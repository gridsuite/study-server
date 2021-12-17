/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import lombok.SneakyThrows;
import nl.jqno.equalsverifier.EqualsVerifier;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.MatcherLoadFlowInfos;
import org.gridsuite.study.server.utils.MatcherReport;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.ResourceUtils;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gridsuite.study.server.NetworkModificationTreeService.HEADER_INSERT_BEFORE;
import static org.gridsuite.study.server.NetworkModificationTreeService.HEADER_NEW_NODE;
import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.*;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient
@EnableWebFlux
@SpringBootTest(properties = {"spring.data.elasticsearch.enabled=false"})
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, EmbeddedElasticsearch.class, TestChannelBinderConfiguration.class})})
public class StudyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    private static final long TIMEOUT = 1000;
    private static final String STUDIES_URL = "/v1/studies";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String TEST_FILE_WITH_ERRORS = "testCase_with_errors.xiidm";
    private static final String TEST_FILE_IMPORT_ERRORS = "testCase_import_errors.xiidm";
    private static final String TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY = "testCase_import_errors_no_message_in_response_body.xiidm";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_BLOCKING_CASE_UUID_STRING = "22111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_CASE_WITH_ERRORS_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    private static final String NEW_STUDY_CASE_UUID = "11888888-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11111111-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_UUID = UUID.fromString(IMPORTED_CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_WITH_ERRORS_UUID = UUID.fromString(IMPORTED_CASE_WITH_ERRORS_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final String CONTINGENCY_LIST_NAME = "ls";
    private static final String SECURITY_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"preContingencyResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"postContingencyResults\":[{\"contingency\":{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}},{\"contingency\":{\"id\":\"l2\",\"elements\":[{\"id\":\"l2\",\"type\":\"BRANCH\"}]},\"limitViolationsResult\":{\"computationOk\":true,\"limitViolations\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"acceptableDuration\":0,\"limit\":400.0,\"limitReduction\":1.0,\"value\":410.0}],\"actionsTaken\":[]}}]}";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";
    public static final String LOAD_PARAMETERS_JSON = "{\"version\":\"1.5\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":false,\"readSlackBus\":false,\"writeSlackBus\":false,\"dc\":false,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_GENERATION_P_MAX\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\"}";
    public static final String LOAD_PARAMETERS_JSON2 = "{\"version\":\"1.5\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"simulShunt\":true,\"readSlackBus\":false,\"writeSlackBus\":true,\"dc\":true,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_CONFORM_LOAD\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\"}";
    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");
    private static final String VOLTAGE_LEVEL_ID = "VOLTAGE_LEVEL_ID";
    private static final String VARIANT_ID = "variant_1";

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private StudyService studyService;

    @Autowired
    private NetworkStoreService networkStoreService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    @MockBean
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private StudyInfosService studyInfosService;

    @Autowired
    private ObjectMapper mapper;

    private List<EquipmentInfos> linesInfosInitialVariant;

    private List<EquipmentInfos> linesInfosDeltaVariant;

    private List<CreatedStudyBasicInfos> studiesInfos;

    private MockWebServer server;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    //used by testGetStudyCreationRequests to control asynchronous case import
    CountDownLatch countDownLatch;

    private static EquipmentInfos toEquipmentInfos(Line line) {
        return EquipmentInfos.builder()
            .networkUuid(NETWORK_UUID)
            .variantId(VariantManagerConstants.INITIAL_VARIANT_ID)
            .id(line.getId())
            .name(line.getNameOrId())
            .type("LINE")
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id(line.getTerminal1().getVoltageLevel().getId()).name(line.getTerminal1().getVoltageLevel().getNameOrId()).build()))
            .build();
    }

    private void initMockBeans(Network network) {
        linesInfosInitialVariant = network.getLineStream().map(StudyTest::toEquipmentInfos).collect(Collectors.toList());

        List<EquipmentInfos> linesInfosDeltaVariantTombstoned = List.of(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("BBE1AA1  BBE2AA1  1").variantId(VARIANT_ID).tombstoned(true).build());
        List<EquipmentInfos> linesInfosDeltaVariantAdded = List.of(EquipmentInfos.builder().networkUuid(NETWORK_UUID).id("SUB_1 SUB_2").variantId(VARIANT_ID).build());

        linesInfosDeltaVariant = new ArrayList<>();
        linesInfosDeltaVariant.addAll(linesInfosDeltaVariantTombstoned);
        linesInfosDeltaVariant.addAll(linesInfosDeltaVariantAdded);

        studiesInfos = List.of(
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111111")).userId("userId1").caseFormat("XIIDM").studyPrivate(false).creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build(),
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111112")).userId("userId1").caseFormat("UCTE").studyPrivate(false).creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build()
        );

        when(studyInfosService.add(any(CreatedStudyBasicInfos.class))).thenReturn(studiesInfos.get(0));
        when(studyInfosService.search(String.format("userId:%s", "userId")))
                .then((Answer<List<CreatedStudyBasicInfos>>) invocation -> studiesInfos);

        when(equipmentInfosService.search(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND equipmentType:(LINE)", NETWORK_UUID_STRING, VariantManagerConstants.INITIAL_VARIANT_ID)))
                .then((Answer<List<EquipmentInfos>>) invocation -> linesInfosInitialVariant);

        when(equipmentInfosService.search(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND tombstoned: true AND equipmentType:(LINE)", NETWORK_UUID_STRING, VARIANT_ID)))
                .then((Answer<List<EquipmentInfos>>) invocation -> linesInfosDeltaVariantTombstoned);

        when(equipmentInfosService.search(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND NOT tombstoned: true AND equipmentType:(LINE)", NETWORK_UUID_STRING, VARIANT_ID)))
                .then((Answer<List<EquipmentInfos>>) invocation -> linesInfosDeltaVariantAdded);
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();
    }

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
            new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        server = new MockWebServer();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        studyService.setCaseServerBaseUri(baseUrl);
        studyService.setNetworkConversionServerBaseUri(baseUrl);
        studyService.setSingleLineDiagramServerBaseUri(baseUrl);
        studyService.setGeoDataServerBaseUri(baseUrl);
        studyService.setNetworkMapServerBaseUri(baseUrl);
        studyService.setLoadFlowServerBaseUri(baseUrl);
        studyService.setSecurityAnalysisServerBaseUri(baseUrl);
        studyService.setActionsServerBaseUri(baseUrl);
        networkStoreService.setNetworkStoreServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        // FIXME: remove lines when dicos will be used on the front side
        mapper.registerModule(new ReporterModelJsonModule() {
            @Override
            public Object getTypeId() {
                return getClass().getName() + "override";
            }
        });

        String networkInfosAsString = mapper.writeValueAsString(NETWORK_INFOS);
        String importedCaseUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_UUID);

        String voltageLevelsMapDataAsString = mapper.writeValueAsString(List.of(
            VoltageLevelMapData.builder().id("BBE1AA1").name("BBE1AA1").substationId("BBE1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("BBE2AA1").name("BBE2AA1").substationId("BBE2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("DDE1AA1").name("DDE1AA1").substationId("DDE1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("DDE2AA1").name("DDE2AA1").substationId("DDE2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("DDE3AA1").name("DDE3AA1").substationId("DDE3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("FFR1AA1").name("FFR1AA1").substationId("FFR1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("FFR3AA1").name("FFR3AA1").substationId("FFR3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("NNL1AA1").name("NNL1AA1").substationId("NNL1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("NNL2AA1").name("NNL2AA1").substationId("NNL2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
            VoltageLevelMapData.builder().id("NNL3AA1").name("NNL3AA1").substationId("NNL3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build()));

        String busesDataAsString = mapper.writeValueAsString(List.of(
            IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
            IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build()));

        String busbarSectionsDataAsString = mapper.writeValueAsString(List.of(
            IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
            IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build()));

        String importedCaseWithErrorsUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_WITH_ERRORS_UUID);
        String importedBlockingCaseUuidAsString = mapper.writeValueAsString(IMPORTED_BLOCKING_CASE_UUID_STRING);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=.*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build());
                    return new MockResponse().setResponseCode(200).setBody("\"" + resultUuid + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/stop.*")
                           || path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=.*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "sa.stopped");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*\\&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*\\&open=true\\&variantId=" + VARIANT_ID)) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*")) {
                    if (Objects.nonNull(body) && body.peek().readUtf8().equals("lockout")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*")) {
                    if (Objects.nonNull(body) && body.peek().readUtf8().equals("trip")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*")) {
                    String bodyStr = Objects.nonNull(body) ? body.peek().readUtf8() : "";
                    if (bodyStr.equals("switchOn") || bodyStr.equals("energiseEndOne")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*")) {
                    return new MockResponse().setResponseCode(500).setBody(LINE_MODIFICATION_FAILED.name());
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s4", "s5", "s6", "s7")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformer\\?group=.*") && request.getMethod().equals("PUT")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/LOAD/id/idLoadToDelete\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("equipmentId", "idLoadToDelete",
                        "equipmentType", "LOAD", "substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=" + NETWORK_UUID_STRING + "\\&reportName=loadflow\\&overwrite=true") ||
                           path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=" + NETWORK_UUID_STRING + "\\&reportName=loadflow\\&overwrite=true\\&variantId=.*")) {
                        return new MockResponse().setResponseCode(200)
                            .setBody("{\n" +
                                "\"version\":\"1.1\",\n" +
                                "\"metrics\":{\n" +
                                "\"network_0_iterations\":\"7\",\n" +
                                "\"network_0_status\":\"CONVERGED\"\n" +
                                "},\n" +
                                "\"isOK\":true,\n" +
                                "\"componentResults\": [{\"componentNum\":0,\"status\":\"CONVERGED\",\"iterationCount\":7, \"slackBusId\": \"c6ace316-6b39-40ec-b1d6-09ab2fe42992\", \"slackBusActivePowerMismatch\": 3.7}]\n" +
                                "}")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING)
                           || path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + "\\&variantId=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(CONTINGENCIES_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/realization.*") && request.getMethod().equals("POST")) {
                    // variant realization
                    input.send(MessageBuilder.withPayload("s1,s2")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "realize.result");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/realization/stop.*")) {
                    // stop variant realization
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "realize.stopped");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                }

                switch (path) {
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/voltage-levels":
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelsMapDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/studies/cases/{caseUuid}":
                        return new MockResponse().setResponseCode(200).setBody("CGMES")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/studies/newStudy/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/exists":
                    case "/v1/cases/11111111-0000-0000-0000-000000000000/exists":
                    case "/v1/cases/88888888-0000-0000-0000-000000000000/exists":
                    case "/v1/cases/11888888-0000-0000-0000-000000000000/exists":
                        return new MockResponse().setResponseCode(200).setBody("true")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/00000000-8cf0-11bd-b23e-10b96e4ef00d/format":
                        return new MockResponse().setResponseCode(200).setBody("UCTE")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/format":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/format":
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/format":
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("false")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/" + CASE_API_VERSION + "/cases/private": {
                        String bodyStr = body.readUtf8();
                        if (bodyStr.contains("filename=\"" + TEST_FILE_WITH_ERRORS + "\"")) {  // import file with errors
                            return new MockResponse().setResponseCode(200).setBody(importedCaseWithErrorsUuidAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else if (bodyStr.contains("filename=\"" + TEST_FILE_IMPORT_ERRORS + "\"")) {  // import file with errors during import in the case server
                            return new MockResponse().setResponseCode(500)
                                .addHeader("Content-Type", "application/json; charset=utf-8")
                                .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                        } else if (bodyStr.contains("filename=\"" + TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY + "\"")) {  // import file with errors during import in the case server without message in response body
                            return new MockResponse().setResponseCode(500)
                                .addHeader("Content-Type", "application/json; charset=utf-8")
                                .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                        } else if (bodyStr.contains("filename=\"blockingCaseFile\"")) {
                            return new MockResponse().setResponseCode(200).setBody(importedBlockingCaseUuidAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                        } else {
                            return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                        }
                    }

                    case "/" + CASE_API_VERSION + "/cases/11111111-0000-0000-0000-000000000000":
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks?caseUuid=" + NEW_STUDY_CASE_UUID:
                    case "/v1/networks?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING:
                        countDownLatch.await(2, TimeUnit.SECONDS);
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_UUID_STRING:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");

                    case "/v1/lines?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations?networkUuid=38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/substations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/2-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/3-windings-transformers/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/generators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/batteries/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/dangling-lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/hvdc-lines/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/lcc-converter-stations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/vsc-converter-stations/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/loads/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/shunt-compensators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/static-var-compensators/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                    case "/v1/all/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        return new MockResponse().setBody(" ").setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/reports/38400000-8cf0-11bd-b23e-10b96e4ef00d":
                        return new MockResponse().setResponseCode(200)
                            .setBody(mapper.writeValueAsString(REPORT_TEST))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("byte")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("svgandmetadata")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(200).setBody("substation-byte")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(200).setBody("substation-svgandmetadata")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-component-libraries":
                        return new MockResponse().setResponseCode(200).setBody("[\"GridSuiteAndConvergence\",\"Convergence\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/export/formats":
                        return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/38400000-8cf0-11bd-b23e-10b96e4ef00d/export/XIIDM":
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "?limitType":
                    case "/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "?limitType":
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_RESULT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/status":
                    case "/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status":
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/invalidate-status?resultUuid=" + SECURITY_ANALYSIS_RESULT_UUID:
                    case "/v1/results/invalidate-status?resultUuid=" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID:
                        return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/configured-buses":
                        return new MockResponse().setResponseCode(200).setBody(busesDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/busbar-sections":
                        return new MockResponse().setResponseCode(200).setBody(busbarSectionsDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    default:
                        LOGGER.error("Path not supported: " + request.getPath());
                        return new MockResponse().setResponseCode(404);
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    private Set<String> getRequestsDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                return server.takeRequest(0, TimeUnit.SECONDS).getPath();
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    private class RequestWithBody {

        public RequestWithBody(String path, String body) {
            this.path = path;
            this.body = body;
        }

        public String getPath() {
            return path;
        }

        public String getBody() {
            return body;
        }

        private String path;
        private String body;
    }

    private Set<RequestWithBody> getRequestsWithBodyDone(int n) {
        return IntStream.range(0, n).mapToObj(i -> {
            try {
                var request = server.takeRequest();
                return new RequestWithBody(request.getPath(), request.getBody().readUtf8());
            } catch (InterruptedException e) {
                LOGGER.error("Error while attempting to get the request done : ", e);
            }
            return null;
        }).collect(Collectors.toSet());
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    public List<EquipmentInfos> expectedMergedEquipmentsInfos(List<EquipmentInfos> initialVariantList, List<EquipmentInfos> deltaVariantList) {
        Set<String> removedEquimpentsIdsInDelta = deltaVariantList.stream().filter(e -> e.getTombstoned() == Boolean.TRUE).map(EquipmentInfos::getId).collect(Collectors.toSet());
        List<EquipmentInfos> addedEquipmentInDelta = deltaVariantList.stream().filter(e -> e.getTombstoned() == null || e.getTombstoned() == Boolean.FALSE).collect(Collectors.toList());
        List<EquipmentInfos> mergedList = initialVariantList.stream().filter(ei -> !removedEquimpentsIdsInDelta.contains(ei.getId())).collect(Collectors.toList());
        mergedList.addAll(addedEquipmentInDelta);
        return mergedList;
    }

    @Test
    public void testSearch() {
        UUID studyUuid = createStudy("userId", CASE_UUID, true);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        webTestClient.get()
                .uri("/v1/studies/search?q={request}", String.format("userId:%s", "userId"))
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(new MatcherJson<>(mapper, studiesInfos));

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?q={request}", studyUuid, rootNodeUuid, "equipmentType:(LINE)")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(EquipmentInfos.class)
            .value(new MatcherJson<>(mapper, linesInfosInitialVariant));

        List<EquipmentInfos> expectedEquipmentsInfos = expectedMergedEquipmentsInfos(linesInfosInitialVariant, linesInfosDeltaVariant);
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?q={request}", studyUuid, modificationNodeUuid, "equipmentType:(LINE)")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(EquipmentInfos.class)
                .value(new MatcherJson<>(mapper, expectedEquipmentsInfos));
    }

    @Test
    public void test() {
        //empty list
        webTestClient.get()
            .uri("/v1/studies")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .isEqualTo("[]");

        //empty list
        webTestClient.get()
            .uri("/v1/study_creation_requests")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .isEqualTo("[]");

        //insert a study
        UUID studyUuid = createStudy("userId", CASE_UUID, false);

        //insert a study with a non existing case and except exception
        webTestClient.post()
                .uri("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}", "00000000-0000-0000-0000-000000000000", "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isEqualTo(424)
                .expectBody()
                .jsonPath("$")
                .isEqualTo(CASE_NOT_FOUND.name());

        assertTrue(getRequestsDone(1).contains(String.format("/v1/cases/%s/exists", "00000000-0000-0000-0000-000000000000")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "UCTE", false));

        //insert the same study but with another user (should work)
        //even with the same name should work
        UUID oldStudyUuid = studyUuid;
        studyUuid = createStudy("userId2", CASE_UUID, true);

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE", true));

        //insert a study with a case (multipartfile)
        UUID s2Uuid = createStudy("userId", TEST_FILE, IMPORTED_CASE_UUID_STRING, true);

        // check the study s2
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}", s2Uuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(s2Uuid, "userId", "XIIDM", true));

        //try to get the study s2 with another user -> unauthorized because study is private
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}", s2Uuid)
            .header("userId", "userId2")
            .exchange()
            .expectStatus().isForbidden();

        UUID randomUuid = UUID.randomUUID();
        //get a non existing study -> 404 not found
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}", randomUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody();

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        //delete existing study s2
        webTestClient.delete()
            .uri("/v1/studies/" + s2Uuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk();

        // assert that the broker message has been sent
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("userId", headers.get(HEADER_USER_ID));
        assertEquals(s2Uuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(Boolean.FALSE, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        var httpRequests = getRequestsDone(2);
        assertTrue(httpRequests.contains(String.format("/v1/networks/%s", NETWORK_UUID_STRING)));
        assertTrue(httpRequests.contains(String.format("/v1/reports/%s", NETWORK_UUID_STRING)));

        //expect only 1 study (public one) since the other is private and we use another userId
        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE", false));

        //get available export format
        webTestClient.get()
            .uri("/v1/export-network-formats")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("[\"CGMES\",\"UCTE\",\"XIIDM\"]");

        assertTrue(getRequestsDone(1).contains("/v1/export/formats"));

        //export a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/export-network/{format}", studyNameUserIdUuid, "XIIDM")
            .exchange()
            .expectStatus().isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/export/XIIDM", NETWORK_UUID_STRING)));

        // make public study private
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "userId", "UCTE", true));

        // make private study private should work
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/private", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "userId", "UCTE", true));

        // make private study public
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/public", studyNameUserIdUuid)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(StudyInfos.class)
                .value(createMatcherStudyInfos(studyNameUserIdUuid, "userId", "UCTE", false));

        // drop the broker message for study deletion (due to right access change)
        output.receive(1000);
        output.receive(1000);
        output.receive(1000);

        // try to change access rights of a non-existing study
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/public", randomUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isNotFound();

        // try to change access rights of a non-existing study
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/private", randomUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isNotFound();
        UUID studyNameUserId2Uuid = studyRepository.findAll().get(1).getId();

        // try to change access right for a study of another user -> forbidden
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/private", studyNameUserId2Uuid)
            .header("userId", "notAuth")
            .exchange()
            .expectStatus().isForbidden();
    }

    @Test
    public void testMetadata() {
        UUID studyUuid = createStudy("userId", CASE_UUID, false);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudy("userId2", CASE_UUID, true);

        StringJoiner ids = new StringJoiner("&id=", "?id=", "");
        ids.add(oldStudyUuid.toString());
        ids.add(studyUuid.toString());
        var res = webTestClient.get()
                .uri("/v1/studies/metadata" + ids)
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
            .returnResult().getResponseBody();

        assertNotNull(res);
        assertEquals(2, res.size());
        if (!res.get(0).getId().equals(oldStudyUuid)) {
            Collections.reverse(res);
        }
        assertTrue(createMatcherCreatedStudyBasicInfos(oldStudyUuid, "userId", "UCTE", false).matchesSafely(res.get(0)));
        assertTrue(createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE", true).matchesSafely(res.get(1)));
    }

    @Test
    public void testLogsReport() {
        createStudy("userId", CASE_UUID, false);
        UUID studyId = studyRepository.findAll().get(0).getId();

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/report", studyId)
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ReporterModel.class)
            .value(new MatcherReport(REPORT_TEST));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", NETWORK_UUID_STRING)));

        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/report", studyId)
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/reports/%s", NETWORK_UUID_STRING)));
    }

    @Test
    public void testLoadFlow() {
        //insert a study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID, false);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        //run a loadflow
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk();
        // assert that the broker message has been sent
        Message<byte[]> messageLfStatus = output.receive(1000);
        assertEquals("", new String(messageLfStatus.getPayload()));
        MessageHeaders headersLF = messageLfStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(rootNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));

        Message<byte[]> messageLf = output.receive(1000);
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(rootNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW, headersLF.get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/run?reportId=%s&reportName=loadflow&overwrite=true", NETWORK_UUID_STRING, NETWORK_UUID_STRING)));

        // check load flow status
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid, rootNodeUuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(LoadFlowInfos.class)
                .value(new MatcherLoadFlowInfos(LoadFlowInfos.builder()
                    .loadFlowStatus(LoadFlowStatus.CONVERGED)
                    .build()));

        //try to run a another loadflow
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isEqualTo(403)
            .expectBody()
            .jsonPath("$")
            .isEqualTo(LOADFLOW_NOT_RUNNABLE.name());

        // get default LoadFlowParameters
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON);

        // setting loadFlow Parameters
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
            .header("userId", "userId")
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(new LoadFlowParameters(
                LoadFlowParameters.VoltageInitMode.DC_VALUES,
                true,
                false,
                true,
                false,
                true,
                false,
                true,
                true,
                true,
                LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD,
                true,
                EnumSet.noneOf(Country.class),
                LoadFlowParameters.ConnectedComponentMode.MAIN))
            )
            .exchange()
            .expectStatus().isOk();

        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));
        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headersLF.get(HEADER_UPDATE_TYPE));

        // getting setted values
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON2);

        // run loadflow with new parameters
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk();

        // assert that the broker message has been sent
        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(rootNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));
        messageLf = output.receive(1000);
        assertEquals("", new String(messageLf.getPayload()));
        headersLF = messageLf.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(rootNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW, headersLF.get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/run?reportId=%s&reportName=loadflow&overwrite=true", NETWORK_UUID_STRING, NETWORK_UUID_STRING)));

        // get default load flow provider
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("");

        // set load flow provider
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .header("userId", "userId")
            .contentType(MediaType.TEXT_PLAIN)
            .body(BodyInserters.fromValue("Hades2"))
            .exchange()
            .expectStatus().isOk();

        messageLfStatus = output.receive(1000);
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, messageLfStatus.getHeaders().get(HEADER_UPDATE_TYPE));

        // get load flow provider
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("Hades2");

        // reset load flow provider to default one
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk();

        messageLfStatus = output.receive(1000);
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, messageLfStatus.getHeaders().get(HEADER_UPDATE_TYPE));

        // get default load flow provider again
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("");

        //run a loadflow on another node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNodeUuid)
            .exchange()
            .expectStatus().isOk();
        // assert that the broker message has been sent
        messageLfStatus = output.receive(1000);
        assertEquals("", new String(messageLfStatus.getPayload()));
        headersLF = messageLfStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(modificationNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLF.get(HEADER_UPDATE_TYPE));

        messageLf = output.receive(1000);
        assertEquals(studyNameUserIdUuid, headersLF.get(HEADER_STUDY_UUID));
        assertEquals(modificationNodeUuid, headersLF.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW, messageLf.getHeaders().get(HEADER_UPDATE_TYPE));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=" + NETWORK_UUID_STRING + "\\&reportName=loadflow\\&overwrite=true\\&variantId=" + VARIANT_ID)));

        // check load flow status
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid, modificationNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(LoadFlowInfos.class)
            .value(new MatcherLoadFlowInfos(LoadFlowInfos.builder()
                .loadFlowStatus(LoadFlowStatus.CONVERGED)
                .build()));
    }

    private void testSecurityAnalysisWithNodeUuid(UUID studyUuid, UUID nodeUuid, UUID resultUuid) {
        // security analysis not found
        webTestClient.get()
            .uri("/v1/security-analysis/results/{resultUuid}", NOT_FOUND_SECURITY_ANALYSIS_UUID)
            .exchange()
            .expectStatus().isNotFound();

        // run security analysis
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}", studyUuid, nodeUuid, CONTINGENCY_LIST_NAME)
            .exchange()
            .expectStatus().isOk()
            .expectBody(UUID.class)
            .isEqualTo(resultUuid);

        Message<byte[]> securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(1000);
        assertEquals(studyUuid, securityAnalysisUpdateMessage.getHeaders().get(HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "\\&receiver=.*nodeUuid.*")));

        // get security analysis result
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo(SECURITY_ANALYSIS_RESULT_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/results/%s?limitType", resultUuid)));

        // get security analysis status
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/status", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo(SECURITY_ANALYSIS_STATUS_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/results/%s/status", resultUuid)));

        // stop security analysis
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk();

        securityAnalysisStatusMessage = output.receive(1000);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));

        // get contingency count
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/contingency-count?contingencyListName={contingencyListName}", studyUuid, nodeUuid, CONTINGENCY_LIST_NAME)
            .exchange()
            .expectStatus().isOk()
            .expectBody(Integer.class)
            .isEqualTo(1);

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + ".*")));
    }

    @Test
    public void testSecurityAnalysis() {
        //insert a study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID, false);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, rootNodeUuid, UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNodeUuid, UUID.fromString(SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID));
    }

    @Test
    public void testDiagramsAndGraphics() {
        //insert a study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID, false);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        UUID randomUuid = UUID.randomUUID();

        //get the voltage level diagram svg
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelId")
            .exchange()
            .expectHeader().contentType(MediaType.APPLICATION_XML)
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("byte");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/svg/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        //get the voltage level diagram svg from a study that doesn't exist
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg", randomUuid, rootNodeUuid, "voltageLevelId")
            .exchange()
            .expectStatus().isNotFound();

        //get the voltage level diagram svg and metadata
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelId")
            .exchange()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("svgandmetadata");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        //get the voltage level diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata", randomUuid, rootNodeUuid, "voltageLevelId")
            .exchange()
            .expectStatus().isNotFound();

        // get the substation diagram svg
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationId")
            .exchange()
            .expectHeader().contentType(MediaType.APPLICATION_XML)
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("substation-byte");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substation-svg/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        // get the substation diagram svg from a study that doesn't exist
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg", randomUuid, rootNodeUuid, "substationId")
            .exchange()
            .expectStatus().isNotFound();

        // get the substation diagram svg and metadata
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationId")
            .exchange()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("substation-svgandmetadata");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substation-svg-and-metadata/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        // get the substation diagram svg and metadata from a study that doesn't exist
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata", randomUuid, rootNodeUuid, "substationId")
            .exchange()
            .expectStatus().isNotFound();

        //get voltage levels
        EqualsVerifier.simple().forClass(VoltageLevelMapData.class).verify();
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(VoltageLevelInfos.class)
            .value(new MatcherJson<>(mapper, List.of(
                VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").substationId("BBE1AA").build(),
                VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").substationId("BBE2AA").build(),
                VoltageLevelInfos.builder().id("DDE1AA1").name("DDE1AA1").substationId("DDE1AA").build(),
                VoltageLevelInfos.builder().id("DDE2AA1").name("DDE2AA1").substationId("DDE2AA").build(),
                VoltageLevelInfos.builder().id("DDE3AA1").name("DDE3AA1").substationId("DDE3AA").build(),
                VoltageLevelInfos.builder().id("FFR1AA1").name("FFR1AA1").substationId("FFR1AA").build(),
                VoltageLevelInfos.builder().id("FFR3AA1").name("FFR3AA1").substationId("FFR3AA").build(),
                VoltageLevelInfos.builder().id("NNL1AA1").name("NNL1AA1").substationId("NNL1AA").build(),
                VoltageLevelInfos.builder().id("NNL2AA1").name("NNL2AA1").substationId("NNL2AA").build(),
                VoltageLevelInfos.builder().id("NNL3AA1").name("NNL3AA1").substationId("NNL3AA").build()
            )));

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/voltage-levels", NETWORK_UUID_STRING)));

        //get the lines-graphics of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/geo-data/lines/", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lines?networkUuid=%s", NETWORK_UUID_STRING)));

        //get the substation-graphics of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/geo-data/substations/", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substations?networkUuid=%s", NETWORK_UUID_STRING)));

        //get the lines map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lines/%s", NETWORK_UUID_STRING)));

        //get the substation map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/substations/%s", NETWORK_UUID_STRING)));

        //get the 2 windings transformers map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/2-windings-transformers/%s", NETWORK_UUID_STRING)));

        //get the 3 windings transformers map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/3-windings-transformers/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/3-windings-transformers/%s", NETWORK_UUID_STRING)));

        //get the generators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/generators/%s", NETWORK_UUID_STRING)));

        //get the batteries map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/batteries/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/batteries/%s", NETWORK_UUID_STRING)));

        //get the dangling lines map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/dangling-lines/%s", NETWORK_UUID_STRING)));

        //get the hvdc lines map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/hvdc-lines/%s", NETWORK_UUID_STRING)));

        //get the lcc converter stations map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/lcc-converter-stations/%s", NETWORK_UUID_STRING)));

        //get the vsc converter stations map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/vsc-converter-stations/%s", NETWORK_UUID_STRING)));

        //get the loads map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/loads/%s", NETWORK_UUID_STRING)));

        //get the shunt compensators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/shunt-compensators/%s", NETWORK_UUID_STRING)));

        //get the static var compensators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/static-var-compensators/%s", NETWORK_UUID_STRING)));

        //get all map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/all/%s", NETWORK_UUID_STRING)));

        // get the svg component libraries
        webTestClient.get()
            .uri("/v1/svg-component-libraries")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/svg-component-libraries")));
    }

    @Test
    public void testNetworkModificationSwitch() {
        createStudy("userId", CASE_UUID, false);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        //update switch
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, rootNodeUuid, "switchId")
            .exchange()
            .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*\\&open=true")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE", false));

        //update switch on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, modificationNodeUuid, "switchId")
            .exchange()
            .expectStatus().isOk();
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*\\&open=true\\&variantId=" + VARIANT_ID)));
    }

    @SneakyThrows
    @Test
    public void testNetworkModificationEquipment() {
        createStudy("userId", CASE_UUID, false);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        //update equipment
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid, rootNodeUuid)
            .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .exchange()
            .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        checkEquipmentMessagesReceived(studyNameUserIdUuid, rootNodeUuid, HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE", false));

        // get all modifications for the default group of a network
        RootNode rootNode = getRootNode(studyNameUserIdUuid);

        webTestClient.get()
            .uri("/v1/studies/{groupUuid}/network/modifications", rootNode.getNetworkModification())
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk();

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/groups/" + rootNode.getNetworkModification())));

        // delete all modifications for the default group of a network
        webTestClient.delete()
            .uri("/v1/studies/{groupUuid}/network/modifications", rootNode.getNetworkModification())
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk();

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/groups/" + rootNode.getNetworkModification())));

        //update equipment on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid, modificationNodeUuid)
            .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .exchange()
            .expectStatus().isOk();

        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*\\&variantId=" + VARIANT_ID)));
    }

    @Test
    public void testCreationWithErrorBadCaseFile() {
        // Create study with a bad case file -> error
        createStudy("userId", TEST_FILE_WITH_ERRORS, IMPORTED_CASE_WITH_ERRORS_UUID_STRING, false,
                "The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'");
    }

    @Test
    public void testCreationWithErrorBadExistingCase() {
        // Create study with a bad case file -> error when importing in the case server
        createStudy("userId", TEST_FILE_IMPORT_ERRORS, null, false,
                "Error during import in the case server");
    }

    @Test
    public void testCreationWithErrorNoMessageBadExistingCase() {
        // Create study with a bad case file -> error when importing in the case server without message in response body
        createStudy("userId", TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY, null, false,
                "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
    }

    private NetworkModificationNode createNetworkModificationNode(UUID parentNodeUuid) {
        return createNetworkModificationNode(parentNodeUuid, false, UUID.randomUUID(), VARIANT_ID);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID parentNodeUuid, boolean isRealized, UUID networkModificationUuid, String variantId) {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder()
            .name("hypo")
            .description("description")
            .networkModification(networkModificationUuid)
            .variantId(variantId)
            .loadFlowStatus(LoadFlowStatus.NOT_DONE)
            .isRealized(isRealized)
            .children(Collections.emptyList())
            .build();
        webTestClient.post().uri("/v1/tree/nodes/{id}", parentNodeUuid).bodyValue(modificationNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(HEADER_INSERT_BEFORE));
        return modificationNode;
    }

    private ModelNode createModelNode(UUID parentNodeUuid) {
        ModelNode modelNode = ModelNode.builder()
            .name("model")
            .model("loadflow")
            .description("model")
            .children(Collections.emptyList())
            .build();
        webTestClient.post().uri("/v1/tree/nodes/{id}", parentNodeUuid).bodyValue(modelNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        modelNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(HEADER_INSERT_BEFORE));
        return modelNode;
    }

    private RootNode getRootNode(UUID studyUuid) throws IOException {
        return mapper.readValue(webTestClient.get().uri("/v1/tree/{id}", studyUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody(), new TypeReference<>() {
            });
    }

    @SneakyThrows
    private UUID createStudy(String userId, UUID caseUuid, boolean isPrivate, String... errorMessage) {
        BasicStudyInfos infos = webTestClient.post()
                .uri("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}",
                        caseUuid, isPrivate)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .returnResult()
                .getResponseBody();

        UUID studyUuid = infos.getId();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(1000);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message for deletion
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(3);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_STRING)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/format", CASE_UUID_STRING)));
        assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", CASE_UUID_STRING)));

        return studyUuid;
    }

    @SneakyThrows
    private UUID createStudy(String userId, String fileName, String caseUuid, boolean isPrivate, String... errorMessage) {
        final WebTestClient.ResponseSpec exchange;
        final UUID studyUuid;
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + fileName))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", fileName, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                .filename(fileName)
                .contentType(MediaType.TEXT_XML);

            exchange = webTestClient.post()
                    .uri(STUDIES_URL + "?isPrivate={isPrivate}", isPrivate)
                    .header("userId", userId)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange();

            studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

            exchange.expectStatus().isOk()
                    .expectBody(BasicStudyInfos.class)
                    .value(createMatcherStudyBasicInfos(studyUuid, userId, isPrivate));
        }

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message for deletion
        message = output.receive(1000);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertNotEquals(isPrivate, headers.get(HEADER_IS_PUBLIC_STUDY));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(caseUuid == null ? 1 : 3);
        assertTrue(requests.contains("/v1/cases/private"));
        if (caseUuid != null) {
            assertTrue(requests.contains(String.format("/v1/cases/%s/format", caseUuid)));
            assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", caseUuid)));
        }

        return studyUuid;
    }

    @Test
    public void testGetStudyCreationRequests() throws Exception {
        countDownLatch = new CountDownLatch(1);

        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("blockingCaseFile/cases/private", "testCase.xiidm", "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                .filename("blockingCaseFile")
                .contentType(MediaType.TEXT_XML);

            webTestClient.post()
                    .uri(STUDIES_URL + "?isPrivate={isPrivate}", "true")
                    .header("userId", "userId")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(BasicStudyInfos.class)
                    .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId", true));
        }

        UUID studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherStudyBasicInfos(studyUuid, "userId", true));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(1000);

        webTestClient.get()
            .uri("/v1/study_creation_requests")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(BasicStudyInfos.class)
            .isEqualTo(List.of());

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM", true));

        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

        // assert that all http requests have been sent to remote services
        var httpRequests = getRequestsDone(3);
        assertTrue(httpRequests.contains("/v1/cases/private"));
        assertTrue(httpRequests.contains(String.format("/v1/cases/%s/format", IMPORTED_BLOCKING_CASE_UUID_STRING)));
        assertTrue(httpRequests.contains(String.format("/v1/networks?caseUuid=%s", IMPORTED_BLOCKING_CASE_UUID_STRING)));

        countDownLatch = new CountDownLatch(1);

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}", NEW_STUDY_CASE_UUID, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId", false));

        studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherStudyBasicInfos(studyUuid, "userId", false));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(1000);

        webTestClient.get()
            .uri("/v1/study_creation_requests")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(BasicStudyInfos.class)
            .isEqualTo(List.of());

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM", false));

        // drop the broker message for study creation request (creation)
        output.receive(1000);
        // drop the broker message for study creation
        output.receive(1000);
        // drop the broker message for study creation request (deletion)
        output.receive(1000);

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(3);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/format", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains(String.format("/v1/networks?caseUuid=%s", NEW_STUDY_CASE_UUID)));
    }

    @Test
    public void testUpdateLines() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // lockout line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "line12")
            .bodyValue("lockout")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s1", "s2"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "lineFailedId")
            .bodyValue("lockout")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // trip line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "line23")
            .bodyValue("trip")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s2", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "lineFailedId")
            .bodyValue("trip")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // energise line end
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "line13")
            .bodyValue("energiseEndOne")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "lineFailedId")
            .bodyValue("energiseEndTwo")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // switch on line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "line13")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "lineFailedId")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // switch on line on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNodeUuid, "line13")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isOk();
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s3"));

        var requests = getRequestsWithBodyDone(9);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("energiseEndOne")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("energiseEndTwo")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals("switchOn")));
    }

    @Test
    public void testCreateLoad() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // create load
        String createLoadAttributes = "{\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, rootNodeUuid)
            .bodyValue(createLoadAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s2"));

        // create load on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue(createLoadAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*") && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals(createLoadAttributes)));
    }

    @Test
    public void testDeleteEquipment() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // delete equipment
        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, rootNodeUuid, "LOAD", "idLoadToDelete")
            .exchange()
            .expectStatus().isOk();
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, rootNodeUuid, HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete",
            HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, "LOAD", HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));

        // delete equipment on modification node child of root node
        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, modificationNodeUuid, "LOAD", "idLoadToDelete")
            .exchange()
            .expectStatus().isOk();
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete",
            HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, "LOAD", HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*\\&variantId=" + VARIANT_ID)));
    }

    private void checkEquipmentDeletedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeEquipmentType, String equipmentType,
                                                       String headerUpdateTypeEquipmentId, String equipmentId, String headerUpdateTypeSubstationsIds, Set<String> modifiedSubstationsIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(1000);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_STUDY, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(equipmentType, headersStudyUpdate.get(headerUpdateTypeEquipmentType));
        assertEquals(equipmentId, headersStudyUpdate.get(headerUpdateTypeEquipmentId));
        assertEquals(modifiedSubstationsIdsSet, headersStudyUpdate.get(headerUpdateTypeSubstationsIds));

        // assert that the broker message has been sent for updating load flow status
        Message<byte[]> messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        MessageHeaders headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLFStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersLFStatus.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLFStatus.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent for updating security analysis status
        Message<byte[]> messageSAStatus = output.receive(1000);
        assertEquals("", new String(messageSAStatus.getPayload()));
        MessageHeaders headersSAStatus = messageSAStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSAStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSAStatus.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headersSAStatus.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeId, Set<String> modifiedIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(1000);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_STUDY, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedIdsSet, headersStudyUpdate.get(headerUpdateTypeId));

        // assert that the broker message has been sent for updating load flow status
        Message<byte[]> messageLFStatus = output.receive(1000);
        assertEquals("", new String(messageLFStatus.getPayload()));
        MessageHeaders headersLFStatus = messageLFStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersLFStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersLFStatus.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_LOADFLOW_STATUS, headersLFStatus.get(StudyService.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent for updating security analysis status
        Message<byte[]> messageSAStatus = output.receive(1000);
        assertEquals("", new String(messageSAStatus.getPayload()));
        MessageHeaders headersSAStatus = messageSAStatus.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSAStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSAStatus.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, headersSAStatus.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedIdsSet);
    }

    private void checkLineModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageLine = output.receive(1000);
        assertEquals("", new String(messageLine.getPayload()));
        MessageHeaders headersSwitch = messageLine.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(HEADER_NODE));
        assertEquals(StudyService.UPDATE_TYPE_LINE, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkSwitchModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(1000);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_SWITCH, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
    }

    @Test
    public void testGetBusesOrBusbarSections() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses", studyNameUserIdUuid, rootNodeUuid, VOLTAGE_LEVEL_ID)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(IdentifiableInfos.class)
            .value(new MatcherJson<>(mapper, List.of(
                IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
                IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build()
            )));

        var requests = getRequestsDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/configured-buses")));

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/busbar-sections", studyNameUserIdUuid, rootNodeUuid, VOLTAGE_LEVEL_ID)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(IdentifiableInfos.class)
            .value(new MatcherJson<>(mapper, List.of(
                IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
                IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build()
            )));

        requests = getRequestsDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/busbar-sections")));
    }

    @Test
    public void testCreateGenerator() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // create generator
        String createGeneratorAttributes = "{\"generatorId\":\"generatorId1\",\"generatorName\":\"generatorName1\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators", studyNameUserIdUuid, rootNodeUuid)
            .bodyValue(createGeneratorAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s2"));

        // create generator on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators", studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue(createGeneratorAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*") && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals(createGeneratorAttributes)));
    }

    @Test
    public void testCreateLine() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // create line
        String createLineAttributes = "{" +
                "\"lineId\":\"lineId1\"," +
                "\"lineName\":\"lineName1\"," +
                "\"seriesResistance\":\"50.0\"," +
                "\"seriesReactance\":\"50.0\"," +
                "\"shuntConductance1\":\"100.0\"," +
                "\"shuntSusceptance1\":\"100.0\"," +
                "\"shuntConductance2\":\"200.0\"," +
                "\"shuntSusceptance2\":\"200.0\"," +
                "\"voltageLevelId1\":\"idVL1\"," +
                "\"busOrBusbarSectionId1\":\"idBus1\"," +
                "\"voltageLevelId2\":\"idVL2\"," +
                "\"busOrBusbarSectionId2\":\"idBus2\"}";
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines", studyNameUserIdUuid, rootNodeUuid)
            .bodyValue(createLineAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s2"));

        // create line on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines", studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue(createLineAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*") && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals(createLineAttributes)));
    }

    @Test
    public void testCreateTwoWindingsTransformer() {
        createStudy("userId", CASE_UUID, true);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        // create 2WT
        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformer", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createTwoWindingsTransformerAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, rootNodeUuid, ImmutableSet.of("s2"));

        // create 2WT on modification node child of root node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformer", studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue(createTwoWindingsTransformerAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformer\\?group=.*") && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformer\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
    }

    private void testRealizationWithNodeUuid(UUID studyUuid, UUID nodeUuid) {
        // realize node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/realization", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk();

        Message<byte[]> realizationStatusMessage = output.receive(1000);
        assertEquals(studyUuid, realizationStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(nodeUuid, realizationStatusMessage.getHeaders().get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_REALIZATION_COMPLETED, realizationStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), realizationStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/realization\\?receiver=.*")));

        assertTrue(networkModificationTreeService.isRealized(nodeUuid));  // node is realized

        // stop realization
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/realization/stop", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk();

        realizationStatusMessage = output.receive(1000);
        assertEquals(studyUuid, realizationStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(nodeUuid, realizationStatusMessage.getHeaders().get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_REALIZATION_CANCELLED, realizationStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        assertFalse(networkModificationTreeService.isRealized(nodeUuid));  // node is not realized

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/realization/stop\\?receiver=.*")));
    }

    @Test
    public void testRealization() {
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID, false);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(rootNodeUuid, false, modificationGroupUuid1, "variant_1");
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(modificationNode1.getId(), false, modificationGroupUuid2, "variant_2");
        ModelNode modelNode1 = createModelNode(modificationNode2.getId());
        UUID modificationGroupUuid3 = UUID.randomUUID();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(modelNode1.getId(), false, modificationGroupUuid3, "variant_3");
        ModelNode modelNode2 = createModelNode(modificationNode3.getId());
        UUID modificationGroupUuid4 = UUID.randomUUID();
        NetworkModificationNode modificationNode4 = createNetworkModificationNode(modelNode2.getId(), false, modificationGroupUuid4, "variant_4");

        RealizationInfos realizationInfos = networkModificationTreeService.getRealizationInfos(modificationNode4.getId());
        assertNull(realizationInfos.getOriginVariantId());  // previous realized node is root node
        assertEquals("variant_4", realizationInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid1, modificationGroupUuid2, modificationGroupUuid3, modificationGroupUuid4), realizationInfos.getModifications());

        modificationNode2.setRealized(true);  // mark node modificationNode2 as realized
        networkModificationTreeService.doUpdateNode(modificationNode2);
        output.receive(1000);

        realizationInfos = networkModificationTreeService.getRealizationInfos(modificationNode4.getId());
        assertEquals("variant_2", realizationInfos.getOriginVariantId());  // variant to clone is variant associated to node modificationNode2
        assertEquals("variant_4", realizationInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid3, modificationGroupUuid4), realizationInfos.getModifications());

        testRealizationWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId());
        testRealizationWithNodeUuid(studyNameUserIdUuid, modificationNode4.getId());
    }

    @After
    public void tearDown() {
        cleanDB();

        Set<String> httpRequest = null;
        Message<byte[]> message = null;
        try {
            httpRequest = getRequestsDone(1);
            message = output.receive(1000);
        } catch (NullPointerException e) {
            // Ignoring
        }

        // Shut down the server. Instances cannot be reused.
        try {
            server.shutdown();
        } catch (Exception e) {
            // Ignoring
        }

        output.clear(); // purge in order to not fail the other tests

        assertNull("Should not be any messages : ", message);
        assertNull("Should not be any http requests : ", httpRequest);
    }
}
