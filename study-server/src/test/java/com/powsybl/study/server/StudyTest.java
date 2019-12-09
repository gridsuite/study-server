package com.powsybl.study.server;

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

import java.io.ByteArrayInputStream;
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
@CassandraDataSet(value = "pgs.cql", keyspace = "pgs")
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

    private final UUID networkUuid = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");
    private final NetworkIds networkIds = new NetworkIds(networkUuid, "20140116_0830_2D4_UX1_pst");
    private final String substationGraphicsString = "[{\"id\":\"id\",\"position\":{\"lat\":0.0,\"lon\":0.0}}]";
    private final String lineGraphicsString = "[{\"id\":\"id\"," +
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
                eq(String.class))).willReturn(new ResponseEntity<>(lineGraphicsString, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/lines-graphics-with-pagination/" + networkUuid + "?page=1&size=1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(lineGraphicsString, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/substations-graphics/" + networkUuid),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(substationGraphicsString, HttpStatus.OK));

        given(geoDataServerRest.exchange(
                eq("http://localhost:8087/v1/substations-graphics-with-pagination/" + networkUuid + "?page=1&size=1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class))).willReturn(new ResponseEntity<>(substationGraphicsString, HttpStatus.OK));

        InputStream inputStream = new ByteArrayInputStream(getNetworkAsByte());
        Network network = Importers.loadNetwork("test.xiidm", inputStream);
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
                .param("description", "description"))
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
                .param("description", "description"))
                .andExpect(status().isConflict())
                .andReturn();
        assertEquals(StudyConstants.STUDY_ALREADY_EXISTS, result.getResponse().getErrorMessage());

        //insert a study with a case (multipartfile)
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("testCase.xiidm")) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", "testCase.xiidm", "text/plain", inputStream);
            MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.multipart("/v1/studies/{studyName}", "s2")
                    .file(mockFile)
                    .param("description", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                    .andReturn();
            assertEquals("{\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\"}", mvcResult.getResponse().getContentAsString());
        }

        //Import the same case -> 409 conflict
        classLoader = getClass().getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream("testCase.xiidm")) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", "testCase.xiidm", "text/plain", inputStream);
            MvcResult mvcResult = mvc.perform(MockMvcRequestBuilders.multipart("/v1/studies/{studyName}", "s2")
                    .file(mockFile)
                    .param("description", "desc"))
                    .andExpect(status().isConflict())
                    .andReturn();
            assertEquals(StudyConstants.STUDY_ALREADY_EXISTS, result.getResponse().getErrorMessage());
        }

        result = mvc.perform(get("/v1/studies/{studyName}", "s2"))
                 .andExpect(status().isOk())
                 .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                 .andReturn();
        assertEquals("{\"name\":\"s2\",\"networkUuid\":\"38400000-8cf0-11bd-b23e-10b96e4ef00d\",\"networkId\":\"20140116_0830_2D4_UX1_pst\",\"networkCase\":\"testCase.xiidm\",\"description\":\"desc\"}",
                result.getResponse().getContentAsString());

        //get a non existing study -> 404 not found
        mvc.perform(get("/v1/studies/{studyName}", "s3"))
                .andExpect(status().isNotFound())
                .andReturn();

        //get the case lists
        result = mvc.perform(get("/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals("{\"case1Path\":\"case1\",\"case2Path\":\"case2\",\"case3Path\":\"case3\"}", result.getResponse().getContentAsString());

        //get the voltage level diagram svg
        result = mvc.perform(get("/v1/svg/{networkUuid}/{voltageLevelId}", "38400000-8cf0-11bd-b23e-10b96e4ef00d", "voltageLevelId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andReturn();
        assertEquals("byte", result.getResponse().getContentAsString());

        //get all the voltage levels of the network
        result = mvc.perform(get("/v1/networks/{networkUuid}/voltage-levels", "38400000-8cf0-11bd-b23e-10b96e4ef00d"))
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
        result = mvc.perform(get("/v1/lines-graphics/{networkUuid}/", "38400000-8cf0-11bd-b23e-10b96e4ef00d"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(lineGraphicsString, result.getResponse().getContentAsString());

        //get the lines-graphics of a network paginated
        result = mvc.perform(get("/v1/lines-graphics-with-pagination/{networkUuid}", "38400000-8cf0-11bd-b23e-10b96e4ef00d")
                .param("page", "1")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(lineGraphicsString, result.getResponse().getContentAsString());

        //get the substation-graphics of a network
        result = mvc.perform(get("/v1/substations-graphics/{networkUuid}", "38400000-8cf0-11bd-b23e-10b96e4ef00d"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(substationGraphicsString, result.getResponse().getContentAsString());

        //get the substation-graphics of a network paginated
        result = mvc.perform(get("/v1/substations-graphics-with-pagination/{networkUuid}", "38400000-8cf0-11bd-b23e-10b96e4ef00d")
                .param("page", "1")
                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8))
                .andReturn();
        assertEquals(substationGraphicsString, result.getResponse().getContentAsString());

        //delete existing study s2
        mvc.perform(delete("/v1/studies/{studyName}", "s2"))
                .andExpect(status().isOk())
                .andReturn();

    }

    public byte[] getNetworkAsByte() {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<iidm:network xmlns:iidm=\"http://www.itesla_project.eu/schema/iidm/1_0\" id=\"20140116_0830_2D4_UX1_pst\" caseDate=\"2014-01-16T08:30:00.000+01:00\" forecastDistance=\"0\" sourceFormat=\"UCTE\">\n" +
                "    <iidm:substation id=\"BBE1AA\" country=\"BE\">\n" +
                "        <iidm:voltageLevel id=\"BBE1AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"BBE1AA1 \" v=\"380.0\" angle=\"-2.353799343109131\"/>\n" +
                "                <iidm:bus id=\"BBE3AA1 \" v=\"380.0\" angle=\"0.0\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"BBE1AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"1500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"BBE1AA1 \" connectableBus=\"BBE1AA1 \" p=\"-1500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:generator id=\"BBE3AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"BBE3AA1 \" connectableBus=\"BBE3AA1 \" p=\"-2500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"BBE1AA1 _load\" loadType=\"UNDEFINED\" p0=\"3000.0\" q0=\"0.0\" bus=\"BBE1AA1 \" connectableBus=\"BBE1AA1 \" p=\"3000.0\"/>\n" +
                "            <iidm:load id=\"BBE3AA1 _load\" loadType=\"UNDEFINED\" p0=\"1500.0\" q0=\"0.0\" bus=\"BBE3AA1 \" connectableBus=\"BBE3AA1 \" p=\"1500.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "        <iidm:twoWindingsTransformer id=\"BBE1AA1  BBE3AA1  2\" r=\"0.20800000429153442\" x=\"25.5\" g=\"1.0625000186337274E-6\" b=\"-2.1624998680636054E-6\" ratedU1=\"400.0\" ratedU2=\"400.0\" bus1=\"BBE3AA1 \" connectableBus1=\"BBE3AA1 \" voltageLevelId1=\"BBE1AA1\" bus2=\"BBE1AA1 \" connectableBus2=\"BBE1AA1 \" voltageLevelId2=\"BBE1AA1\" p1=\"-227.49346923828125\" p2=\"227.49346923828125\">\n" +
                "            <iidm:phaseTapChanger lowTapPosition=\"-33\" tapPosition=\"5\" regulationMode=\"FIXED_TAP\">\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"30.036436080932617\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"29.165605545043945\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"28.29132080078125\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"27.413660049438477\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"26.53270721435547\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"25.648548126220703\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"24.761274337768555\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"23.870973587036133\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"22.977739334106445\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"22.0816650390625\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"21.182849884033203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"20.281387329101562\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"19.377382278442383\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"18.470935821533203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"17.56215476989746\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"16.651138305664062\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"15.737995147705078\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"14.82283878326416\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"13.905777931213379\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"12.986922264099121\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"12.066386222839355\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"11.144285202026367\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"10.220732688903809\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"9.295848846435547\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"8.369750022888184\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"7.442552089691162\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"6.514379024505615\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"5.585349082946777\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"4.655583381652832\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"3.7252047061920166\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"2.7943341732025146\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"1.8630945682525635\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"0.9316088557243347\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-0.0\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-0.9316088557243347\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-1.8630945682525635\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-2.7943341732025146\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-3.7252047061920166\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-4.655583381652832\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-5.585349082946777\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-6.514379024505615\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-7.442552089691162\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-8.369750022888184\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-9.295848846435547\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-10.220732688903809\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-11.144285202026367\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-12.066386222839355\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-12.986922264099121\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-13.905777931213379\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-14.82283878326416\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-15.737995147705078\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-16.651138305664062\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-17.56215476989746\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-18.470935821533203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-19.377382278442383\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-20.281387329101562\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-21.182849884033203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-22.0816650390625\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-22.977739334106445\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-23.870973587036133\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-24.761274337768555\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-25.648548126220703\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-26.53270721435547\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-27.413660049438477\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-28.29132080078125\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-29.165605545043945\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-30.036436080932617\"/>\n" +
                "            </iidm:phaseTapChanger>\n" +
                "            <iidm:currentLimits2 permanentLimit=\"1227.0\"/>\n" +
                "        </iidm:twoWindingsTransformer>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"BBE2AA\" country=\"BE\">\n" +
                "        <iidm:voltageLevel id=\"BBE2AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"BBE2AA1 \" v=\"380.0\" angle=\"2.1468396186828613\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"BBE2AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"3000.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"BBE2AA1 \" connectableBus=\"BBE2AA1 \" p=\"-3000.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"BBE2AA1 _load\" loadType=\"UNDEFINED\" p0=\"1000.0\" q0=\"0.0\" bus=\"BBE2AA1 \" connectableBus=\"BBE2AA1 \" p=\"1000.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"DDE1AA\" country=\"DE\">\n" +
                "        <iidm:voltageLevel id=\"DDE1AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"DDE1AA1 \" v=\"380.0\" angle=\"-13.329349517822266\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"DDE1AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"DDE1AA1 \" connectableBus=\"DDE1AA1 \" p=\"-2500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"DDE1AA1 _load\" loadType=\"UNDEFINED\" p0=\"3500.0\" q0=\"0.0\" bus=\"DDE1AA1 \" connectableBus=\"DDE1AA1 \" p=\"3500.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"DDE2AA\" country=\"DE\">\n" +
                "        <iidm:voltageLevel id=\"DDE2AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"DDE2AA1 \" v=\"380.0\" angle=\"-11.774831771850586\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"DDE2AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2000.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"DDE2AA1 \" connectableBus=\"DDE2AA1 \" p=\"-2000.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"DDE2AA1 _load\" loadType=\"UNDEFINED\" p0=\"3000.0\" q0=\"0.0\" bus=\"DDE2AA1 \" connectableBus=\"DDE2AA1 \" p=\"3000.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"DDE3AA\" country=\"DE\">\n" +
                "        <iidm:voltageLevel id=\"DDE3AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"DDE3AA1 \" v=\"380.0\" angle=\"-10.916015625\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"DDE3AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"1500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"DDE3AA1 \" connectableBus=\"DDE3AA1 \" p=\"-1500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"DDE3AA1 _load\" loadType=\"UNDEFINED\" p0=\"2000.0\" q0=\"0.0\" bus=\"DDE3AA1 \" connectableBus=\"DDE3AA1 \" p=\"2000.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"FFR1AA\" country=\"FR\">\n" +
                "        <iidm:voltageLevel id=\"FFR1AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"FFR1AA1 \" v=\"380.0\" angle=\"0.13716478645801544\"/>\n" +
                "                <iidm:bus id=\"FFR2AA1 \" v=\"380.0\" angle=\"-5.659938335418701\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"FFR1AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2000.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"FFR1AA1 \" connectableBus=\"FFR1AA1 \" p=\"-2000.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:generator id=\"FFR2AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2000.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"FFR2AA1 \" connectableBus=\"FFR2AA1 \" p=\"-2000.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"FFR1AA1 _load\" loadType=\"UNDEFINED\" p0=\"1000.0\" q0=\"0.0\" bus=\"FFR1AA1 \" connectableBus=\"FFR1AA1 \" p=\"1000.0\"/>\n" +
                "            <iidm:load id=\"FFR2AA1 _load\" loadType=\"UNDEFINED\" p0=\"3500.0\" q0=\"0.0\" bus=\"FFR2AA1 \" connectableBus=\"FFR2AA1 \" p=\"3500.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "        <iidm:twoWindingsTransformer id=\"FFR1AA1  FFR2AA1  2\" r=\"0.08669999986886978\" x=\"13.98799991607666\" g=\"1.2216000868647825E-6\" b=\"-2.3275001694855746E-6\" ratedU1=\"400.0\" ratedU2=\"400.0\" bus1=\"FFR2AA1 \" connectableBus1=\"FFR2AA1 \" voltageLevelId1=\"FFR1AA1\" bus2=\"FFR1AA1 \" connectableBus2=\"FFR1AA1 \" voltageLevelId2=\"FFR1AA1\" p1=\"279.1942443847656\" p2=\"-279.1942443847656\">\n" +
                "            <iidm:phaseTapChanger lowTapPosition=\"-17\" tapPosition=\"-5\" regulationMode=\"FIXED_TAP\">\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"24.626773834228516\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"23.21863555908203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"21.803354263305664\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"20.38130760192871\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"18.95288848876953\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"17.51850128173828\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"16.07855987548828\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"14.633487701416016\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"13.183723449707031\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"11.729705810546875\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"10.27188777923584\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"8.81072998046875\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"7.346695423126221\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"5.880255699157715\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"4.411885738372803\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"2.9420645236968994\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"1.471274733543396\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-0.0\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-1.471274733543396\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-2.9420645236968994\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-4.411885738372803\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-5.880255699157715\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-7.346695423126221\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-8.81072998046875\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-10.27188777923584\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-11.729705810546875\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-13.183723449707031\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-14.633487701416016\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-16.07855987548828\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-17.51850128173828\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-18.95288848876953\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-20.38130760192871\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-21.803354263305664\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-23.21863555908203\"/>\n" +
                "                <iidm:step r=\"0.0\" x=\"0.0\" g=\"0.0\" b=\"0.0\" rho=\"1.0\" alpha=\"-24.626773834228516\"/>\n" +
                "            </iidm:phaseTapChanger>\n" +
                "            <iidm:currentLimits2 permanentLimit=\"2329.0\"/>\n" +
                "        </iidm:twoWindingsTransformer>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"FFR3AA\" country=\"FR\">\n" +
                "        <iidm:voltageLevel id=\"FFR3AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"FFR3AA1 \" v=\"380.0\" angle=\"0.8586146235466003\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"FFR3AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"3000.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"FFR3AA1 \" connectableBus=\"FFR3AA1 \" p=\"-3000.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"FFR3AA1 _load\" loadType=\"UNDEFINED\" p0=\"1500.0\" q0=\"0.0\" bus=\"FFR3AA1 \" connectableBus=\"FFR3AA1 \" p=\"1500.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"NNL1AA\" country=\"NL\">\n" +
                "        <iidm:voltageLevel id=\"NNL1AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"NNL1AA1 \" v=\"380.0\" angle=\"-4.895452976226807\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"NNL1AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"1500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"NNL1AA1 \" connectableBus=\"NNL1AA1 \" p=\"-1500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"NNL1AA1 _load\" loadType=\"UNDEFINED\" p0=\"1000.0\" q0=\"0.0\" bus=\"NNL1AA1 \" connectableBus=\"NNL1AA1 \" p=\"1000.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"NNL2AA\" country=\"NL\">\n" +
                "        <iidm:voltageLevel id=\"NNL2AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"NNL2AA1 \" v=\"380.0\" angle=\"-4.663552761077881\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"NNL2AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"NNL2AA1 \" connectableBus=\"NNL2AA1 \" p=\"-500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"NNL2AA1 _load\" loadType=\"UNDEFINED\" p0=\"1000.0\" q0=\"0.0\" bus=\"NNL2AA1 \" connectableBus=\"NNL2AA1 \" p=\"1000.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:substation id=\"NNL3AA\" country=\"NL\">\n" +
                "        <iidm:voltageLevel id=\"NNL3AA1\" nominalV=\"380.0\" topologyKind=\"BUS_BREAKER\">\n" +
                "            <iidm:busBreakerTopology>\n" +
                "                <iidm:bus id=\"NNL3AA1 \" v=\"380.0\" angle=\"-7.111279010772705\"/>\n" +
                "            </iidm:busBreakerTopology>\n" +
                "            <iidm:generator id=\"NNL3AA1 _generator\" energySource=\"OTHER\" minP=\"-0.0\" maxP=\"9000.0\" voltageRegulatorOn=\"true\" targetP=\"2500.0\" targetV=\"400.0\" targetQ=\"-0.0\" bus=\"NNL3AA1 \" connectableBus=\"NNL3AA1 \" p=\"-2500.0\">\n" +
                "                <iidm:minMaxReactiveLimits minQ=\"-9000.0\" maxQ=\"9000.0\"/>\n" +
                "            </iidm:generator>\n" +
                "            <iidm:load id=\"NNL3AA1 _load\" loadType=\"UNDEFINED\" p0=\"2500.0\" q0=\"0.0\" bus=\"NNL3AA1 \" connectableBus=\"NNL3AA1 \" p=\"2500.0\"/>\n" +
                "        </iidm:voltageLevel>\n" +
                "    </iidm:substation>\n" +
                "    <iidm:line id=\"BBE1AA1  BBE2AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"BBE1AA1 \" connectableBus1=\"BBE1AA1 \" voltageLevelId1=\"BBE1AA1\" bus2=\"BBE2AA1 \" connectableBus2=\"BBE2AA1 \" voltageLevelId2=\"BBE2AA1\" p1=\"-1134.2760009765625\" p2=\"1134.2760009765625\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"BBE1AA1  BBE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"BBE1AA1 \" connectableBus1=\"BBE1AA1 \" voltageLevelId1=\"BBE1AA1\" bus2=\"BBE3AA1 \" connectableBus2=\"BBE3AA1 \" voltageLevelId2=\"BBE1AA1\" p1=\"-593.217529296875\" p2=\"593.217529296875\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"BBE2AA1  BBE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"BBE2AA1 \" connectableBus1=\"BBE2AA1 \" voltageLevelId1=\"BBE2AA1\" bus2=\"BBE3AA1 \" connectableBus2=\"BBE3AA1 \" voltageLevelId2=\"BBE1AA1\" p1=\"541.0584106445312\" p2=\"-541.0584106445312\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"FFR1AA1  FFR2AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"FFR1AA1 \" connectableBus1=\"FFR1AA1 \" voltageLevelId1=\"FFR1AA1\" bus2=\"FFR2AA1 \" connectableBus2=\"FFR2AA1 \" voltageLevelId2=\"FFR1AA1\" p1=\"1461.01806640625\" p2=\"-1461.01806640625\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"FFR1AA1  FFR3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"FFR1AA1 \" connectableBus1=\"FFR1AA1 \" voltageLevelId1=\"FFR1AA1\" bus2=\"FFR3AA1 \" connectableBus2=\"FFR3AA1 \" voltageLevelId2=\"FFR3AA1\" p1=\"-181.82379150390625\" p2=\"181.82379150390625\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"FFR2AA1  FFR3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"FFR2AA1 \" connectableBus1=\"FFR2AA1 \" voltageLevelId1=\"FFR1AA1\" bus2=\"FFR3AA1 \" connectableBus2=\"FFR3AA1 \" voltageLevelId2=\"FFR3AA1\" p1=\"-1642.841796875\" p2=\"1642.841796875\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"DDE1AA1  DDE2AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"DDE1AA1 \" connectableBus1=\"DDE1AA1 \" voltageLevelId1=\"DDE1AA1\" bus2=\"DDE2AA1 \" connectableBus2=\"DDE2AA1 \" voltageLevelId2=\"DDE2AA1\" p1=\"-391.77813720703125\" p2=\"391.77813720703125\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"DDE1AA1  DDE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"DDE1AA1 \" connectableBus1=\"DDE1AA1 \" voltageLevelId1=\"DDE1AA1\" bus2=\"DDE3AA1 \" connectableBus2=\"DDE3AA1 \" voltageLevelId2=\"DDE3AA1\" p1=\"-608.2218627929688\" p2=\"608.2218627929688\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"DDE2AA1  DDE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"DDE2AA1 \" connectableBus1=\"DDE2AA1 \" voltageLevelId1=\"DDE2AA1\" bus2=\"DDE3AA1 \" connectableBus2=\"DDE3AA1 \" voltageLevelId2=\"DDE3AA1\" p1=\"-216.44374084472656\" p2=\"216.44374084472656\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"NNL1AA1  NNL2AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"NNL1AA1 \" connectableBus1=\"NNL1AA1 \" voltageLevelId1=\"NNL1AA1\" bus2=\"NNL2AA1 \" connectableBus2=\"NNL2AA1 \" voltageLevelId2=\"NNL2AA1\" p1=\"-58.444793701171875\" p2=\"58.444793701171875\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"NNL1AA1  NNL3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"NNL1AA1 \" connectableBus1=\"NNL1AA1 \" voltageLevelId1=\"NNL1AA1\" bus2=\"NNL3AA1 \" connectableBus2=\"NNL3AA1 \" voltageLevelId2=\"NNL3AA1\" p1=\"558.44482421875\" p2=\"-558.44482421875\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"NNL2AA1  NNL3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"NNL2AA1 \" connectableBus1=\"NNL2AA1 \" voltageLevelId1=\"NNL2AA1\" bus2=\"NNL3AA1 \" connectableBus2=\"NNL3AA1 \" voltageLevelId2=\"NNL3AA1\" p1=\"616.8895874023438\" p2=\"-616.8895874023438\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"FFR2AA1  DDE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"FFR2AA1 \" connectableBus1=\"FFR2AA1 \" voltageLevelId1=\"FFR1AA1\" bus2=\"DDE3AA1 \" connectableBus2=\"DDE3AA1 \" voltageLevelId2=\"DDE3AA1\" p1=\"1324.6656494140625\" p2=\"-1324.6656494140625\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"DDE2AA1  NNL3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"DDE2AA1 \" connectableBus1=\"DDE2AA1 \" voltageLevelId1=\"DDE2AA1\" bus2=\"NNL3AA1 \" connectableBus2=\"NNL3AA1 \" voltageLevelId2=\"NNL3AA1\" p1=\"-1175.3343505859375\" p2=\"1175.3343505859375\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"NNL2AA1  BBE3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"NNL2AA1 \" connectableBus1=\"NNL2AA1 \" voltageLevelId1=\"NNL2AA1\" bus2=\"BBE3AA1 \" connectableBus2=\"BBE3AA1 \" voltageLevelId2=\"BBE1AA1\" p1=\"-1175.3343505859375\" p2=\"1175.3343505859375\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "    <iidm:line id=\"BBE2AA1  FFR3AA1  1\" r=\"0.0\" x=\"10.0\" g1=\"0.0\" b1=\"0.0\" g2=\"0.0\" b2=\"0.0\" bus1=\"BBE2AA1 \" connectableBus1=\"BBE2AA1 \" voltageLevelId1=\"BBE2AA1\" bus2=\"FFR3AA1 \" connectableBus2=\"FFR3AA1 \" voltageLevelId2=\"FFR3AA1\" p1=\"324.6656188964844\" p2=\"-324.6656188964844\">\n" +
                "        <iidm:currentLimits1 permanentLimit=\"5000.0\"/>\n" +
                "        <iidm:currentLimits2 permanentLimit=\"5000.0\"/>\n" +
                "    </iidm:line>\n" +
                "</iidm:network>").getBytes();
    }
}
