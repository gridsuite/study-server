/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.commons.reporter.ReporterModelJsonModule;
import com.powsybl.iidm.network.*;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NotificationService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.SecurityAnalysisService;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.MatcherReport;
import org.gridsuite.study.server.utils.RequestWithBody;
import org.gridsuite.study.server.utils.TestUtils;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.ResourceUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class StudyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    @Autowired
    private MockMvc mockMvc;

    private static final String FIRST_VARIANT_ID = "first_variant_id";

    private static final long TIMEOUT = 1000;
    private static final String STUDIES_URL = "/v1/studies";
    private static final String TEST_FILE_UCTE = "testCase.ucte";
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
    private static final String DUPLICATED_STUDY_UUID = "11888888-0000-0000-0000-111111111111";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_UUID = UUID.fromString(IMPORTED_CASE_UUID_STRING);
    private static final UUID IMPORTED_CASE_WITH_ERRORS_UUID = UUID.fromString(IMPORTED_CASE_WITH_ERRORS_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");
    private static final ReporterModel REPORT_TEST_ROOT_NODE = new ReporterModel("test", "Root");
    private static final String VARIANT_ID = "variant_1";
    public static final String POST = "POST";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";
    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final String CASE_UUID_CAUSING_IMPORT_ERROR = "178719f5-cccc-48be-be46-e92345951e32";
    private static final String CASE_UUID_CAUSING_STUDY_CREATION_ERROR = "278719f5-cccc-48be-be46-e92345951e32";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";
    private static final NetworkInfos NETWORK_INFOS_2 = new NetworkInfos(UUID.fromString(NETWORK_UUID_2_STRING), "file_2.xiidm");
    private static final NetworkInfos NETWORK_INFOS_3 = new NetworkInfos(UUID.fromString(NETWORK_UUID_3_STRING), "file_3.xiidm");
    private static final String CASE_NAME = "DefaultCaseName";

    private static final String STUDY_CREATION_ERROR_MESSAGE = "Une erreur est survenue lors de la création de l'étude";

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private CaseService caseService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @MockBean
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private StudyInfosService studyInfosService;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    private List<EquipmentInfos> linesInfos;

    private List<CreatedStudyBasicInfos> studiesInfos;

    private MockWebServer server;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    //used by testGetStudyCreationRequests to control asynchronous case import
    CountDownLatch countDownLatch;

    @MockBean
    private NetworkStoreService networkStoreService;

    //output destinations
    private String studyUpdateDestination = "study.update";

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
                CreatedStudyBasicInfos.builder().id(UUID.fromString(DUPLICATED_STUDY_UUID)).userId("userId1").caseFormat("XIIDM").creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build(),
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111112")).userId("userId1").caseFormat("UCTE").creationDate(ZonedDateTime.now(ZoneOffset.UTC)).build()
        );

        when(studyInfosService.add(any(CreatedStudyBasicInfos.class))).thenReturn(studiesInfos.get(0));
        when(studyInfosService.search(String.format("userId:%s", "userId")))
                .then((Answer<List<CreatedStudyBasicInfos>>) invocation -> studiesInfos);

        when(equipmentInfosService.searchEquipments(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND equipmentName.fullascii:(*B*)", NETWORK_UUID_STRING, VariantManagerConstants.INITIAL_VARIANT_ID)))
            .then((Answer<List<EquipmentInfos>>) invocation -> linesInfos);

        when(equipmentInfosService.searchEquipments(String.format("networkUuid.keyword:(%s) AND variantId.keyword:(%s) AND equipmentId.fullascii:(*B*)", NETWORK_UUID_STRING, VariantManagerConstants.INITIAL_VARIANT_ID)))
            .then((Answer<List<EquipmentInfos>>) invocation -> linesInfos);

        when(networkStoreService.cloneNetwork(NETWORK_UUID, Collections.emptyList())).thenReturn(network);
        when(networkStoreService.getNetworkUuid(network)).thenReturn(NETWORK_UUID);
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenReturn(network);

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

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        caseService.setCaseServerBaseUri(baseUrl);
        networkConversionService.setNetworkConversionServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
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

        String importedCaseWithErrorsUuidAsString = mapper.writeValueAsString(IMPORTED_CASE_WITH_ERRORS_UUID);
        String importedBlockingCaseUuidAsString = mapper.writeValueAsString(IMPORTED_BLOCKING_CASE_UUID_STRING);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID) ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups\\?duplicateFrom=.*&groupUuid=.*&reportUuid=.*")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    }
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
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads(-modification)?\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/.*/reindex-all")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS, "UCTE");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_2_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS_2, "UCTE");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_3_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS_3, "UCTE");
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    // need asynchronous run to get study creation requests
                    new Thread(() -> {
                        try {
                            countDownLatch.await();
                            sendCaseImportSucceededMessage(path, NETWORK_INFOS, "XIIDM");
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportFailedMessage(path, STUDY_CREATION_ERROR_MESSAGE);
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/reports/.*")) {
                    return new MockResponse().setResponseCode(200).setBody(mapper.writeValueAsString(REPORT_TEST))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    // need asynchronous run to get study creation requests
                    new Thread(() -> {
                        try {
                            countDownLatch.await();
                            sendCaseImportSucceededMessage(path, NETWORK_INFOS, "XIIDM");
                        } catch (Exception e) {
                            System.err.println(e);
                        }
                    }).start();
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS, "XIIDM");
                    return new MockResponse().setResponseCode(200);
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
                    case "/v1/cases/" + CASE_UUID_CAUSING_IMPORT_ERROR + "/exists":
                    case "/v1/cases/" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("true")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + CASE_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + CASE_UUID_STRING + "\",\"name\":\"" + TEST_FILE_UCTE + "\",\"format\":\"UCTE\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "\",\"name\":\"" + TEST_FILE_IMPORT_ERRORS + "\",\"format\":\"XIIDM\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + IMPORTED_CASE_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + NEW_STUDY_CASE_UUID + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + IMPORTED_BLOCKING_CASE_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + CASE_2_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}")
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/infos":
                        return new MockResponse().setResponseCode(200)
                                .setBody("{\"uuid\":\"" + CASE_3_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}")
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
                        return new MockResponse().setResponseCode(200).setBody("XIIDM")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse().setResponseCode(200).setBody("false")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/" + CASE_API_VERSION + "/cases/private": {
                        String bodyStr = body.readUtf8();
                        if (bodyStr.contains("filename=\"")) {
                            String bodyFilename = bodyStr.split(System.lineSeparator())[1].split("\r")[0];
                            if (bodyFilename.matches(".*filename=\".*" + TEST_FILE_WITH_ERRORS + "\".*")) {  // import file with errors
                                return new MockResponse().setResponseCode(200).setBody(importedCaseWithErrorsUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                            } else if (bodyFilename.matches(".*filename=\".*" + TEST_FILE_IMPORT_ERRORS + "\"")) {  // import file with errors during import in the case server
                                return new MockResponse().setResponseCode(500)
                                    .addHeader("Content-Type", "application/json; charset=utf-8")
                                    .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                            } else if (bodyFilename.matches(".*filename=\".*" + TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY + "\"")) {  // import file with errors during import in the case server without message in response body
                                return new MockResponse().setResponseCode(500)
                                    .addHeader("Content-Type", "application/json; charset=utf-8")
                                    .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
                            } else if (bodyFilename.matches(".*filename=\".*blockingCaseFile\".*")) {
                                return new MockResponse().setResponseCode(200).setBody(importedBlockingCaseUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                            } else {
                                return new MockResponse().setResponseCode(200).setBody(importedCaseUuidAsString)
                                    .addHeader("Content-Type", "application/json; charset=utf-8");
                            }
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
                    case "/v1/networks?caseUuid=" + CASE_UUID_CAUSING_IMPORT_ERROR + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setResponseCode(500);
                    case "/v1/networks?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID:
                        sendCaseImportFailedMessage(path, "ERROR WHILE IMPORTING STUDY");
                        return new MockResponse().setResponseCode(200);
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse().setBody(String.valueOf(networkInfosAsString)).setResponseCode(500)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");

                    case "/v1/reports/" + NETWORK_UUID_STRING:
                        return new MockResponse().setResponseCode(200)
                            .setBody(mapper.writeValueAsString(REPORT_TEST))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

                    case "/v1/export/formats":
                        return new MockResponse().setResponseCode(200).setBody("[\"CGMES\",\"UCTE\",\"XIIDM\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM":
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM" + "?variantId=" + VARIANT_ID:
                        return new MockResponse().setResponseCode(200).addHeader("Content-Disposition", "attachment; filename=fileName").setBody("byteData")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/status":
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID_2:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID_3:
                        return new MockResponse().setResponseCode(200);
                    default:
                        LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                        return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    private void sendCaseImportSucceededMessage(String requestPath, NetworkInfos networkInfos, String format) {
        Pattern receiverPattern = Pattern.compile("receiver=(.*)");
        Matcher matcher = receiverPattern.matcher(requestPath);
        if (matcher.find()) {
            String receiverUrlString = matcher.group(1);
            input.send(MessageBuilder.withPayload("").setHeader("receiver", URLDecoder.decode(receiverUrlString, StandardCharsets.UTF_8))
                    .setHeader("networkUuid", networkInfos.getNetworkUuid().toString())
                    .setHeader("networkId", networkInfos.getNetworkId())
                    .setHeader("caseFormat", format)
                    .build(), "case.import.succeeded");
        }
    }

    private void sendCaseImportFailedMessage(String requestPath, String errorMessage) {
        Pattern receiverPattern = Pattern.compile("receiver=(.*)");
        Matcher matcher = receiverPattern.matcher(requestPath);
        if (matcher.find()) {
            String receiverUrlString = matcher.group(1);
            input.send(MessageBuilder.withPayload("").setHeader("receiver", URLDecoder.decode(receiverUrlString, StandardCharsets.UTF_8))
                    .setHeader("errorMessage", errorMessage)
                    .build(), "case.import.failed");
        }
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    @Test
    public void testSearch() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeId = getRootNodeUuid(studyUuid);

        mvcResult = mockMvc
                .perform(get("/v1/search?q={request}", String.format("userId:%s", "userId")).header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString,
                new TypeReference<List<CreatedStudyBasicInfos>>() {
                });
        assertThat(createdStudyBasicInfosList, new MatcherJson<>(mapper, studiesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=name",
                        studyUuid, rootNodeId, "B").header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<EquipmentInfos> equipmentInfos = mapper.readValue(resultAsString,
                new TypeReference<List<EquipmentInfos>>() {
                });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME",
                        studyUuid, rootNodeId, "B").header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<List<EquipmentInfos>>() {
        });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=ID",
                        studyUuid, rootNodeId, "B").header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<List<EquipmentInfos>>() {
        });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=bogus",
                        studyUuid, rootNodeId, "B").header("userId", "userId"))
                .andExpectAll(status().isBadRequest(),
                        content().string("Enum unknown entry 'bogus' should be among NAME, ID"))
                .andReturn();
    }

    @Test
    public void test() throws Exception {
        MvcResult result;
        String resultAsString;

        //empty list
        mockMvc.perform(get("/v1/studies").header("userId", "userId")).andExpectAll(status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //empty list
        mockMvc.perform(get("/v1/study_creation_requests").header("userId", "userId")).andExpectAll(status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //insert a study
        UUID studyUuid = createStudy("userId", CASE_UUID);

        // check the study
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", studyUuid).header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        StudyInfos infos = mapper.readValue(resultAsString, StudyInfos.class);

        assertThat(infos, createMatcherStudyInfos(studyUuid, "userId", "UCTE"));

        //insert a study with a non existing case and except exception
        result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}",
                NOT_EXISTING_CASE_UUID, "false").header("userId", "userId"))
                .andExpectAll(status().isFailedDependency(), content().contentType(MediaType.valueOf("text/plain;charset=UTF-8"))).andReturn();
        assertEquals("The case '" + NOT_EXISTING_CASE_UUID + "' does not exist", result.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/cases/%s/exists", NOT_EXISTING_CASE_UUID)));

        result = mockMvc.perform(get("/v1/studies").header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString,
                new TypeReference<List<CreatedStudyBasicInfos>>() {
                });

        assertThat(createdStudyBasicInfosList.get(0), createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "UCTE"));

        //insert the same study but with another user (should work)
        //even with the same name should work
        studyUuid = createStudy("userId2", CASE_UUID);

        resultAsString = mockMvc.perform(get("/v1/studies").header("userId", "userId2"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();

        createdStudyBasicInfosList = mapper.readValue(resultAsString,
                new TypeReference<List<CreatedStudyBasicInfos>>() {
                });

        assertThat(createdStudyBasicInfosList.get(0),
                        createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE"));

        //insert a study with a case (multipartfile)
        UUID s2Uuid = createStudy("userId", TEST_FILE, IMPORTED_CASE_UUID_STRING, true);

        // check the study s2
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", s2Uuid).header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        StudyInfos studyInfos = mapper.readValue(resultAsString, StudyInfos.class);

        assertThat(studyInfos, createMatcherStudyInfos(s2Uuid, "userId", "XIIDM"));

        UUID randomUuid = UUID.randomUUID();
        //get a non existing study -> 404 not found
        mockMvc.perform(get("/v1/studies/{studyUuid}", randomUuid).header("userId", "userId"))
            .andExpectAll(status().isNotFound(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$").value(STUDY_NOT_FOUND.name()));

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        //delete existing study s2
        mockMvc.perform(delete("/v1/studies/{studyUuid}", s2Uuid).header("userId", "userId"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals("userId", headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(s2Uuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        var httpRequests = TestUtils.getRequestsDone(3, server);
        assertTrue(httpRequests.stream().anyMatch(r -> r.matches("/v1/groups/.*")));
        assertEquals(2, httpRequests.stream().filter(p -> p.matches("/v1/reports/.*")).count());

        // expect only 1 study (public one) since the other is private and we use
        // another userId
        result = mockMvc.perform(get("/v1/studies").header("userId", "a"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        createdStudyBasicInfosList = mapper.readValue(resultAsString,
                new TypeReference<List<CreatedStudyBasicInfos>>() {
                });
        assertEquals(2, createdStudyBasicInfosList.size());

        //get available export format
        mockMvc.perform(get("/v1/export-network-formats")).andExpectAll(status().isOk(),
                content().string("[\"CGMES\",\"UCTE\",\"XIIDM\"]"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains("/v1/export/formats"));

        //export a network
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, rootNodeUuid, "XIIDM"))
                .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/export/XIIDM", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}?formatParameters=%7B%22iidm.export.xml.indent%22%3Afalse%7D", studyNameUserIdUuid, rootNodeUuid, "XIIDM"))
            .andExpect(status().isOk());
        TestUtils.getRequestsDone(1, server); // just consume it

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 3");
        UUID modificationNode1Uuid = modificationNode1.getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, modificationNode1Uuid, "XIIDM"))
            .andExpect(status().isInternalServerError());

        modificationNode1.setBuildStatus(BuildStatus.BUILT);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/export-network/{format}", studyNameUserIdUuid, modificationNode1Uuid, "XIIDM"))
            .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/export/XIIDM?variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));
    }

    @Test
    public void testCreateStudyWithImportParameters() throws Exception {
        HashMap<String, Object> importParameters = new HashMap<String, Object>();
        ArrayList<String> randomListParam = new ArrayList<String>();
        randomListParam.add("paramValue1");
        randomListParam.add("paramValue2");
        importParameters.put("randomListParam", randomListParam);
        importParameters.put("randomParam2", "randomParamValue");

        createStudyWithImportParameters("userId", CASE_UUID, importParameters);
    }

    @Test
    public void testMetadata() throws Exception {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudy("userId2", CASE_UUID);

        MvcResult mvcResult = mockMvc
                .perform(get("/v1/studies/metadata?ids="
                        + Stream.of(oldStudyUuid, studyUuid).map(Object::toString).collect(Collectors.joining(",")))
                        .header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString,
                new TypeReference<List<CreatedStudyBasicInfos>>() {
                });

        assertNotNull(createdStudyBasicInfosList);
        assertEquals(2, createdStudyBasicInfosList.size());
        if (!createdStudyBasicInfosList.get(0).getId().equals(oldStudyUuid)) {
            Collections.reverse(createdStudyBasicInfosList);
        }
        assertTrue(createMatcherCreatedStudyBasicInfos(oldStudyUuid, "userId", "UCTE")
                .matchesSafely(createdStudyBasicInfosList.get(0)));
        assertTrue(createMatcherCreatedStudyBasicInfos(studyUuid, "userId2", "UCTE")
                .matchesSafely(createdStudyBasicInfosList.get(1)));
    }

    @Test
    public void testNotifyStudyMetadataUpdated() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/studies/{studyUuid}/notification?type=metadata_updated", studyUuid)
                .header("userId", "userId"))
                .andExpect(status().isOk());
        checkStudyMetadataUpdatedMessagesReceived(studyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/notification?type=NOT_EXISTING_TYPE", UUID.randomUUID())
                .header("userId", "userId"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testLogsReport() throws Exception {
        UUID studyUuid = createStudy("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/report", studyUuid, rootNodeUuid).header("userId", "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReporterModel> reporterModel = mapper.readValue(resultAsString, new TypeReference<List<ReporterModel>>() { });

        assertThat(reporterModel.get(0), new MatcherReport(REPORT_TEST_ROOT_NODE));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/report", studyUuid, rootNodeUuid).header("userId", "userId"))
            .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    public void testCreationWithErrorBadCaseFile() throws Exception {
        // Create study with a bad case file -> error
        createStudyWithFileError("userId", TEST_FILE_WITH_ERRORS, IMPORTED_CASE_WITH_ERRORS_UUID_STRING, false,
                "The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'");
    }

    @Test
    public void testCreationWithErrorBadExistingCase() throws Exception {
        // Create study with a bad case file -> error when importing in the case server
        createStudyWithFileError("userId", TEST_FILE_IMPORT_ERRORS, null, false, "Error during import in the case server");
    }

    @Test
    public void testCreationWithErrorNoMessageBadExistingCase() throws Exception {
        // Create study with a bad case file -> error when importing in the case server
        // without message in response body
        createStudyWithFileError("userId", TEST_FILE_IMPORT_ERRORS_NO_MESSAGE_IN_RESPONSE_BODY, null, false,
                "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message2\":\"Error during import in the case server\",\"path\":\"/v1/networks\"}");
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private UUID createStudy(String userId, UUID caseUuid, String... errorMessage) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid).header("userId", userId))
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();

        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);

        UUID studyUuid = infos.getId();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT, studyUpdateDestination);  // message for first modification node creation

        // assert that the broker message has been sent a study creation message for
        // creation
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message
        // for deletion
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", caseUuid)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")));

        return studyUuid;
    }

    private UUID createStudyWithImportParameters(String userId, UUID caseUuid, HashMap<String, Object> importParameters, String... errorMessage) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid).header("userId", userId).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(importParameters)))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();

        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);

        UUID studyUuid = infos.getId();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT, studyUpdateDestination);  // message for first modification node creation

        // assert that the broker message has been sent a study creation message for
        // creation
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message
        // for deletion
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches(String.format("/v1/cases/%s/exists", caseUuid))));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));

        assertEquals(mapper.writeValueAsString(importParameters),
                requests.stream().filter(r -> r.getPath().matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*"))
                    .findFirst().orElseThrow().getBody());
        return studyUuid;
    }

    private UUID createStudy(String userId, String fileName, String caseUuid, boolean isPrivate,
            String... errorMessage) throws Exception {
        final UUID studyUuid;
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + fileName))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", fileName, "text/xml", is);

            MvcResult result = mockMvc
                    .perform(multipart(STUDIES_URL + "?isPrivate={isPrivate}", isPrivate).file(mockFile)
                            .header("userId", userId).contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk()).andReturn();

            String resultAsString = result.getResponse().getContentAsString();
            BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);

            studyUuid = infos.getId();
        }

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        if (errorMessage.length == 0) {
            output.receive(TIMEOUT, studyUpdateDestination);   // message for first modification node creation
        }

        // assert that the broker message has been sent a study creation message for
        // creation
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));

        // assert that the broker message has been sent a study creation request message
        // for deletion
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(caseUuid == null ? 1 : 2, server);
        assertTrue(requests.contains("/v1/cases/private"));
        if (caseUuid != null) {
            assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
        }
        return studyUuid;
    }

    private UUID createStudyWithFileError(String userId, String fileName, String caseUuid, boolean isPrivate, String errorMessage)
            throws Exception {
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:" + fileName))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", fileName, "text/xml", is);

            mockMvc
                    .perform(multipart(STUDIES_URL + "?isPrivate={isPrivate}", isPrivate).file(mockFile)
                            .header("userId", userId).contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().is5xxServerError()).andReturn();
        }

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(caseUuid == null ? 1 : 2, server);
        assertTrue(requests.contains("/v1/cases/private"));

        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header("userId", "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();

        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() {
        });

        assertEquals(List.of(), bsiListResult);

        if (caseUuid != null) {
            assertTrue(requests.stream().anyMatch(r -> r.matches(
                    "/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
        }
        return null;
    }

    @Test
    public void testCreateStudyWithErrorDuringCaseImport() throws Exception {
        String userId = "userId";
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_IMPORT_ERROR).header("userId", userId))
            .andExpect(status().is5xxServerError());

       // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");

        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() { });

        assertEquals(List.of(), bsiListResult);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_CAUSING_IMPORT_ERROR)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_IMPORT_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    @Test
    public void testCreateStudyWithErrorDuringStudyCreation() throws Exception {
        String userId = "userId";
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_STUDY_CREATION_ERROR).header("userId", userId))
            .andExpect(status().isOk());

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // study deletion
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_DELETE, headers.get(HEADER_UPDATE_TYPE));

        // study error message
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(NotificationService.HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(STUDY_CREATION_ERROR_MESSAGE, headers.get(NotificationService.HEADER_ERROR));

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_CAUSING_STUDY_CREATION_ERROR)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    @Test
    public void testGetStudyCreationRequests() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        countDownLatch = new CountDownLatch(1);

        //insert a study with a case (multipartfile)
        try (InputStream is = new FileInputStream(ResourceUtils.getFile("classpath:testCase.xiidm"))) {
            MockMultipartFile mockFile = new MockMultipartFile("caseFile", "blockingCaseFile",
                    "text/xml", is);

            mvcResult = mockMvc
                    .perform(multipart(STUDIES_URL + "?isPrivate={isPrivate}", "true").file(mockFile)
                            .header("userId", "userId").contentType(MediaType.MULTIPART_FORM_DATA))
                    .andExpect(status().isOk()).andReturn();
            resultAsString = mvcResult.getResponse().getContentAsString();
            BasicStudyInfos bsiResult = mapper.readValue(resultAsString, BasicStudyInfos.class);

            assertThat(bsiResult, createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId"));
        }

        UUID studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() { });

        assertThat(bsiListResult.get(0), createMatcherStudyBasicInfos(studyUuid, "userId"));

        // once we checked study creation requests, we can countDown latch to trigger study creation request
        countDownLatch.countDown();

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation
        output.receive(TIMEOUT * 3, studyUpdateDestination);
        // drop the broker message for node creation
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT, studyUpdateDestination);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResponse.get(0), createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM"));

        // assert that all http requests have been sent to remote services
        var httpRequests = TestUtils.getRequestsDone(2, server);
        assertTrue(httpRequests.contains("/v1/cases/private"));
        assertTrue(httpRequests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));

        countDownLatch = new CountDownLatch(1);

      //insert a study
        mvcResult = mockMvc.perform(post("/v1/studies/cases/{caseUuid}?isPrivate={isPrivate}", NEW_STUDY_CASE_UUID, "false")
                .header("userId", "userId"))
            .andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        BasicStudyInfos bsiResult = mapper.readValue(resultAsString, BasicStudyInfos.class);

        assertThat(bsiResult, createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId(), "userId"));

        studyUuid = studyCreationRequestRepository.findAll().get(0).getId();

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests", NEW_STUDY_CASE_UUID, "false")
                .header("userId", "userId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() { });

        assertThat(bsiListResult.get(0), createMatcherStudyBasicInfos(studyUuid, "userId"));

        countDownLatch.countDown();

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation
        output.receive(TIMEOUT * 3);
        // drop the broker message for node creation
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT, studyUpdateDestination);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests")
                .header("userId", "userId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<List<BasicStudyInfos>>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies")
                .header("userId", "userId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        csbiListResponse = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResponse.get(0), createMatcherCreatedStudyBasicInfos(studyUuid, "userId", "XIIDM"));

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
    }

    private void checkUpdateNodesMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeId,
            Set<String> modifiedIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedIdsSet, headersStudyUpdate.get(headerUpdateTypeId));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentCreatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkStudyMetadataUpdatedMessagesReceived(UUID studyNameUserIdUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_METADATA_UPDATED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedIdsSet);
    }

    @Test
    public void testCreateStudyWithDefaultLoadflow() throws Exception {
        createStudy("userId", CASE_UUID);
        StudyEntity study = studyRepository.findAll().get(0);

        assertEquals(study.getLoadFlowProvider(), defaultLoadflowProvider);
    }

    @Test
    public void testDuplicateStudy() throws Exception {
        UUID study1Uuid = createStudy("userId", CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1");
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2");

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers", study1Uuid, node1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreationMessagesReceived(study1Uuid, node1.getId(), ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());

        var requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*")));

        // add modification on node "node2"
        String createLoadAttributes = "{\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", study1Uuid, node2.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(createLoadAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreationMessagesReceived(study1Uuid, node2.getId(), ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*")));

        node2.setLoadFlowStatus(LoadFlowStatus.CONVERGED);
        node2.setLoadFlowResult(new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs"));
        node2.setSecurityAnalysisResultUuid(UUID.randomUUID());
        networkModificationTreeService.updateNode(study1Uuid, node2);
        output.receive(TIMEOUT, studyUpdateDestination);

        // duplicate the study
        StudyEntity duplicatedStudy = duplicateStudy(study1Uuid);
        assertNotEquals(study1Uuid, duplicatedStudy.getId());

        //Test duplication from a non existing source study
        mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={sourceStudyUuid}&studyUuid={studyUuid}", UUID.randomUUID(), DUPLICATED_STUDY_UUID)
                .header("userId", "userId"))
                .andExpect(status().isNotFound());
    }

    public StudyEntity duplicateStudy(UUID studyUuid) throws Exception {
        mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={sourceStudyUuid}&studyUuid={studyUuid}", studyUuid, DUPLICATED_STUDY_UUID)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);

        StudyEntity duplicatedStudy = studyRepository.findById(UUID.fromString(DUPLICATED_STUDY_UUID)).orElse(null);
        RootNode duplicatedRootNode = networkModificationTreeService.getStudyTree(UUID.fromString(DUPLICATED_STUDY_UUID));

        //Check tree node has been duplicated
        assertEquals(1, duplicatedRootNode.getChildren().size());
        NetworkModificationNode duplicatedModificationNode = (NetworkModificationNode) duplicatedRootNode.getChildren().get(0);
        assertEquals(2, duplicatedModificationNode.getChildren().size());

        assertEquals(LoadFlowStatus.NOT_DONE, ((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getLoadFlowStatus());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getLoadFlowResult());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getSecurityAnalysisResultUuid());

        assertEquals(LoadFlowStatus.NOT_DONE, ((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getLoadFlowStatus());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getLoadFlowResult());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSecurityAnalysisResultUuid());

        //Check requests to duplicate modification has been emitted
        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/groups\\?duplicateFrom=.*&groupUuid=.*&reportUuid=.*")));

        return duplicatedStudy;
    }

    @Test
    public void testDuplicateNode() throws Exception {
        UUID study1Uuid = createStudy("userId", CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1");
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2");

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers", study1Uuid, node1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreationMessagesReceived(study1Uuid, node1.getId(), ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());

        var requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*")));

        // add modification on node "node2"
        String createLoadAttributes = "{\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads", study1Uuid, node2.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreationMessagesReceived(study1Uuid, node2.getId(), ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*")));

        node2.setLoadFlowStatus(LoadFlowStatus.CONVERGED);
        node2.setLoadFlowResult(new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs"));
        node2.setSecurityAnalysisResultUuid(UUID.randomUUID());
        networkModificationTreeService.updateNode(study1Uuid, node2);
        output.receive(TIMEOUT);

        //node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // duplicate the node1 after node2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, node1.getId(), node2.getId(), InsertMode.AFTER);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                        && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
                .count());

        // duplicate the node2 before node1
        UUID duplicatedNodeUuid2 = duplicateNode(study1Uuid, node2.getId(), node1.getId(), InsertMode.BEFORE);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid2)
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .count());

        //now the tree looks like root -> modificationNode -> duplicatedNode2 -> node1 -> node2 -> duplicatedNode1
        //duplicate node1 in a new branch starting from duplicatedNode2
        UUID duplicatedNodeUuid3 = duplicateNode(study1Uuid, node1.getId(), duplicatedNodeUuid2, InsertMode.CHILD);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        //expect to have modificationNode as a parent
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid3)
                        && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid2))
                .count());
        //and expect that no other node has the new branch create node as parent
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid3)).count());

        //try copy non existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, UUID.randomUUID(), node1.getId(), InsertMode.AFTER)
                .header("userId", "userId"))
                .andExpect(status().isNotFound());

        //try to copy to a non existing position and expect expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), UUID.randomUUID(), InsertMode.AFTER)
                .header("userId", "userId"))
                .andExpect(status().isNotFound());

        //try to copy to before the root node and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE)
                .header("userId", "userId"))
                .andExpect(status().isForbidden());

        var request = TestUtils.getRequestsDone(1, server);
        assertTrue(request.stream().anyMatch(r -> r.matches("/v1/groups\\?duplicateFrom=.*&groupUuid=.*&reportUuid=.*")));
    }

    public UUID duplicateNode(UUID studyUuid, UUID nodeToCopyUuid, UUID referenceNodeUuid, InsertMode insertMode) throws Exception {
        List<NodeEntity> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(studyUuid);

        mockMvc.perform(post(STUDIES_URL +
                "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                studyUuid, nodeToCopyUuid, referenceNodeUuid, insertMode)
                .header("userId", "userId"))
                .andExpect(status().isOk());

        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/groups\\?duplicateFrom=.*&groupUuid=.*&reportUuid=.*")));

        List<NodeEntity> allNodesAfterDuplication = networkModificationTreeService.getAllNodes(studyUuid);

        List<NodeEntity> newNodeUuid;
        //newNodeUuid = allNodesAfterDuplication.stream().filter(nodeEntity -> !allNodesBeforeDuplication.contains(nodeEntity)).collect(Collectors.toList());
        allNodesAfterDuplication.removeIf(nodeEntity -> allNodesBeforeDuplication.stream().anyMatch(nodeEntity1 -> nodeEntity.getIdNode().equals(nodeEntity1.getIdNode())));

        return allNodesAfterDuplication.get(0).getIdNode();
    }

    public void getDefaultLoadflowProvider() throws Exception {
        mockMvc.perform(get("/v1/loadflow-default-provider")).andExpectAll(
                status().isOk(),
                content().string(defaultLoadflowProvider));
    }

    @Test
    public void reindexStudyTest() throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/reindex-all", UUID.randomUUID()))
            .andExpect(status().isNotFound());

        UUID study1Uuid = createStudy("userId", CASE_UUID);

        mockMvc.perform(post("/v1/studies/{studyUuid}/reindex-all", study1Uuid))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().contains("/v1/networks/" + NETWORK_UUID_STRING + "/reindex-all")));
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*")).count());

        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(study1Uuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);

        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
