/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.nosan.embedded.cassandra.EmbeddedCassandraFactory;
import com.github.nosan.embedded.cassandra.api.CassandraFactory;
import com.github.nosan.embedded.cassandra.api.Version;
import com.github.nosan.embedded.cassandra.artifact.DefaultArtifact;
import com.github.nosan.embedded.cassandra.spring.test.EmbeddedCassandra;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@RunWith(SpringRunner.class)
@WebMvcTest(StudyController.class)
@EmbeddedCassandra(scripts = {"classpath:create_keyspace.cql", "classpath:study.cql"})
@EnableWebMvc
@ContextConfiguration(classes = {StudyApplication.class, StudyService.class, CassandraConfig.class})
@DirtiesContext
public class StudyTest {

    @Configuration
    static class TestConfig {

        @Bean
        CassandraFactory cassandraFactory() throws UnknownHostException {
            EmbeddedCassandraFactory cassandraFactory = new EmbeddedCassandraFactory();
            Version version = Version.of("4.0-alpha3");
            Path directory = Paths.get(System.getProperty("user.home") + "/apache-cassandra-4.0-alpha3");
            if (!Files.isDirectory(directory)) {
                throw new IllegalStateException("directory : " + directory + " doesn't exist. You must install a cassandra in your home directory to run the integrations tests");
            }
            cassandraFactory.setArtifact(new DefaultArtifact(version, directory));
            cassandraFactory.setPort(9142);
            cassandraFactory.setJmxLocalPort(0);
            cassandraFactory.setRpcPort(0);
            cassandraFactory.setStoragePort(16432);
            cassandraFactory.setAddress(InetAddress.getByName("localhost"));
            return cassandraFactory;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StudyService studyService;

    @Mock
    private RestTemplate caseServerRest;

    @Mock
    private RestTemplate networkConversionServerRest;

    @Mock
    private RestTemplate singleLineDiagramServerRest;

    @Mock
    private RestTemplate geoDataServerRest;

    @Mock
    private RestTemplate networkMapServerRest;

    @MockBean
    private NetworkStoreService networkStoreClient;

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String TEST_UUID = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private final UUID networkUuid = UUID.fromString(TEST_UUID);
    private final NetworkInfos networkInfos = new NetworkInfos(networkUuid, "20140116_0830_2D4_UX1_pst");

    @Before
    public void setup() {
        studyService.setCaseServerRest(caseServerRest);
        studyService.setNetworkConversionServerRest(networkConversionServerRest);
        studyService.setSingleLineDiagramServerRest(singleLineDiagramServerRest);
        studyService.setGeoDataServerRest(geoDataServerRest);
        studyService.setNetworkMapServerRest(networkMapServerRest);

        given(caseServerRest.exchange(
                eq("/v1/cases/caseName/format"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/testCase.xiidm/format"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("XIIDM", HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/caseName/exists"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class))).willReturn(new ResponseEntity<>(true, HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/notExistingCase/exists"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class))).willReturn(new ResponseEntity<>(false, HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/cases"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        List<CaseInfos> caseList = new ArrayList<>();
        caseList.add(new CaseInfos("case1", "XIIDM"));
        caseList.add(new CaseInfos("case2", "XIIDM"));

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/cases"),
                eq(HttpMethod.GET),
                eq(null),
                eq(new ParameterizedTypeReference<List<CaseInfos>>() { }))).willReturn(new ResponseEntity<>(caseList, HttpStatus.OK));

        given(networkConversionServerRest.exchange(
                eq("/v1/networks?caseName=caseName"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkInfos.class))).willReturn(new ResponseEntity<>(networkInfos, HttpStatus.OK));

        given(networkConversionServerRest.exchange(
                eq("/v1/networks?caseName=testCase.xiidm"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkInfos.class))).willReturn(new ResponseEntity<>(networkInfos, HttpStatus.OK));

        given(singleLineDiagramServerRest.exchange(
                eq("/v1/svg/" + networkUuid + "/voltageLevelId?useName=false"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class))).willReturn(new ResponseEntity<>("byte".getBytes(), HttpStatus.OK));

        given(singleLineDiagramServerRest.exchange(
                eq("/v1/svg-and-metadata/" + networkUuid + "/voltageLevelId?useName=false"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("svgandmetadata", HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("/v1/lines?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("/v1/substations?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        given(networkMapServerRest.exchange(
                eq("/v1/lines/38400000-8cf0-11bd-b23e-10b96e4ef00d"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        given(networkMapServerRest.exchange(
                eq("/v1/substations/38400000-8cf0-11bd-b23e-10b96e4ef00d"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = Importers.importData("XIIDM", dataSource, null);
        given(networkStoreClient.getNetwork(networkUuid)).willReturn(network);
    }

    @Test
    public void test() throws Exception {
        //empty
        MvcResult result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[]", result.getResponse().getContentAsString());

        //insert a study
        mvc.perform(post("/v1/studies/{studyName}/cases/{caseName}", STUDY_NAME, "caseName")
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isOk());

        //insert a study with a non existing case and except exception
        result = mvc.perform(post("/v1/studies/{studyName}/cases/{caseName}", "randomStudy", "notExistingCase")
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(CASE_DOESNT_EXISTS, result.getResponse().getErrorMessage());

        //1 study
        result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[{\"studyName\":\"studyName\",\"description\":\"description\",\"caseFormat\":\"\"}]",
                result.getResponse().getContentAsString());

        //insert the same study => 409 conflict
        result = mvc.perform(post("/v1/studies/{studyName}/cases/{caseName}", STUDY_NAME, "caseName")
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isConflict())
                .andReturn();
        assertEquals(StudyConstants.STUDY_ALREADY_EXISTS, result.getResponse().getErrorMessage());

        //insert a study with a case (multipartfile)
        try (InputStream inputStream = getClass().getResourceAsStream("/src/test/resources/testCase.xiidm")) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/plain", inputStream);
            MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.multipart(STUDIES_URL, "s2")
                    .file(mockFile)
                    .param(DESCRIPTION, "desc"))
                    .andExpect(status().isOk())
                    .andReturn();
        }

        //Import the same case -> 409 conflict
        try (InputStream inputStream = getClass().getResourceAsStream("/src/test/resources/testCase.xiidm")) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", TEST_FILE, "text/plain", inputStream);
            mvc.perform(MockMvcRequestBuilders.multipart(STUDIES_URL, "s2")
                    .file(mockFile)
                    .param(DESCRIPTION, DESCRIPTION))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertEquals(StudyConstants.STUDY_ALREADY_EXISTS, result.getResponse().getErrorMessage());
        }

        result = mvc.perform(get(STUDIES_URL, "s2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("{\"name\":\"s2\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"networkCase\":\"testCase.xiidm\",\"description\":\"desc\",\"caseFormat\":\"XIIDM\"}",
                result.getResponse().getContentAsString());

        //get a non existing study -> 404 not found
        mvc.perform(get(STUDIES_URL, "s3"))
                .andExpect(status().isNotFound())
                .andReturn();

        //get the case lists
        result = mvc.perform(get("/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[{\"name\":\"case1\",\"format\":\"XIIDM\"},{\"name\":\"case2\",\"format\":\"XIIDM\"}]", result.getResponse().getContentAsString());

        //get the voltage level diagram svg
        result = mvc.perform(get("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg?useName=false", STUDY_NAME, "voltageLevelId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andReturn();
        assertEquals("byte", result.getResponse().getContentAsString());

        //get the voltage level diagram svg from a study that doesn't exist

        result = mvc.perform(get("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg", "notExistingStudy", "voltageLevelId"))
                .andExpect(status().isNotFound())
                .andReturn();

        //get the voltage level diagram svg and metadata
        result = mvc.perform(get("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", STUDY_NAME, "voltageLevelId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("svgandmetadata", result.getResponse().getContentAsString());

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        result = mvc.perform(get("/v1/studies/{studyName}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", "notExistingStudy", "voltageLevelId"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(STUDY_DOESNT_EXISTS, result.getResponse().getContentAsString());

        //get all the voltage levels of the network
        result = mvc.perform(get("/v1/studies/{studyName}/network/voltage-levels", STUDY_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals(
                "[{\"id\":\"BBE1AA1\",\"name\":\"BBE1AA1\",\"substationId\":\"BBE1AA\"}," +
                        "{\"id\":\"BBE2AA1\",\"name\":\"BBE2AA1\",\"substationId\":\"BBE2AA\"}," +
                        "{\"id\":\"DDE1AA1\",\"name\":\"DDE1AA1\",\"substationId\":\"DDE1AA\"}," +
                        "{\"id\":\"DDE2AA1\",\"name\":\"DDE2AA1\",\"substationId\":\"DDE2AA\"}," +
                        "{\"id\":\"DDE3AA1\",\"name\":\"DDE3AA1\",\"substationId\":\"DDE3AA\"}," +
                        "{\"id\":\"FFR1AA1\",\"name\":\"FFR1AA1\",\"substationId\":\"FFR1AA\"}," +
                        "{\"id\":\"FFR3AA1\",\"name\":\"FFR3AA1\",\"substationId\":\"FFR3AA\"}," +
                        "{\"id\":\"NNL1AA1\",\"name\":\"NNL1AA1\",\"substationId\":\"NNL1AA\"}," +
                        "{\"id\":\"NNL2AA1\",\"name\":\"NNL2AA1\",\"substationId\":\"NNL2AA\"}," +
                        "{\"id\":\"NNL3AA1\",\"name\":\"NNL3AA1\",\"substationId\":\"NNL3AA\"}]",
                result.getResponse().getContentAsString());

        //get the lines-graphics of a network
        mvc.perform(get("/v1/studies/{studyName}/geo-data/lines/", STUDY_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        //get the substation-graphics of a network
        mvc.perform(get("/v1/studies/{studyName}/geo-data/substations/", STUDY_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        //get the lines map data of a network
        mvc.perform(get("/v1/studies/{studyName}/network-map/lines/", STUDY_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        //get the substation map data of a network
        mvc.perform(get("/v1/studies/{studyName}/network-map/substations/", STUDY_NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        //delete existing study s2
        mvc.perform(delete(STUDIES_URL, "s2"))
                .andExpect(status().isOk())
                .andReturn();

    }
}
