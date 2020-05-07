/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.RenameStudyAttributes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
@EnableWebMvc
@ContextHierarchy({
    @ContextConfiguration(classes = {StudyApplication.class, StudyService.class})
    })
public class StudyTest extends AbstractEmbeddedCassandraSetup {

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

    @Mock
    private RestTemplate networkModificationServerRest;

    @MockBean
    private NetworkStoreService networkStoreClient;

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String STUDY_NAME = "studyName";
    private static final String NETWORK_UUID = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID = "11111111-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private final UUID networkUuid = UUID.fromString(NETWORK_UUID);
    private final UUID caseUuid = UUID.fromString(CASE_UUID);
    private final UUID importedCaseUuid = UUID.fromString(IMPORTED_CASE_UUID);
    private final NetworkInfos networkInfos = new NetworkInfos(networkUuid, "20140116_0830_2D4_UX1_pst");

    @Before
    public void setup() {
        studyService.setCaseServerRest(caseServerRest);
        studyService.setNetworkConversionServerRest(networkConversionServerRest);
        studyService.setSingleLineDiagramServerRest(singleLineDiagramServerRest);
        studyService.setGeoDataServerRest(geoDataServerRest);
        studyService.setNetworkMapServerRest(networkMapServerRest);
        studyService.setNetworkModificationServerRest(networkModificationServerRest);

        given(caseServerRest.exchange(
                eq("/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/" + IMPORTED_CASE_UUID + "/format"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("XIIDM", HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/exists"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class))).willReturn(new ResponseEntity<>(true, HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class))).willReturn(new ResponseEntity<>(false, HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/cases/private"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(UUID.class))).willReturn(new ResponseEntity<>(importedCaseUuid, HttpStatus.OK));

        List<CaseInfos> caseList = new ArrayList<>();
        caseList.add(new CaseInfos("case1", "XIIDM", UUID.fromString("2b72f3ac-031e-412b-b9e6-0ba7d588dc9d")));
        caseList.add(new CaseInfos("case2", "XIIDM", UUID.fromString("2925b172-1dc0-40bc-9d1a-1243cf196082")));

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/cases"),
                eq(HttpMethod.GET),
                eq(null),
                eq(new ParameterizedTypeReference<List<CaseInfos>>() { }))).willReturn(new ResponseEntity<>(caseList, HttpStatus.OK));

        given(networkConversionServerRest.exchange(
                eq("/v1/networks?caseUuid=" + CASE_UUID),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkInfos.class))).willReturn(new ResponseEntity<>(networkInfos, HttpStatus.OK));

        given(networkConversionServerRest.exchange(
                eq("/v1/networks?caseUuid=" + IMPORTED_CASE_UUID),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkInfos.class))).willReturn(new ResponseEntity<>(networkInfos, HttpStatus.OK));

        given(networkConversionServerRest.exchange(
                eq("/v1/networks?caseName=" + IMPORTED_CASE_UUID),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkInfos.class))).willReturn(new ResponseEntity<>(networkInfos, HttpStatus.OK));

        given(singleLineDiagramServerRest.exchange(
                eq("/v1/svg/" + networkUuid + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class))).willReturn(new ResponseEntity<>("byte".getBytes(), HttpStatus.OK));

        given(singleLineDiagramServerRest.exchange(
                eq("/v1/svg-and-metadata/" + networkUuid + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false"),
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

        given(networkModificationServerRest.exchange(
                eq("/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/switches/switchId?open=true"),
                eq(HttpMethod.PUT),
                eq(HttpEntity.EMPTY),
                eq(Void.class))).willReturn(new ResponseEntity<Void>(HttpStatus.OK));
    }

    @Test
    public void test() throws Exception {
        MockWebServer server = new MockWebServer();
        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseUrl = server.url("");

        studyService.setCaseServerBaseUri(baseUrl.toString().substring(0, baseUrl.toString().length() - 1));

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch (RecordedRequest request) throws InterruptedException {
                switch (Objects.requireNonNull(request.getPath())) {
                    case "/v1/studies/{studyName}/cases/{caseUuid}":
                        return new MockResponse().setResponseCode(200).setBody("CGMES");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/exists":
                        return new MockResponse().setResponseCode(200).setBody("true");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format":
                        return new MockResponse().setResponseCode(200).setBody("UCTE");

                    case "/v1/cases/" + IMPORTED_CASE_UUID + "/format":
                        return new MockResponse().setResponseCode(200).setBody("XIIDM");

                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("false");

                    case "/" + CASE_API_VERSION + "/cases/private":
                        return new MockResponse().setResponseCode(200).setBody(importedCaseUuid.toString());
                    case "/" + CASE_API_VERSION + "v1/cases/11111111-0000-0000-0000-000000000000":
                        return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(404);
            }
        };
        server.setDispatcher(dispatcher);

        //empty
        MvcResult result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[]", result.getResponse().getContentAsString());

        //insert a study
        mvc.perform(post("/v1/studies/{studyName}/cases/{caseUuid}", STUDY_NAME, caseUuid)
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isOk());

        //insert a study with a non existing case and except exception
        result = mvc.perform(post("/v1/studies/{studyName}/cases/{caseUuid}", "randomStudy", "00000000-0000-0000-0000-000000000000")
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isNotFound())
                .andReturn();

        assertEquals(CASE_DOESNT_EXISTS, result.getResponse().getErrorMessage());

        //1 study
        result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[{\"studyName\":\"studyName\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]",
                result.getResponse().getContentAsString());

        //insert the same study => 409 conflict
        result = mvc.perform(post("/v1/studies/{studyName}/cases/{caseUuid}", STUDY_NAME, caseUuid)
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
        assertEquals("{\"name\":\"s2\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"desc\",\"caseFormat\":\"XIIDM\",\"caseUuid\":\"11111111-0000-0000-0000-000000000000\",\"casePrivate\":true}",
                result.getResponse().getContentAsString());

        //get a non existing study -> 404 not found
        mvc.perform(get(STUDIES_URL, "s3"))
                .andExpect(status().isNotFound())
                .andReturn();

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

        mvc.perform(put("/v1/studies/{studyName}/network-modification/switches/{switchId}?open=true", STUDY_NAME, "switchId"))
                .andExpect(status().isOk());

        //insert 1 study
        result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("[{\"studyName\":\"studyName\",\"description\":\"description\",\"caseFormat\":\"UCTE\"}]",
                result.getResponse().getContentAsString());

        //rename the study
        ObjectMapper objMapper = new ObjectMapper();
        String newStudyName = "newName";
        RenameStudyAttributes renameStudyAttributes = new RenameStudyAttributes(newStudyName);
        result = mvc.perform(post("/v1/studies/" + STUDY_NAME + "/rename")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objMapper.writeValueAsString(renameStudyAttributes)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        assertEquals("{\"name\":\"newName\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"description\":\"description\",\"caseFormat\":\"UCTE\",\"caseUuid\":\"00000000-8cf0-11bd-b23e-10b96e4ef00d\",\"casePrivate\":false}",
                result.getResponse().getContentAsString());

        result = mvc.perform(post("/v1/studies/aaa/rename")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objMapper.writeValueAsString(renameStudyAttributes)))
                .andExpect(status().isNotFound())
                .andReturn();
        assertEquals(STUDY_DOESNT_EXISTS, result.getResponse().getContentAsString());

        // Shut down the server. Instances cannot be reused.
        server.shutdown();
    }
}
