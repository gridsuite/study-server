/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

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
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
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
import org.gridsuite.study.server.dto.modification.BusbarSectionCreationInfos;
import org.gridsuite.study.server.dto.modification.EquipmentModificationInfos;
import org.gridsuite.study.server.dto.modification.LineSplitWithVoltageLevelInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.modification.VoltageLevelCreationInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.MatcherLoadFlowInfos;
import org.gridsuite.study.server.utils.MatcherReport;
import org.jetbrains.annotations.NotNull;
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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.gridsuite.study.server.NetworkModificationTreeService.HEADER_INSERT_MODE;
import static org.gridsuite.study.server.NetworkModificationTreeService.HEADER_NEW_NODE;
import static org.gridsuite.study.server.NetworkModificationTreeService.NODE_UPDATED;
import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyService.*;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureWebTestClient//(timeout = "PT59S") // allows time to step with debugger
@EnableWebFlux
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
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
    public static final String LOAD_PARAMETERS_JSON = "{\"version\":\"1.7\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"shuntCompensatorVoltageControlOn\":false,\"readSlackBus\":true,\"writeSlackBus\":false,\"dc\":false,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_GENERATION_P_MAX\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\",\"hvdcAcEmulation\":true}";
    public static final String LOAD_PARAMETERS_JSON2 = "{\"version\":\"1.7\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"noGeneratorReactiveLimits\":false,\"twtSplitShuntAdmittance\":false,\"shuntCompensatorVoltageControlOn\":true,\"readSlackBus\":false,\"writeSlackBus\":true,\"dc\":true,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_CONFORM_LOAD\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\",\"hvdcAcEmulation\":true}";
    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");
    private static final ReporterModel REPORT_TEST_ROOT_NODE = new ReporterModel("test", "Root");
    private static final String VOLTAGE_LEVEL_ID = "VOLTAGE_LEVEL_ID";
    private static final String VARIANT_ID = "variant_1";
    public static final String POST = "POST";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String LOAD_ID_1 = "LOAD_ID_1";
    private static final String LINE_ID_1 = "LINE_ID_1";
    private static final String GENERATOR_ID_1 = "GENERATOR_ID_1";
    private static final String SHUNT_COMPENSATOR_ID_1 = "SHUNT_COMPENSATOR_ID_1";
    private static final String TWO_WINDINGS_TRANSFORMER_ID_1 = "2WT_ID_1";
    private static final String SUBSTATION_ID_1 = "SUBSTATION_ID_1";
    private static final String VL_ID_1 = "VL_ID_1";
    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final String CASE_4_UUID_STRING = "196719f5-cccc-48be-be46-e92345951e32";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";
    private static final String NETWORK_UUID_4_STRING = "33333333-cccc-48be-be46-e92345951e32";
    private static final NetworkInfos NETWORK_INFOS_2 = new NetworkInfos(UUID.fromString(NETWORK_UUID_2_STRING), "file_2.xiidm");
    private static final NetworkInfos NETWORK_INFOS_3 = new NetworkInfos(UUID.fromString(NETWORK_UUID_3_STRING), "file_3.xiidm");
    private static final NetworkInfos NETWORK_INFOS_4 = new NetworkInfos(UUID.fromString(NETWORK_UUID_4_STRING), "file_4.xiidm");

    private static final String CASE_LOADFLOW_ERROR_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_ERROR_UUID = UUID.fromString(CASE_LOADFLOW_ERROR_UUID_STRING);
    private static final String NETWORK_LOADFLOW_ERROR_UUID_STRING = "7845000f-5af0-14be-bc3e-10b96e4ef00d";
    private static final UUID NETWORK_LOADFLOW_ERROR_UUID = UUID.fromString(NETWORK_LOADFLOW_ERROR_UUID_STRING);
    private static final NetworkInfos NETWORK_LOADFLOW_ERROR_INFOS = new NetworkInfos(NETWORK_LOADFLOW_ERROR_UUID, "20140116_0830_2D4_UX1_pst");

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private StudyService studyService;

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

    private List<EquipmentInfos> linesInfos;

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

    @MockBean
    private NetworkStoreService networkStoreService;

    private static EquipmentInfos toEquipmentInfos(Line line) {
        return EquipmentInfos.builder()
            .networkUuid(NETWORK_UUID)
            .id(line.getId())
            .name(line.getNameOrId())
            .type("LINE")
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id(line.getTerminal1().getVoltageLevel().getId()).name(line.getTerminal1().getVoltageLevel().getNameOrId()).build()))
            .build();
    }

    private void initMockBeans(Network network) {
        linesInfos = network.getLineStream().map(StudyTest::toEquipmentInfos).collect(Collectors.toList());

        studiesInfos = List.of(
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111111")).userId("userId1").caseFormat("XIIDM").creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build(),
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111112")).userId("userId1").caseFormat("UCTE").creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build()
        );

        when(studyInfosService.add(any(CreatedStudyBasicInfos.class))).thenReturn(studiesInfos.get(0));
        when(studyInfosService.search(String.format("userId:%s", "userId")))
                .then((Answer<List<CreatedStudyBasicInfos>>) invocation -> studiesInfos);

        when(equipmentInfosService.searchEquipments(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND equipmentName.fullascii:(*B*)", NETWORK_UUID_STRING, VariantManagerConstants.INITIAL_VARIANT_ID)))
            .then((Answer<List<EquipmentInfos>>) invocation -> linesInfos);

        when(equipmentInfosService.searchEquipments(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND equipmentId.fullascii:(*B*)", NETWORK_UUID_STRING, VariantManagerConstants.INITIAL_VARIANT_ID)))
            .then((Answer<List<EquipmentInfos>>) invocation -> linesInfos);

        doNothing().when(networkStoreService).deleteNetwork(NETWORK_UUID);
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();
        equipmentInfosService.deleteAll(NETWORK_UUID);
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
        String networkInfos2AsString = mapper.writeValueAsString(NETWORK_INFOS_2);
        String networkInfos3AsString = mapper.writeValueAsString(NETWORK_INFOS_3);
        String networkInfos4AsString = mapper.writeValueAsString(NETWORK_INFOS_4);
        String importedCaseUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_UUID);
        String networkLoadFlowErrorInfosAsString = mapper.writeValueAsString(NETWORK_LOADFLOW_ERROR_INFOS);

        LoadFlowResult loadFlowOK = new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs",
            List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 10, "bus_1", 5.),
                new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.FAILED, 20, "bus_2", 10.)));
        String loadFlowOKString = mapper.writeValueAsString(loadFlowOK);

        LoadFlowResult loadFlowError = new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs",
            List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, 10, "bus_1", 5.),
                new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 20, "bus_2", 10.)));
        String loadFlowErrorString = mapper.writeValueAsString(loadFlowError);

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

        String loadDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(LOAD_ID_1).name("LOAD_NAME_1").build());
        String lineDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build());
        String generatorDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(GENERATOR_ID_1).name("GENERATOR_NAME_1").build());
        String shuntCompensatorDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(SHUNT_COMPENSATOR_ID_1).name("SHUNT_COMPENSATOR_NAME_1").build());
        String twoWindingsTransformerDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(TWO_WINDINGS_TRANSFORMER_ID_1).name("2WT_NAME_1").build());
        String voltageLevelDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(VL_ID_1).name("VL_NAME_1").build());
        String substationDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build());
        String importedCaseWithErrorsUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_WITH_ERRORS_UUID);
        String importedBlockingCaseUuidAsString = mapper.writeValueAsString(IMPORTED_BLOCKING_CASE_UUID_STRING);

        EquipmentModificationInfos lineToSplitDeletion = EquipmentModificationInfos.builder()
            .type(ModificationType.EQUIPMENT_DELETION)
            .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
            .build();
        List<EquipmentModificationInfos> lineSplitResponseInfos = new ArrayList<>();
        lineSplitResponseInfos.add(lineToSplitDeletion);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build());
                    return new MockResponse().setResponseCode(200).setBody("\"" + resultUuid + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/stop.*")
                           || path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "sa.stopped");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID) ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*")) {
                    if (body.peek().readUtf8().equals("lockout")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*")) {
                    if (body.peek().readUtf8().equals("trip")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*")) {
                    String bodyStr = body.peek().readUtf8();
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
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && POST.equals(request.getMethod())) {
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
                }  else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators[?]group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                }  else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(substationDataAsString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                }  else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(voltageLevelDataAsString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                }  else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-splits[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(mapper.writeValueAsString(lineSplitResponseInfos))
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true") ||
                           path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&variantId=.*") ||
                           path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=(Hades2|OpenLoadFlow)&variantId=.*")) {
                        return new MockResponse().setResponseCode(200)
                            .setBody(loadFlowOKString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true") ||
                    path.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&variantId=.*") ||
                    path.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=(Hades2|OpenLoadFlow)&variantId=.*")) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(loadFlowErrorString)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING)
                           || path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(CONTINGENCIES_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // variant build
                    input.send(MessageBuilder.withPayload("s1,s2")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.result");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/build/stop.*")) {
                    // stop variant build
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.stopped");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*/modifications[?]") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks/.*/reindex-all")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_LOADFLOW_ERROR_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse().setBody(String.valueOf(networkLoadFlowErrorInfosAsString)).setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    countDownLatch.await(2, TimeUnit.SECONDS);
                    return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                    .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/reports/.*")) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORT_TEST))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")
                           || path.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                }

                switch (path) {
                    case "/v1/networks/" + NETWORK_UUID_STRING:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels":
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelsMapDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/studies/cases/{caseUuid}":
                        return new MockResponse().setResponseCode(200).setBody("CGMES")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/studies/newStudy/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + CASE_UUID_STRING + "/exists":
                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/exists":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/exists":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/exists":
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/exists":
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/exists":
                    case "/v1/cases/" + CASE_4_UUID_STRING + "/exists":
                    case "/v1/cases/" + CASE_LOADFLOW_ERROR_UUID_STRING + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("true")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + CASE_UUID_STRING + "/format":
                        return new MockResponse().setResponseCode(200).setBody("UCTE")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/format":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/format":
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_4_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_LOADFLOW_ERROR_UUID_STRING + "/format":
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

                    case "/" + CASE_API_VERSION + "/cases/" + IMPORTED_CASE_UUID_STRING:
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID:
                    case "/v1/networks?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        countDownLatch.await(2, TimeUnit.SECONDS);
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_2_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfos2AsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_3_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfos3AsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + CASE_4_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfos4AsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");

                    case "/v1/lines?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/substations?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/substations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/2-windings-transformers":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/3-windings-transformers":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/generators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/batteries":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/dangling-lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/hvdc-lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lcc-converter-stations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/vsc-converter-stations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/loads":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/static-var-compensators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/all":
                        return new MockResponse().setBody(" ").setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks?caseUuid=" + CASE_LOADFLOW_ERROR_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkLoadFlowErrorInfosAsString)).setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/reports/" + NETWORK_UUID_STRING:
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

                    case "/v1/network-area-diagram/" + NETWORK_UUID_STRING + "?depth=0&voltageLevelsIds=vlFr1A":
                        return new MockResponse().setResponseCode(200).setBody("nad-svg")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-component-libraries":
                        return new MockResponse().setResponseCode(200).setBody("[\"GridSuiteAndConvergence\",\"Convergence\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/export/formats":
                        return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM":
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM" + "?variantId=" + VARIANT_ID:
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

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/loads/" + LOAD_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(loadDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/" + LINE_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(lineDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/generators/" + GENERATOR_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(generatorDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators/" + SHUNT_COMPENSATOR_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(shuntCompensatorDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/2-windings-transformers/" + TWO_WINDINGS_TRANSFORMER_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(twoWindingsTransformerDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/substations/" + SUBSTATION_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(substationDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VL_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    default:
                        LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                        return new MockResponse().setResponseCode(418);
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

    private static class RequestWithBody {

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

        private final String path;
        private final String body;
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

    @Test
    public void testSearch() {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeId = getRootNodeUuid(studyUuid);

        webTestClient.get()
                .uri("/v1/search?q={request}", String.format("userId:%s", "userId"))
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(new MatcherJson<>(mapper, studiesInfos));

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=name", studyUuid, rootNodeId, "B")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(EquipmentInfos.class)
            .value(new MatcherJson<>(mapper, linesInfos));

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME", studyUuid, rootNodeId, "B")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(EquipmentInfos.class)
            .value(new MatcherJson<>(mapper, linesInfos));

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=ID", studyUuid, rootNodeId, "B")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(EquipmentInfos.class)
            .value(new MatcherJson<>(mapper, linesInfos));

        byte[] resp = webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=bogus", studyUuid, rootNodeId, "B")
            .header("userId", "userId")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody().returnResult().getResponseBody();

        assertNotNull(resp);
        String asStr = new String(resp);
        assertEquals("Enum unknown entry 'bogus' should be among NAME, ID", asStr);
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
        UUID studyUuid = createStudy("userId", CASE_UUID);

        // check the study
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}", studyUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(StudyInfos.class)
            .value(createMatcherStudyInfos(studyUuid, "userId", "UCTE"));

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
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "UCTE"));

        //insert the same study but with another user (should work)
        //even with the same name should work
        studyUuid = createStudy("userId2", CASE_UUID);

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId2")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE"));

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
                .value(createMatcherStudyInfos(s2Uuid, "userId", "XIIDM"));

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
        Message<byte[]> message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("userId", headers.get(HEADER_USER_ID));
        assertEquals(s2Uuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        var httpRequests = getRequestsDone(1);
        assertTrue(httpRequests.stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        //expect only 1 study (public one) since the other is private and we use another userId
        var result = webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "a")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .returnResult();
        assertEquals(2, result.getResponseBody().size());

        //get available export format
        webTestClient.get()
            .uri("/v1/export-network-formats")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("[\"CGMES\",\"UCTE\",\"XIIDM\"]");

        assertTrue(getRequestsDone(1).contains("/v1/export/formats"));

        //export a network
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, rootNodeUuid, "XIIDM")
            .exchange()
            .expectStatus().isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/export/XIIDM", NETWORK_UUID_STRING)));

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, modificationNode1Uuid, "XIIDM")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        modificationNode1.setBuildStatus(BuildStatus.BUILT);
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, modificationNode1Uuid, "XIIDM")
            .exchange()
            .expectStatus().isOk();

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/export/XIIDM?variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));
    }

    @Test
    public void testMetadata() {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudy("userId2", CASE_UUID);

        var res = webTestClient.get()
                .uri("/v1/studies/metadata?ids=" + Stream.of(oldStudyUuid, studyUuid).map(Object::toString).collect(Collectors.joining(",")))
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
        assertTrue(createMatcherCreatedStudyBasicInfos(oldStudyUuid, "userId", "UCTE").matchesSafely(res.get(0)));
        assertTrue(createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE").matchesSafely(res.get(1)));
    }

    @Test
    public void testLogsReport() {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/report", studyUuid, rootNodeUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(ReporterModel.class)
            .value(reports -> reports.get(0), new MatcherReport(REPORT_TEST_ROOT_NODE));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/report", studyUuid, rootNodeUuid)
            .header("userId", "userId")
            .exchange()
            .expectStatus()
            .isOk();

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    public void testLoadFlow() {
        //insert a study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run a loadflow on root node (not allowed)
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid)
                .exchange()
                .expectStatus().isForbidden();

        //run a loadflow
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode2Uuid)
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, UPDATE_TYPE_LOADFLOW);

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // check load flow status
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid, modificationNode2Uuid)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(LoadFlowInfos.class)
                .value(new MatcherLoadFlowInfos(LoadFlowInfos.builder()
                    .loadFlowStatus(LoadFlowStatus.CONVERGED)
                    .build()));

        //try to run a another loadflow
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode2Uuid)
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
                LoadFlowParameters.ConnectedComponentMode.MAIN,
                true))
            )
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, null);

        // getting setted values
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(LOAD_PARAMETERS_JSON2);

        // run loadflow with new parameters
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode2Uuid)
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, UPDATE_TYPE_LOADFLOW);

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // get default load flow provider
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(defaultLoadflowProvider);

        // set load flow provider
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .header("userId", "userId")
            .contentType(MediaType.TEXT_PLAIN)
            .body(BodyInserters.fromValue("Hades2"))
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, null, UPDATE_TYPE_LOADFLOW_STATUS);

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

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, null, UPDATE_TYPE_LOADFLOW_STATUS);

        // get default load flow provider again
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo(defaultLoadflowProvider);

        //run a loadflow on another node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode3Uuid)
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid, UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid, UPDATE_TYPE_LOADFLOW);

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID_2)));

        // check load flow status
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid, modificationNode3Uuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(LoadFlowInfos.class)
            .value(new MatcherLoadFlowInfos(LoadFlowInfos.builder()
                .loadFlowStatus(LoadFlowStatus.CONVERGED)
                .build()));
    }

    @Test
    public void testLoadFlowError() {
        UUID studyNameUserIdUuid = createStudy("userId", CASE_LOADFLOW_ERROR_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNodeUuid = modificationNode.getId();

        // run loadflow
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNodeUuid)
            .exchange()
            .expectStatus().isOk();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, UPDATE_TYPE_LOADFLOW);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=loadflow&overwrite=true&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // check load flow status
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid, modificationNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(LoadFlowInfos.class)
            .value(new MatcherLoadFlowInfos(LoadFlowInfos.builder()
                .loadFlowStatus(LoadFlowStatus.DIVERGED)
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

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, securityAnalysisUpdateMessage.getHeaders().get(HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));

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

        securityAnalysisStatusMessage = output.receive(TIMEOUT);
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
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run security analysis on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}", studyNameUserIdUuid, rootNodeUuid, CONTINGENCY_LIST_NAME)
                .exchange()
                .expectStatus().isForbidden();

        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID));
    }

    @Test
    public void testDiagramsAndGraphics() {
        //insert a study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
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

        // get the network area diagram
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram?&depth=0&voltageLevelsIds=vlFr1A", studyNameUserIdUuid, rootNodeUuid)
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("nad-svg");

        assertTrue(getRequestsDone(1).contains(String.format("/v1/network-area-diagram/" + NETWORK_UUID_STRING + "?depth=0&voltageLevelsIds=vlFr1A")));

        // get the network area diagram from a study that doesn't exist
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram?&depth=0&voltageLevelsIds=vlFr1A", randomUuid, rootNodeUuid)
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

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/lines", NETWORK_UUID_STRING)));

        //get the substation map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/substations", NETWORK_UUID_STRING)));

        //get the 2 windings transformers map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/2-windings-transformers", NETWORK_UUID_STRING)));

        //get the 3 windings transformers map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/3-windings-transformers/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/3-windings-transformers", NETWORK_UUID_STRING)));

        //get the generators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/generators", NETWORK_UUID_STRING)));

        //get the batteries map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/batteries/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/batteries", NETWORK_UUID_STRING)));

        //get the dangling lines map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/dangling-lines", NETWORK_UUID_STRING)));

        //get the hvdc lines map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/hvdc-lines", NETWORK_UUID_STRING)));

        //get the lcc converter stations map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/lcc-converter-stations", NETWORK_UUID_STRING)));

        //get the vsc converter stations map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/vsc-converter-stations", NETWORK_UUID_STRING)));

        //get the loads map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/loads", NETWORK_UUID_STRING)));

        //get the shunt compensators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/shunt-compensators", NETWORK_UUID_STRING)));

        //get the static var compensators map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/static-var-compensators", NETWORK_UUID_STRING)));

        //get all map data of a network
        webTestClient.get()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all/", studyNameUserIdUuid, rootNodeUuid)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/all", NETWORK_UUID_STRING)));

        // get the svg component libraries
        webTestClient.get()
            .uri("/v1/svg-component-libraries")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentType(MediaType.APPLICATION_JSON);

        assertTrue(getRequestsDone(1).contains("/v1/svg-component-libraries"));
    }

    @Test
    public void testNetworkModificationSwitch() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        // update switch on root node (not allowed)
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, rootNodeUuid, "switchId")
                .exchange()
                .expectStatus().isForbidden();

        // update switch on first modification node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, modificationNode1Uuid, "switchId")
            .exchange()
            .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, substationsSet);

        var requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update switch on second modification node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, modificationNode2Uuid, "switchId")
            .exchange()
            .expectStatus().isOk();
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, substationsSet);

        requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)));

        // test build status on switch modification
        modificationNode1.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode1 as built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        modificationNode2.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode2 as built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT);

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true", studyNameUserIdUuid, modificationNode1Uuid, "switchId")
            .exchange()
            .expectStatus().isOk();

        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);

        requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode1Uuid));  //  modificationNode1 is still built
        assertEquals(BuildStatus.BUILT_INVALID, networkModificationTreeService.getBuildStatus(modificationNode2Uuid));  // modificationNode2 is now invalid
    }

    @Test
    public void testGetLoadMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the load map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/{loadId}", studyNameUserIdUuid, rootNodeUuid, LOAD_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/loads/%s", NETWORK_UUID_STRING, LOAD_ID_1)));
    }

    @Test
    public void testGetLineMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the line map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/{lineId}", studyNameUserIdUuid, rootNodeUuid, LINE_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/lines/%s", NETWORK_UUID_STRING, LINE_ID_1)));
    }

    @Test
    public void testGetGeneratorMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the generator map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/{generatorId}", studyNameUserIdUuid, rootNodeUuid, GENERATOR_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/generators/%s", NETWORK_UUID_STRING, GENERATOR_ID_1)));
    }

    @Test
    public void testGet2wtMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the 2wt map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/{2wtId}", studyNameUserIdUuid, rootNodeUuid, TWO_WINDINGS_TRANSFORMER_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/2-windings-transformers/%s", NETWORK_UUID_STRING, TWO_WINDINGS_TRANSFORMER_ID_1)));
    }

    @Test
    public void testGetShuntCompensatorMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the shunt compensator map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/{shuntCompensatorId}", studyNameUserIdUuid, rootNodeUuid, SHUNT_COMPENSATOR_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/shunt-compensators/%s", NETWORK_UUID_STRING, SHUNT_COMPENSATOR_ID_1)));
    }

    @Test
    public void testGetSubstationMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the substation map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/{substationId}", studyNameUserIdUuid, rootNodeUuid, SUBSTATION_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/substations/%s", NETWORK_UUID_STRING, SUBSTATION_ID_1)));
    }

    @Test
    public void testGetVoltageLevelsMapServer() {
        //create study
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);

        //get the voltage level map data info of a network
        webTestClient.get()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}", studyNameUserIdUuid, rootNodeUuid, VL_ID_1)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody(String.class);

        assertTrue(getRequestsDone(1).contains(String.format("/v1/networks/%s/voltage-levels/%s", NETWORK_UUID_STRING, VL_ID_1)));
    }

    @SneakyThrows
    @Test
    public void testNetworkModificationEquipment() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNodeUuid);
        UUID modificationNodeUuid2 = modificationNode2.getId();

        //update equipment on root node (not allowed)
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid, rootNodeUuid)
                .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
                .exchange()
                .expectStatus().isForbidden();

        //update equipment
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid, modificationNodeUuid)
            .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .exchange()
            .expectStatus().isOk();

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")));

        webTestClient.get()
                .uri("/v1/studies")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(CreatedStudyBasicInfos.class)
                .value(studies -> studies.get(0),
                        createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update equipment on second modification node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid, modificationNodeUuid2)
            .body(BodyInserters.fromValue("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .exchange()
            .expectStatus().isOk();

        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*&variantId=" + VARIANT_ID)));
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

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid) {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), VARIANT_ID);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, UUID networkModificationUuid, String variantId) {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder()
            .name("hypo")
            .description("description")
            .networkModification(networkModificationUuid)
            .variantId(variantId)
            .loadFlowStatus(LoadFlowStatus.NOT_DONE)
            .buildStatus(BuildStatus.NOT_BUILT)
            .children(Collections.emptyList())
            .build();
        webTestClient.post().uri("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).bodyValue(modificationNode)
            .exchange()
            .expectStatus().isOk();
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(HEADER_INSERT_MODE));
        return modificationNode;
    }

    @SneakyThrows
    private UUID createStudy(String userId, UUID caseUuid, String... errorMessage) {
        BasicStudyInfos infos = webTestClient.post()
                .uri("/v1/studies/cases/{caseUuid}",
                        caseUuid)
                .header("userId", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .returnResult()
                .getResponseBody();

        UUID studyUuid = infos.getId();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT);  // message for first modification node creation

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message for deletion
        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(3);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", caseUuid)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/format", caseUuid)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));

        return studyUuid;
    }

    @SneakyThrows
    private UUID createStudy(String userId, String fileName, String caseUuid, boolean isPrivate, String... errorMessage) {
        final UUID studyUuid;
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + fileName))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", fileName, "text/xml", is);

            MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
            bodyBuilder.part("caseFile", mockFile.getBytes())
                .filename(fileName)
                .contentType(MediaType.TEXT_XML);

            BasicStudyInfos infos = webTestClient.post()
                .uri(STUDIES_URL + "?isPrivate={isPrivate}", isPrivate)
                .header("userId", userId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .returnResult()
                .getResponseBody();

            studyUuid = infos.getId();
        }

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        if (errorMessage.length == 0) {
            output.receive(TIMEOUT);   // message for first modification node creation
        }

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message for deletion
        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(caseUuid == null ? 1 : 3);
        assertTrue(requests.contains("/v1/cases/private"));
        if (caseUuid != null) {
            assertTrue(requests.contains(String.format("/v1/cases/%s/format", caseUuid)));
            assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
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
                    .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId"));
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
                        createMatcherStudyBasicInfos(studyUuid, "userId"));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(TIMEOUT);

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
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM"));

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT);
        // drop the broker message for study creation
        output.receive(TIMEOUT);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT);

        // assert that all http requests have been sent to remote services
        var httpRequests = getRequestsDone(3);
        assertTrue(httpRequests.contains("/v1/cases/private"));
        assertTrue(httpRequests.contains(String.format("/v1/cases/%s/format", IMPORTED_BLOCKING_CASE_UUID_STRING)));
        assertTrue(httpRequests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));

        countDownLatch = new CountDownLatch(1);

        //insert a study
        webTestClient.post()
                .uri("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}", NEW_STUDY_CASE_UUID, "false")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectBody(BasicStudyInfos.class)
                .value(createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId"));

        studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        webTestClient.get()
                .uri("/v1/study_creation_requests")
                .header("userId", "userId")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBodyList(BasicStudyInfos.class)
                .value(requests -> requests.get(0),
                        createMatcherStudyBasicInfos(studyUuid, "userId"));

        countDownLatch.countDown();

        // Study import is asynchronous, we have to wait because our code doesn't allow block until the study creation processing is done
        Thread.sleep(TIMEOUT);

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
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM"));

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT);
        // drop the broker message for study creation
        output.receive(TIMEOUT);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT);

        // assert that all http requests have been sent to remote services
        var requests = getRequestsDone(3);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains(String.format("/v1/cases/%s/format", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    @Test
    public void testUpdateLines() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        // change line status on root node (not allowed)
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, rootNodeUuid, "line12")
                .bodyValue("lockout")
                .exchange()
                .expectStatus().isForbidden();

        // lockout line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "line12")
            .bodyValue("lockout")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s2"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId")
            .bodyValue("lockout")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // trip line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "line23")
            .bodyValue("trip")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId")
            .bodyValue("trip")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // energise line end
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "line13")
            .bodyValue("energiseEndOne")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId")
            .bodyValue("energiseEndTwo")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // switch on line
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "line13")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isOk();

        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        // switch on line on second modification node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status", studyNameUserIdUuid, modificationNode2Uuid, "line13")
            .bodyValue("switchOn")
            .exchange()
            .expectStatus().isOk();
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s1", "s3"));

        var requests = getRequestsWithBodyDone(9);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("energiseEndOne")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("energiseEndTwo")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals("switchOn")));
    }

    @Test
    public void testCreateLoad() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createLoadAttributes = "{\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        // create load on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createLoadAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create load on first modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, modificationNode1Uuid)
                .bodyValue(createLoadAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));

        // create load on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, modificationNode2Uuid)
                .bodyValue(createLoadAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));

        // update load creation
        String loadAttributesUpdated = "{\"loadId\":\"loadId2\",\"loadName\":\"loadName2\",\"loadType\":\"UNDEFINED\",\"activePower\":\"50.0\",\"reactivePower\":\"25.0\",\"voltageLevelId\":\"idVL2\",\"busId\":\"idBus2\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(loadAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*") && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/loads-creation") && r.getBody().equals(loadAttributesUpdated)));
    }

    @Test
    public void testModifyLoad() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNodeUuid2 = modificationNode2.getId();

        String loadModificationAttributes = "{\"equipmentId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"AUXILIARY\",\"activePower\":\"100.0\"}";

        // modify load on root node (not allowed)
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(loadModificationAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // modify load on first modification node
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, modificationNodeUuid)
                .bodyValue(loadModificationAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));

        // modify load on second modification node
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", studyNameUserIdUuid, modificationNodeUuid2)
                .bodyValue(loadModificationAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, ImmutableSet.of("s2"));

        // update load modification
        String loadAttributesUpdated = "{\"loadId\":\"loadId1\",\"loadType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-modification", studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                .bodyValue(loadAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*") && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/loads-modification") && r.getBody().equals(loadAttributesUpdated)));
    }

    @Test
    public void testCreateSubstation() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createSubstationAttributes = "{\"substationId\":\"substationId1\",\"substationName\":\"substationName1\",\"country\":\"AD\"}";

        // create substation on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createSubstationAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create substation on first modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations", studyNameUserIdUuid, modificationNode1Uuid)
            .bodyValue(createSubstationAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());

        // create substation on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations", studyNameUserIdUuid, modificationNode2Uuid)
            .bodyValue(createSubstationAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());

        // update substation creation
        String substationAttributesUpdated = "{\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"FR\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/substations-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(substationAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*") && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/substations-creation") && r.getBody().equals(substationAttributesUpdated)));
    }

    @Test
    public void testCreateVoltageLevel() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createVoltageLevelAttributes = "{\"voltageLevelId\":\"voltageLevelId1\",\"voltageLevelName\":\"voltageLevelName1\""
            + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s1\"}";

        // create voltage level on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createVoltageLevelAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create voltage level
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels", studyNameUserIdUuid, modificationNode1Uuid)
            .bodyValue(createVoltageLevelAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());

        // create voltage level on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels", studyNameUserIdUuid, modificationNode2Uuid)
            .bodyValue(createVoltageLevelAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());

        // update voltage level creation
        String voltageLevelAttributesUpdated = "{\"voltageLevelId\":\"voltageLevelId2\",\"voltageLevelName\":\"voltageLevelName2\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/voltage-levels-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(voltageLevelAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*") && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/voltage-levels-creation") && r.getBody().equals(voltageLevelAttributesUpdated)));
    }

    @SneakyThrows
    @Test
    public void testLineSplitWithVoltageLevel() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid);
        UUID modificationNodeUuid = modificationNode.getId();

        VoltageLevelCreationInfos vl1 = VoltageLevelCreationInfos.builder()
            .equipmentId("vl1")
            .equipmentName("NewVoltageLevel")
            .nominalVoltage(379.3)
            .substationId("s1")
            .busbarSections(Collections.singletonList(new BusbarSectionCreationInfos("v1bbs", "BBS1", 1, 1)))
            .busbarConnections(Collections.emptyList())
            .build();
        LineSplitWithVoltageLevelInfos lineSplitWoVL = new LineSplitWithVoltageLevelInfos("line3", 10.0, vl1, null, "1.A",
            "nl1", "NewLine1", "nl2", "NewLine2");
        String lineSplitWoVLasJSON = mapper.writeValueAsString(lineSplitWoVL);

        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-splits",
                studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue(lineSplitWoVLasJSON)
            .exchange()
            .expectStatus().isOk();

        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s2"));

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-splits",
                studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
            .bodyValue(lineSplitWoVLasJSON)
            .exchange()
            .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(2);
        assertEquals(2, requests.size());
        Optional<RequestWithBody> creationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-splits\\?group=.*")).findFirst();
        Optional<RequestWithBody> updateRequest = requests.stream().filter(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/line-splits")).findFirst();
        assertTrue(creationRequest.isPresent());
        assertTrue(updateRequest.isPresent());
        assertEquals(lineSplitWoVLasJSON, creationRequest.get().getBody());
        assertEquals(lineSplitWoVLasJSON, updateRequest.get().getBody());

        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-splits",
                studyNameUserIdUuid, modificationNodeUuid)
            .bodyValue("bogus")
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody(String.class).isEqualTo("400 BAD_REQUEST");

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-splits",
                studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
            .bodyValue("bogus")
            .exchange()
            .expectStatus().is5xxServerError()
            .expectBody(String.class).isEqualTo("400 BAD_REQUEST");

        requests = getRequestsWithBodyDone(2);
    }

    @Test public void testReorderModification() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNodeUuid = modificationNode.getId();

        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, UUID.randomUUID(), modification1, modification2)
            .exchange()
            .expectStatus().isNotFound();

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                UUID.randomUUID(), modificationNodeUuid, modification1, modification2)
            .exchange()
            .expectStatus().isForbidden();

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}",
                studyNameUserIdUuid, modificationNodeUuid, modification1, modification2)
            .exchange()
            .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/groups/" +
            modificationNode.getNetworkModification()
            + "/modifications/move[?]modificationsToMove=.*" + modification1)));

        // update switch on first modification node
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, modificationNodeUuid, modification1, modification2)
            .exchange()
            .expectStatus().isOk();

        requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/groups/" +
            modificationNode.getNetworkModification()
            + "/modifications/move[?]modificationsToMove=.*" + modification1 + ".*&before=" + modification2)));

    }

    @Test
    public void testDeleteEquipment() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        // delete equipment on root node (not allowed)
        webTestClient.delete()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                        studyNameUserIdUuid, rootNodeUuid, "LOAD", "idLoadToDelete")
                .exchange()
                .expectStatus().isForbidden();

        // delete equipment on first modification node
        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, modificationNode1Uuid, "LOAD", "idLoadToDelete")
            .exchange()
            .expectStatus().isOk();
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete",
            HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, "LOAD", HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));

        // delete equipment on second modification node
        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, modificationNode2Uuid, "LOAD", "idLoadToDelete")
            .exchange()
            .expectStatus().isOk();
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete",
            HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, "LOAD", HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*&variantId=" + VARIANT_ID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*&variantId=" + VARIANT_ID_2)));
    }

    private void checkEquipmentDeletedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeEquipmentType, String equipmentType,
                                                       String headerUpdateTypeEquipmentId, String equipmentId, String headerUpdateTypeSubstationsIds, Set<String> modifiedSubstationsIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_STUDY, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(equipmentType, headersStudyUpdate.get(headerUpdateTypeEquipmentType));
        assertEquals(equipmentId, headersStudyUpdate.get(headerUpdateTypeEquipmentId));
        assertEquals(modifiedSubstationsIdsSet, headersStudyUpdate.get(headerUpdateTypeSubstationsIds));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));

        // assert that the broker message has been sent for updating load flow status
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(StudyService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
    }

    private void checkUpdateNodesMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NetworkModificationTreeService.HEADER_NODES));
        assertEquals(NetworkModificationTreeService.NODE_UPDATED, headersStatus.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeId, Set<String> modifiedIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_STUDY, headersStudyUpdate.get(StudyService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedIdsSet, headersStudyUpdate.get(headerUpdateTypeId));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedIdsSet);
    }

    private void checkEquipmentModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedIdsSet);
    }

    private void checkLineModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageLine = output.receive(TIMEOUT);
        assertEquals("", new String(messageLine.getPayload()));
        MessageHeaders headersSwitch = messageLine.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(HEADER_NODE));
        assertEquals(StudyService.UPDATE_TYPE_LINE, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
    }

    private void checkSwitchModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, StudyService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(TIMEOUT);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(StudyService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_SWITCH, headersSwitch.get(StudyService.HEADER_UPDATE_TYPE));
    }

    @Test
    public void testGetBusesOrBusbarSections() {
        createStudy("userId", CASE_UUID);
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
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createGeneratorAttributes = "{\"generatorId\":\"generatorId1\",\"generatorName\":\"generatorName1\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create generator on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createGeneratorAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create generator on first modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators", studyNameUserIdUuid, modificationNode1Uuid)
            .bodyValue(createGeneratorAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));

        // create generator on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators", studyNameUserIdUuid, modificationNode2Uuid)
            .bodyValue(createGeneratorAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));

        // update generator creation
        String generatorAttributesUpdated = "{\"generatorId\":\"generatorId2\",\"generatorName\":\"generatorName2\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"150.0\",\"maxActivePower\":\"50.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/generators-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(generatorAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*") && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/generators-creation") && r.getBody().equals(generatorAttributesUpdated)));
    }

    @Test
    public void testCreateShuntsCompensator() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();

        String createShuntCompensatorAttributes = "{\"shuntCompensatorId\":\"shuntCompensatorId1\",\"shuntCompensatorName\":\"shuntCompensatorName1\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create suntCompensator on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createShuntCompensatorAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create suntCompensator on modification node child of root node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators", studyNameUserIdUuid, modificationNode1Uuid)
            .bodyValue(createShuntCompensatorAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));

        // update shunt compensator creation
        String shuntCompensatorAttributesUpdated = "{\"shuntCompensatorId\":\"shuntCompensatorId2\",\"shuntCompensatorName\":\"shuntCompensatorName2\",\"voltageLevelId\":\"idVL2\",\"busOrBusbarSectionId\":\"idBus1\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/shunt-compensators-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(shuntCompensatorAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(2);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators[?]group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createShuntCompensatorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/shunt-compensators-creation") && r.getBody().equals(shuntCompensatorAttributesUpdated)));
    }

    @Test
    public void testCreateLine() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

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

        // create line on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createLineAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create line on first modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines", studyNameUserIdUuid, modificationNode1Uuid)
            .bodyValue(createLineAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));

        // create line on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines", studyNameUserIdUuid, modificationNode2Uuid)
            .bodyValue(createLineAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));

        // update line creation
        String lineAttributesUpdated = "{" +
                "\"lineId\":\"lineId2\"," +
                "\"lineName\":\"lineName2\"," +
                "\"seriesResistance\":\"54.0\"," +
                "\"seriesReactance\":\"55.0\"," +
                "\"shuntConductance1\":\"100.0\"," +
                "\"shuntSusceptance1\":\"100.0\"," +
                "\"shuntConductance2\":\"200.0\"," +
                "\"shuntSusceptance2\":\"200.0\"," +
                "\"voltageLevelId1\":\"idVL2\"," +
                "\"busOrBusbarSectionId1\":\"idBus1\"," +
                "\"voltageLevelId2\":\"idVL2\"," +
                "\"busOrBusbarSectionId2\":\"idBus2\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(lineAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*") && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/lines-creation") && r.getBody().equals(lineAttributesUpdated)));
    }

    @Test
    public void testCreateTwoWindingsTransformer() {
        createStudy("userId", CASE_UUID);
        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        // create 2WT on root node (not allowed)
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers", studyNameUserIdUuid, rootNodeUuid)
                .bodyValue(createTwoWindingsTransformerAttributes)
                .exchange()
                .expectStatus().isForbidden();

        // create 2WT on first modification node
        webTestClient.post()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers", studyNameUserIdUuid, modificationNode1Uuid)
                .bodyValue(createTwoWindingsTransformerAttributes)
                .exchange()
                .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));

        // create 2WT on second modification node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers", studyNameUserIdUuid, modificationNode2Uuid)
            .bodyValue(createTwoWindingsTransformerAttributes)
            .exchange()
            .expectStatus().isOk();
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));

        // update Two Windings Transformer creation
        String twoWindingsTransformerAttributesUpdated = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        webTestClient.put()
                .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/two-windings-transformers-creation", studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .bodyValue(twoWindingsTransformerAttributesUpdated)
                .exchange()
                .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(3);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/two-windings-transformers-creation") && r.getBody().equals(twoWindingsTransformerAttributesUpdated)));
    }

    private void testBuildWithNodeUuid(UUID studyUuid, UUID nodeUuid) {
        // build node
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk();

        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(NODE_UPDATED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_BUILD_COMPLETED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.BUILDING, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is building

        // stop build
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build/stop", studyUuid, nodeUuid)
            .exchange()
            .expectStatus().isOk();

        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(NODE_UPDATED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT);
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(HEADER_NODE));
        assertEquals(UPDATE_TYPE_BUILD_CANCELLED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is not built

        assertTrue(getRequestsDone(1).stream().anyMatch(r -> r.matches("/v1/build/stop\\?receiver=.*")));
    }

    @Test
    public void testBuild() {
        UUID studyNameUserIdUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, modificationGroupUuid1, "variant_1");
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1.getId(), modificationGroupUuid2, "variant_2");
        UUID modificationGroupUuid3 = UUID.randomUUID();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2.getId(), modificationGroupUuid3, "variant_3");
        UUID modificationGroupUuid4 = UUID.randomUUID();
        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode3.getId(), modificationGroupUuid4, "variant_4");
        UUID modificationGroupUuid5 = UUID.randomUUID();
        NetworkModificationNode modificationNode5 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode4.getId(), modificationGroupUuid5, "variant_5");

        /*
            root
             |
          modificationNode1
             |
          modificationNode2
             |
          modificationNode3
             |
          modificationNode4
             |
          modificationNode5
         */

        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(modificationNode5.getId());
        assertNull(buildInfos.getOriginVariantId());  // previous built node is root node
        assertEquals("variant_5", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid1, modificationGroupUuid2, modificationGroupUuid3, modificationGroupUuid4, modificationGroupUuid5), buildInfos.getModificationGroupUuids());

        modificationNode3.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT);

        buildInfos = networkModificationTreeService.getBuildInfos(modificationNode4.getId());
        assertEquals("variant_3", buildInfos.getOriginVariantId());  // variant to clone is variant associated to node modificationNode3
        assertEquals("variant_4", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid4), buildInfos.getModificationGroupUuids());

        modificationNode2.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode2 as not built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT);
        modificationNode4.setBuildStatus(BuildStatus.BUILT_INVALID);  // mark node modificationNode4 as built invalid
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode4);
        output.receive(TIMEOUT);
        modificationNode5.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode5 as built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode5);
        output.receive(TIMEOUT);

        // build modificationNode2 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId());

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode3.getId()));
        assertEquals(BuildStatus.BUILT_INVALID, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));

        modificationNode3.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.doUpdateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT);

        // build modificationNode3 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode3.getId());

        assertEquals(BuildStatus.BUILT_INVALID, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));
    }

    @Test
    public void testChangeModificationActiveState() {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, modificationGroupUuid1, "variant_1");

        UUID modificationUuid = UUID.randomUUID();
        UUID nodeNotFoundUuid = UUID.randomUUID();

        // deactivate modification on modificationNode
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false", studyUuid, nodeNotFoundUuid, modificationUuid)
            .exchange()
            .expectStatus().isNotFound();  // node not found

        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false", studyUuid, modificationNode1.getId(), modificationUuid)
            .exchange()
            .expectStatus().isOk();

        AtomicReference<AbstractNode> node = new AtomicReference<>();
        networkModificationTreeService.getSimpleNode(studyUuid, modificationNode1.getId()).subscribe(node::set);
        NetworkModificationNode modificationNode = (NetworkModificationNode) node.get();
        assertEquals(Set.of(modificationUuid), modificationNode.getModificationsToExclude());

        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());

        // reactivate modification
        webTestClient.put()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=true", studyUuid, modificationNode1.getId(), modificationUuid)
            .exchange()
            .expectStatus().isOk();

        networkModificationTreeService.getSimpleNode(studyUuid, modificationNode1.getId()).subscribe(node::set);
        modificationNode = (NetworkModificationNode) node.get();
        assertTrue(modificationNode.getModificationsToExclude().isEmpty());

        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());
    }

    @Test
    public void deleteModificationRequest() {
        createStudy("userId", CASE_UUID);
        UUID studyUuid = studyRepository.findAll().get(0).getId();
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid);
        createNetworkModificationNode(studyUuid, rootNodeUuid);
        NetworkModificationNode node3 = createNetworkModificationNode(studyUuid, modificationNode.getId());
        /*  root
           /   \
         node  modification node
                 \
                node3
            node is only there to test that when we update modification node, it is not in notifications list
         */

        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification?modificationsUuids={modificationUuid}", UUID.randomUUID(), modificationNode.getId(), node3.getId())
            .exchange()
            .expectStatus().isForbidden();

        UUID modificationUuid = UUID.randomUUID();
        webTestClient.delete()
            .uri("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification?modificationsUuids={modificationUuid}", studyUuid, modificationNode.getId(), modificationUuid)
            .exchange()
            .expectStatus().isOk();
        assertTrue(getRequestsDone(1).stream().anyMatch(
            r -> r.matches("/v1/groups/" + modificationNode.getNetworkModification() + "/modifications[?]modificationsUuids=.*" + modificationUuid + ".*"))
        );

        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode.getId());
    }

    @Test
    public void testCreateStudyWithDefaultLoadflow() {
        createStudy("userId", CASE_UUID);
        StudyEntity study = studyRepository.findAll().get(0);

        assertEquals(study.getLoadFlowProvider(), defaultLoadflowProvider);
    }

    @Test public void getDefaultLoadflowProvider() {
        String defaultLoadflowProviderResponse = webTestClient.get()
            .uri("/v1/loadflow-default-provider")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .returnResult().getResponseBody();

        assertEquals(defaultLoadflowProviderResponse, defaultLoadflowProvider);
    }

    @Test
    public void reindexStudyTest() {
        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/reindex-all", UUID.randomUUID())
            .exchange()
            .expectStatus().isNotFound();

        UUID study1Uuid = createStudy("userId", CASE_UUID);

        webTestClient.post()
            .uri("/v1/studies/{studyUuid}/reindex-all", study1Uuid)
            .exchange()
            .expectStatus().isOk();

        var requests = getRequestsWithBodyDone(1);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().contains("/v1/networks/" + NETWORK_UUID_STRING + "/reindex-all")));
    }

    @After
    public void tearDown() {
        cleanDB();

        Set<String> httpRequest = null;
        Message<byte[]> message = null;
        try {
            httpRequest = getRequestsDone(1);
            message = output.receive(TIMEOUT);
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
