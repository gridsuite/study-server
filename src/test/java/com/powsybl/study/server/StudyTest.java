package com.powsybl.study.server;

import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.import_.Importers;
import com.powsybl.iidm.network.Network;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.study.server.dto.NetworkIds;
import org.cassandraunit.spring.CassandraDataSet;
import org.cassandraunit.spring.CassandraUnitDependencyInjectionTestExecutionListener;
import org.cassandraunit.spring.CassandraUnitTestExecutionListener;
import org.cassandraunit.spring.EmbeddedCassandra;
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
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.InputStream;
import java.util.*;

import static com.powsybl.study.server.StudyConstants.CASE_API_VERSION;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.context.TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(StudyController.class)
@EnableWebMvc
@ContextConfiguration(classes = {StudyApplication.class, StudyService.class})
@TestExecutionListeners(listeners = {CassandraUnitDependencyInjectionTestExecutionListener.class,
        CassandraUnitTestExecutionListener.class},
        mergeMode = MERGE_WITH_DEFAULTS)
@CassandraDataSet(value = "study.cql", keyspace = "study")
@EmbeddedCassandra
public class StudyTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StudyService studyService;

    @Mock
    private RestTemplate caseServerRest;

    @Mock
    private RestTemplate iidmConverterServerRest;

    @Mock
    private RestTemplate voltageLevelServerRest;

    @Mock
    private RestTemplate geoDataServerRest;

    @MockBean
    private NetworkStoreService networkStoreClient;

    private static final String STUDIES_URL = "/v1/studies/{studyName}";
    private static final String DESCRIPTION = "description";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String TEST_UUID = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private final UUID networkUuid = UUID.fromString(TEST_UUID);
    private final NetworkIds networkIds = new NetworkIds(networkUuid, "20140116_0830_2D4_UX1_pst");
    private static final String SUBSTATION_GRAPHICS_STRING = "[{\"id\":\"id\",\"position\":{\"lat\":0.0,\"lon\":0.0}}]";
    private static final String LINE_GRAPHICS_STRING = "[{\"id\":\"id\"," +
            "\"drawOrder\":1," +
            "\"voltage\":440," +
            "\"color\":{\"red\":1.0,\"green\":1.0,\"blue\":1.0,\"opacity\":0.0}," +
            "\"aerial\":false," +
            "\"ordered\":false," +
            "\"coordinates\":[]}]";

    public void setup() {
        studyService.setCaseServerRest(caseServerRest);
        studyService.setIidmConverterServerRest(iidmConverterServerRest);
        studyService.setVoltageLevelDiagramServerRest(voltageLevelServerRest);
        studyService.setGeoDataServerRest(geoDataServerRest);

        given(caseServerRest.exchange(
                eq("http://localhost:5000/v1/case-server/exists?caseName=caseName"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class))).willReturn(new ResponseEntity<>(true, HttpStatus.OK));

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/case-server/import-case"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>("", HttpStatus.OK));

        Map<String, String> caseList = new HashMap<>();
        caseList.put("case1Path", "case1");
        caseList.put("case2Path", "case2");
        caseList.put("case3Path", "case3");

        given(caseServerRest.exchange(
                eq("/" + CASE_API_VERSION + "/case-server/cases"),
                eq(HttpMethod.GET),
                eq(null),
                eq(new ParameterizedTypeReference<Map<String, String>>() { }))).willReturn(new ResponseEntity<>(caseList, HttpStatus.OK));

        given(iidmConverterServerRest.exchange(
                eq("http://localhost:5003/v1/iidm-converter-server/persistent-store/caseName"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkIds.class))).willReturn(new ResponseEntity<>(networkIds, HttpStatus.OK));

        given(iidmConverterServerRest.exchange(
                eq("http://localhost:5003/v1/iidm-converter-server/persistent-store/testCase.xiidm"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(NetworkIds.class))).willReturn(new ResponseEntity<>(networkIds, HttpStatus.OK));

        given(voltageLevelServerRest.exchange(
                eq("http://localhost:5005/v1/voltage-level-diagram-server/svg/" + networkUuid + "/voltageLevelId"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(byte[].class))).willReturn(new ResponseEntity<>("byte".getBytes(), HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/lines-graphics/" + networkUuid),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(LINE_GRAPHICS_STRING, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/lines-graphics-with-pagination/" + networkUuid + "?page=1&size=1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(LINE_GRAPHICS_STRING, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/substations-graphics/" + networkUuid),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(SUBSTATION_GRAPHICS_STRING, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/substations-graphics-with-pagination/" + networkUuid + "?page=1&size=1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(SUBSTATION_GRAPHICS_STRING, HttpStatus.OK));

        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = Importers.importData("XIIDM", dataSource, null);
        given(networkStoreClient.getNetwork(networkUuid)).willReturn(network);
    }

    @Test
    public void test() throws Exception {
        setup();
        //empty
        MvcResult result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals("[]", result.getResponse().getContentAsString());

        //insert a study
        result = mvc.perform(post("/v1/studies/{studyName}/{caseName}", "studyName", "caseName")
                .param(DESCRIPTION, DESCRIPTION))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals("{\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}", result.getResponse().getContentAsString());

        //1 study
        result = mvc.perform(get("/v1/studies"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals("[{\"name\":\"studyName\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"networkCase\":\"caseName\",\"description\":\"description\"}]",
                result.getResponse().getContentAsString());

        //insert the same study => 409 conflict
        result = mvc.perform(post("/v1/studies/{studyName}/{caseName}", "studyName", "caseName")
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
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                    .andReturn();
            assertEquals("{\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}", mvcResult.getResponse().getContentAsString());
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
                 .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                 .andReturn();
        assertEquals("{\"name\":\"s2\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"networkCase\":\"testCase.xiidm\",\"description\":\"desc\"}",
                result.getResponse().getContentAsString());

        //get a non existing study -> 404 not found
        mvc.perform(get(STUDIES_URL, "s3"))
                .andExpect(status().isNotFound())
                .andReturn();

        //get the case lists
        result = mvc.perform(get("/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals("{\"case1Path\":\"case1\",\"case2Path\":\"case2\",\"case3Path\":\"case3\"}", result.getResponse().getContentAsString());

        //get the voltage level diagram svg
        result = mvc.perform(get("/v1/svg/{networkUuid}/{voltageLevelId}", TEST_UUID, "voltageLevelId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andReturn();
        assertEquals("byte", result.getResponse().getContentAsString());

        //get all the voltage levels of the network
        result = mvc.perform(get("/v1/networks/{networkUuid}/voltage-levels", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
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
        result = mvc.perform(get("/v1/lines-graphics/{networkUuid}/", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(LINE_GRAPHICS_STRING, result.getResponse().getContentAsString());

        //get the lines-graphics of a network paginated
        result = mvc.perform(get("/v1/lines-graphics-with-pagination/{networkUuid}", TEST_UUID)
                .param("page", "1")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(LINE_GRAPHICS_STRING, result.getResponse().getContentAsString());

        //get the substation-graphics of a network
        result = mvc.perform(get("/v1/substations-graphics/{networkUuid}", TEST_UUID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(SUBSTATION_GRAPHICS_STRING, result.getResponse().getContentAsString());

        //get the substation-graphics of a network paginated
        result = mvc.perform(get("/v1/substations-graphics-with-pagination/{networkUuid}", TEST_UUID)
                .param("page", "1")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(SUBSTATION_GRAPHICS_STRING, result.getResponse().getContentAsString());

        //delete existing study s2
        mvc.perform(delete(STUDIES_URL, "s2"))
                .andExpect(status().isOk())
                .andReturn();

    }

}
