/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.Line;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.model.VariantInfos;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okio.Buffer;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationInfos;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.*;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.CASE_API_VERSION;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyException.Type.STUDY_NOT_FOUND;
import static org.gridsuite.study.server.notification.NotificationService.DEFAULT_ERROR_MESSAGE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTest.class);

    @Autowired
    private MockMvc mockMvc;

    private static final String FIRST_VARIANT_ID = "first_variant_id";
    private static final long TIMEOUT = 1000;
    private static final String STUDIES_URL = "/v1/studies";
    private static final String TEST_FILE_UCTE = "testCase.ucte";
    private static final String TEST_FILE = "testCase.xiidm";
    private static final String TEST_FILE_IMPORT_ERRORS = "testCase_import_errors.xiidm";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00e";
    private static final String CLONED_NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String IMPORTED_CASE_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final String CLONED_CASE_UUID_STRING = "22222222-1111-0000-0000-000000000000";
    private static final String IMPORTED_BLOCKING_CASE_UUID_STRING = "22111111-0000-0000-0000-000000000000";
    private static final String IMPORTED_CASE_WITH_ERRORS_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    private static final String NEW_STUDY_CASE_UUID = "11888888-0000-0000-0000-000000000000";
    private static final String DUPLICATED_STUDY_UUID = "11888888-0000-0000-0000-111111111111";
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_NETWORK_CASE_UUID_STRING = "00000000-0000-0000-0000-000000000001";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final String USER_ID_HEADER = "userId";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CLONED_NETWORK_UUID = UUID.fromString(CLONED_NETWORK_UUID_STRING);
    private static final UUID NOT_EXISTING_NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID NOT_EXISTING_NETWORK_CASE_UUID = UUID.fromString(NOT_EXISTING_NETWORK_CASE_UUID_STRING);
    private static final UUID CLONED_CASE_UUID = UUID.fromString(CLONED_CASE_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final NetworkInfos NOT_EXISTING_NETWORK_INFOS = new NetworkInfos(NOT_EXISTING_NETWORK_UUID, "not_existing_network_id");
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final Report REPORT_TEST = Report.builder().id(REPORT_UUID).message("test").severity(StudyConstants.Severity.WARN).build();
    private static final UUID REPORT_LOG_PARENT_UUID = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();
    private static final List<ReportLog> REPORT_LOGS = List.of(new ReportLog("test", StudyConstants.Severity.WARN, REPORT_LOG_PARENT_UUID));
    private static final String VARIANT_ID = "variant_1";
    private static final String POST = "POST";
    private static final String DELETE = "DELETE";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";
    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final String CASE_UUID_CAUSING_IMPORT_ERROR = "178719f5-cccc-48be-be46-e92345951e32";
    private static final String CASE_UUID_CAUSING_STUDY_CREATION_ERROR = "278719f5-cccc-48be-be46-e92345951e32";
    private static final String CASE_UUID_CAUSING_CONVERSION_ERROR = "278719f5-cccc-48be-be46-e92345951e33";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";
    private static final NetworkInfos NETWORK_INFOS_2 = new NetworkInfos(UUID.fromString(NETWORK_UUID_2_STRING), "file_2.xiidm");
    private static final NetworkInfos NETWORK_INFOS_3 = new NetworkInfos(UUID.fromString(NETWORK_UUID_3_STRING), "file_3.xiidm");
    private static final String CASE_NAME = "DefaultCaseName";
    private static final UUID EMPTY_MODIFICATION_GROUP_UUID = UUID.randomUUID();
    private static final String STUDY_CREATION_ERROR_MESSAGE = "Une erreur est survenue lors de la création de l'étude";
    private static final String URI_NETWORK_MODIF = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications?rootNetworkUuid={rootNetworkUuid}";
    private static final String CASE_FORMAT = "caseFormat";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";

    private static final String PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "a09f5282-8e36-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "709f0282-8034-48b5-b66c-7ef9f3f36c4f";
    private static final String PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING = "d09f5112-8e34-41b5-b45e-7ef9f3f36c4f";
    private static final String PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING = "409f5782-8114-48b5-b66e-7ff9f3f36c4f";
    private static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING = "473ff5ce-4378-8dd2-9d07-ce73c5ef11d9";

    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"name\":\"Profile with broken params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING +
        "\",\"allLinksValid\":false}";

    private static final String PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "2cec4a7b-ab7e-4d78-9dd2-ce73c5ef11d9";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-a87e-4d78-9da7-ce73c5ef11d9";
    private static final String PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING = "5cec4a2b-affe-4d78-91d7-ce73c5ef11d9";
    private static final String PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-ab74-5d78-9d07-ce73c5ef11d9";
    private static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING = "2c865123-4378-8dd2-9d07-ce73c5ef11d9";

    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING +
        "\",\"allLinksValid\":true}";

    private static final String PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String DUPLICATED_LOADFLOW_PARAMS_JSON = "\"" + PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "b4ce25e1-56a7-401d-acd1-04425fe24587";
    private static final String DUPLICATED_SECURITY_ANALYSIS_PARAMS_JSON = "\"" + PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "f4ce37e1-59a7-501d-abb1-04425fe24587";
    private static final String DUPLICATED_SENSITIVITY_ANALYSIS_PARAMS_JSON = "\"" + PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String PROFILE_SHORTCIRCUIT_DUPLICATED_PARAMETERS_UUID_STRING = "d4ce28e3-59a7-422d-abb1-04425fe24587";
    private static final String DUPLICATED_SHORTCIRCUIT_PARAMS_JSON = "\"" + PROFILE_SHORTCIRCUIT_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING = "d4ce25e1-27a7-401d-a721-04425fe24587";
    private static final String DUPLICATED_VOLTAGE_INIT_PARAMS_JSON = "\"" + PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String NETWORK_VISUALIZATION_DUPLICATED_PARAMETERS_UUID_STRING = "407a4bec-6f1a-400f-98f0-e5bcf37d4fcf";
    private static final String DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON = "\"" + NETWORK_VISUALIZATION_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String SPREADSHEET_CONFIG_COLLECTION_UUID_STRING = "f4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON = "\"" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "\"";

    private static final String DEFAULT_PROVIDER = "defaultProvider";

    @Value("${non-evacuated-energy.default-provider}")
    private String defaultNonEvacuatedEnergyProvider;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @SpyBean
    private CaseService caseService;

    @Autowired
    private NetworkService networkService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private VoltageInitService voltageInitService;

    @Autowired
    private LoadFlowService loadflowService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private StateEstimationService stateEstimationService;

    @Autowired
    private StudyConfigService studyConfigService;

    @MockBean
    private EquipmentInfosService equipmentInfosService;

    @MockBean
    private StudyInfosService studyInfosService;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    private List<EquipmentInfos> linesInfos;

    private List<CreatedStudyBasicInfos> studiesInfos;

    // new mock server (use this one to mock API calls)
    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    //used by testGetStudyCreationRequests to control asynchronous case import
    private CountDownLatch countDownLatch;

    @MockBean
    private NetworkStoreService networkStoreService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String elementUpdateDestination = "element.update";

    private boolean indexed = false;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;

    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private TestUtils studyTestUtils;

    private static EquipmentInfos toEquipmentInfos(Line line) {
        return EquipmentInfos.builder()
            .networkUuid(NETWORK_UUID)
            .id(line.getId())
            .name(line.getNameOrId())
            .type("LINE")
            .variantId("InitialState")
            .voltageLevels(Set.of(VoltageLevelInfos.builder().id(line.getTerminal1().getVoltageLevel().getId()).name(line.getTerminal1().getVoltageLevel().getNameOrId()).build()))
            .build();
    }

    private void initMockBeans(Network network) {
        linesInfos = network.getLineStream().map(StudyTest::toEquipmentInfos).collect(Collectors.toList());

        studiesInfos = List.of(
                CreatedStudyBasicInfos.builder().id(UUID.fromString(DUPLICATED_STUDY_UUID)).userId("userId1").build(),
                CreatedStudyBasicInfos.builder().id(UUID.fromString("11888888-0000-0000-0000-111111111112")).userId("userId1").build()
        );

        when(studyInfosService.search(String.format("userId:%s", "userId")))
                .then((Answer<List<CreatedStudyBasicInfos>>) invocation -> studiesInfos);

        when(equipmentInfosService.searchEquipments(any(), any(), any(), any(), any())).thenCallRealMethod();
        when(equipmentInfosService.searchEquipments(any(BoolQuery.class))).then((Answer<List<EquipmentInfos>>) invocation -> linesInfos);
        when(equipmentInfosService.getEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("32"));
        when(equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("16"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("8"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("4"));

        when(networkStoreService.cloneNetwork(NETWORK_UUID, List.of(VariantManagerConstants.INITIAL_VARIANT_ID))).thenReturn(network);
        when(networkStoreService.getNetworkUuid(network)).thenReturn(NETWORK_UUID);
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenReturn(network);
        when(networkStoreService.getVariantsInfos(NETWORK_UUID))
                .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0),
                        new VariantInfos(VARIANT_ID, 1)));
        when(networkStoreService.getVariantsInfos(CLONED_NETWORK_UUID))
                .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0)));

        doNothing().when(networkStoreService).deleteNetwork(NETWORK_UUID);
    }

    private void initMockBeansNetworkNotExisting() {
        when(networkStoreService.cloneNetwork(NOT_EXISTING_NETWORK_UUID, Collections.emptyList())).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.NONE)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));

        doNothing().when(networkStoreService).deleteNetwork(NOT_EXISTING_NETWORK_UUID);
    }

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        Network notExistingNetwork = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        notExistingNetwork.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        notExistingNetwork.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeansNetworkNotExisting();

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockUtils = new WireMockUtils(wireMockServer);

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        wireMockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        caseService.setCaseServerBaseUri(baseUrl);
        networkConversionService.setNetworkConversionServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        voltageInitService.setVoltageInitServerBaseUri(baseUrl);
        loadflowService.setLoadFlowServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);
        studyConfigService.setStudyConfigServerBaseUri(baseUrl);

        String baseUrlWireMock = wireMockServer.baseUrl();
        networkModificationService.setNetworkModificationServerBaseUri(baseUrlWireMock);

        String networkInfosAsString = mapper.writeValueAsString(NETWORK_INFOS);
        String notExistingNetworkInfosAsString = mapper.writeValueAsString(NOT_EXISTING_NETWORK_INFOS);
        String networkInfos2AsString = mapper.writeValueAsString(NETWORK_INFOS_2);
        String networkInfos3AsString = mapper.writeValueAsString(NETWORK_INFOS_3);
        String clonedCaseUuidAsString = mapper.writeValueAsString(CLONED_CASE_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/groups/" + EMPTY_MODIFICATION_GROUP_UUID + "/.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), new JSONArray(List.of()).toString());
                } else if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID) ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), new JSONArray(List.of(jsonObject)).toString());
                } else if (path.matches("/v1/groups\\?duplicateFrom=.*&groupUuid=.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/network-modifications.*") && POST.equals(request.getMethod())) {
                    ModificationInfos modificationInfos = mapper.readValue(body.readUtf8(), new TypeReference<>() { });
                    modificationInfos.setSubstationIds(Set.of("s2"));
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "[" + mapper.writeValueAsString(modificationInfos) + "]");
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse(200);
                    } else {
                        return new MockResponse(HttpStatus.BAD_REQUEST.value());
                    }
                } else if (path.matches("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/reindex-all")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/networks/.*/reindex-all")) {
                    // simulate a server error
                    if (indexed) {
                        return new MockResponse(500);
                    }
                    indexed = true;
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/indexed-equipments")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/networks/.*/indexed-equipments")) {
                    return new MockResponse(indexed ? 200 : 204);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*") || path.matches("/v1/networks\\?caseUuid=" + CLONED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS, "UCTE");
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NOT_EXISTING_NETWORK_INFOS, "UCTE");
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_2_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS_2, "UCTE");
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_3_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS_3, "UCTE");
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")) {
                    return new MockResponse(500, Headers.of("Content-Type", "application/json; charset=utf-8"),
                        "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*") || path.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    // need asynchronous run to get study creation requests
                    new Thread(() -> {
                        try {
                            countDownLatch.await();
                            sendCaseImportSucceededMessage(path, NETWORK_INFOS, "XIIDM");
                        } catch (Exception e) {
                            LOGGER.error("Error while waiting", e);
                        }
                    }).start();
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportFailedMessage(path, STUDY_CREATION_ERROR_MESSAGE);
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_CONVERSION_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportFailedMessage(path, null); // some conversion errors don't returnany error mesage
                    return new MockResponse(200);
                } else if (path.matches("/v1/reports/.*/duplicate")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(UUID.randomUUID()));
                } else if (path.matches("/v1/reports/" + REPORT_ID + "/logs.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(REPORT_LOGS));
                } else if (path.matches("/v1/reports/.*/logs.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(REPORT_LOGS));
                } else if (path.matches("/v1/reports/.*") && !"PUT".equals(request.getMethod())) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(REPORT_TEST));
                } else if (path.matches("/v1/reports/.*") && "PUT".equals(request.getMethod())) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/networks\\?caseUuid=" + IMPORTED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")) {
                    sendCaseImportSucceededMessage(path, NETWORK_INFOS, "XIIDM");
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters.*") && POST.equals(request.getMethod())) {
                    if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(404); // params duplication request KO
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_LOADFLOW_PARAMS_JSON); // params duplication request OK
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(404); // params duplication request KO
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_SECURITY_ANALYSIS_PARAMS_JSON); // params duplication request OK
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(404); // params duplication request KO
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_SENSITIVITY_ANALYSIS_PARAMS_JSON); // params duplication request OK
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(404); // params duplication request KO
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_SHORTCIRCUIT_PARAMS_JSON); // params duplication request OK
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(404); // params duplication request KO
                    } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_VOLTAGE_INIT_PARAMS_JSON); // params duplication request OK
                    } else {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(UUID.randomUUID()));
                    }
                } else if (path.matches("/v1/parameters/.*") && DELETE.equals(request.getMethod())) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/network-visualizations-params/.*") && DELETE.equals(request.getMethod())) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/network-visualizations-params/default")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/network-visualizations-params\\?duplicateFrom=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/.*/provider")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/default-provider")) {
                    return new MockResponse.Builder().code(200).body(DEFAULT_PROVIDER).build();
                } else if (path.matches("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_NO_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_INVALID_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                } else if (path.matches("/v1/spreadsheet-config-collections/default") && !POST.equals(request.getMethod())) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                } else if (path.matches("/v1/spreadsheet-config-collections/.*") && DELETE.equals(request.getMethod())) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                } else if (path.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING)) {
                    return new MockResponse(404); // config duplication request KO
                } else if (path.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(UUID.randomUUID()));
                }

                switch (path) {
                    case "/v1/networks/" + NETWORK_UUID_STRING:
                    case "/v1/studies/cases/{caseUuid}":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "CGMES");
                    case "/v1/studies/newStudy/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "XIIDM");

                    case "/v1/cases/" + CASE_UUID_STRING + "/exists":
                    case "/v1/cases/" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "/exists":
                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/exists":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/exists":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/exists":
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/exists":
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/exists":
                    case "/v1/cases/" + CASE_UUID_CAUSING_IMPORT_ERROR + "/exists":
                    case "/v1/cases/" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "/exists":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "true");
                    case "/v1/cases/" + CASE_UUID_CAUSING_CONVERSION_ERROR + "/exists":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "true");
                    case "/v1/cases/" + CASE_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + CASE_UUID_STRING + "\",\"name\":\"" + TEST_FILE_UCTE + "\",\"format\":\"UCTE\"}");
                    case "/v1/cases/" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "\",\"name\":\"" + TEST_FILE_UCTE + "\",\"format\":\"UCTE\"}");
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "\",\"name\":\"" + TEST_FILE_IMPORT_ERRORS + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + IMPORTED_CASE_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + NEW_STUDY_CASE_UUID + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + IMPORTED_BLOCKING_CASE_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + CASE_2_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/infos":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"uuid\":\"" + CASE_3_UUID_STRING + "\",\"name\":\"" + CASE_NAME + "\",\"format\":\"XIIDM\"}");
                    case "/v1/cases/" + CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "/format":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "UCTE");
                    case "/v1/cases/" + IMPORTED_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "/format":
                    case "/v1/cases/" + NEW_STUDY_CASE_UUID + "/format":
                    case "/v1/cases/" + IMPORTED_BLOCKING_CASE_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_2_UUID_STRING + "/format":
                    case "/v1/cases/" + CASE_3_UUID_STRING + "/format":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "XIIDM");
                    case "/v1/cases/" + NOT_EXISTING_CASE_UUID + "/exists":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "false");
                    // duplicate case
                    case "/v1/cases?duplicateFrom=" + CASE_UUID_STRING + "&withExpiration=true":
                    case "/v1/cases?duplicateFrom=" + CASE_UUID_STRING + "&withExpiration=false":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), clonedCaseUuidAsString);
                    // delete case
                    case "/v1/cases/" + CASE_UUID_STRING:
                    // disable case expiration
                    case "/v1/cases/" + CASE_UUID_STRING + "/disableExpiration":
                        return new MockResponse(200);

                    case "/" + CASE_API_VERSION + "/cases/" + IMPORTED_CASE_UUID_STRING:
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), new JSONArray(List.of(jsonObject)).toString());

                    case "/v1/networks?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID:
                    case "/v1/networks?caseUuid=" + IMPORTED_BLOCKING_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        countDownLatch.await(2, TimeUnit.SECONDS);
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), networkInfosAsString);
                    case "/v1/networks?caseUuid=" + CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), networkInfosAsString);
                    case "/v1/networks?caseUuid=" + NOT_EXISTING_NETWORK_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), notExistingNetworkInfosAsString);
                    case "/v1/networks?caseUuid=" + CASE_2_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), networkInfos2AsString);
                    case "/v1/networks?caseUuid=" + CASE_3_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), networkInfos3AsString);
                    case "/v1/networks?caseUuid=" + CASE_UUID_CAUSING_IMPORT_ERROR + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(500);
                    case "/v1/networks?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID:
                        sendCaseImportFailedMessage(path, "ERROR WHILE IMPORTING STUDY");
                        return new MockResponse(200);
                    case "/v1/networks?caseUuid=" + IMPORTED_CASE_WITH_ERRORS_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID:
                        return new MockResponse(500, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                            "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"The network 20140116_0830_2D4_UX1_pst already contains an object 'GeneratorImpl' with the id 'BBE3AA1 _generator'\",\"path\":\"/v1/networks\"}");

                    case "/v1/reports/" + NETWORK_UUID_STRING:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), mapper.writeValueAsString(REPORT_TEST));
                    case "/v1/reports":
                        return new MockResponse(200);
                    case "/v1/export/formats":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "[\"CGMES\",\"UCTE\",\"XIIDM\"]");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM?fileName=" + "myFileName":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fileName", HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE), "byteData");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fileName", HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE), "byteData");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM" + "?variantId=" + VARIANT_ID + "&fileName=" + "myFileName":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fileName", HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE), "byteData");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/export/XIIDM" + "?variantId=" + VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fileName", HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE), "byteData");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID_2:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/" + VARIANT_ID_3:
                        return new MockResponse(200);
                    default:
                        LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                        return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
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
            Map<String, String> importParameters = new HashMap<>();
            importParameters.put("param1", "changedValue1, changedValue2");
            importParameters.put("param2", "changedValue");
            input.send(MessageBuilder.withPayload("").setHeader("receiver", URLDecoder.decode(receiverUrlString, StandardCharsets.UTF_8))
                    .setHeader("networkUuid", networkInfos.getNetworkUuid().toString())
                    .setHeader("networkId", networkInfos.getNetworkId())
                    .setHeader("caseFormat", format)
                    .setHeader("caseName", "caseName")
                    .setHeader("importParameters", importParameters)
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
                    .build(), "case.import.start.dlx");
        }
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    @Test
    void testSearch(final MockWebServer mockWebServer) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        UUID studyUuid = createStudy(mockWebServer, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeId = getRootNodeUuid(studyUuid);

        mvcResult = mockMvc
                .perform(get("/v1/search?q={request}", String.format("userId:%s", "userId")).header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(createdStudyBasicInfosList, new MatcherJson<>(mapper, studiesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME",
                        studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<EquipmentInfos> equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=NAME",
                        studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mvcResult = mockMvc
                .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=ID",
                        studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        equipmentInfos = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertThat(equipmentInfos, new MatcherJson<>(mapper, linesInfos));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/search?userInput={request}&fieldSelector=bogus",
                        studyUuid, firstRootNetworkUuid, rootNodeId, "B").header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isBadRequest(),
                        content().string("Enum unknown entry 'bogus' should be among NAME, ID"))
                .andReturn();
    }

    @Test
    void test(final MockWebServer server) throws Exception {
        MvcResult result;
        String resultAsString;
        String userId = "userId";

        //empty list
        mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //empty list
        mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //insert a study
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        // check the study
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
                     .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        StudyInfos infos = mapper.readValue(resultAsString, StudyInfos.class);

        assertThat(infos, createMatcherStudyInfos(studyUuid));

        //insert a study with a non-existing case and except exception
        result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}",
                NOT_EXISTING_CASE_UUID, "false").header(USER_ID_HEADER, "userId").param(CASE_FORMAT, "XIIDM"))
                     .andExpectAll(status().isFailedDependency(), content().contentType(MediaType.valueOf("text/plain;charset=UTF-8"))).andReturn();
        assertEquals("The case '" + NOT_EXISTING_CASE_UUID + "' does not exist", result.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server)
                       .contains(String.format("/v1/cases/%s/exists", NOT_EXISTING_CASE_UUID)));

        result = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId"))
                     .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(0), createMatcherCreatedStudyBasicInfos(studyUuid));

        //insert the same study but with another user (should work)
        //even with the same name should work
        studyUuid = createStudy(server, "userId2", CASE_UUID);

        resultAsString = mockMvc.perform(get("/v1/studies").header("userId", "userId2"))
                             .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();

        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(1),
            createMatcherCreatedStudyBasicInfos(studyUuid));

        UUID randomUuid = UUID.randomUUID();
        //get a non-existing study -> 404 not found
        mockMvc.perform(get("/v1/studies/{studyUuid}", randomUuid).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isNotFound(),
                content().contentType(MediaType.APPLICATION_JSON),
                jsonPath("$").value(STUDY_NOT_FOUND.name()));

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        // expect only 1 study (public one) since the other is private and we use
        // another userId
        result = mockMvc.perform(get("/v1/studies").header("userId", "a"))
                     .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(2, createdStudyBasicInfosList.size());

        //get available export format
        mockMvc.perform(get("/v1/export-network-formats")).andExpectAll(status().isOk(),
            content().string("[\"CGMES\",\"UCTE\",\"XIIDM\"]"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains("/v1/export/formats"));

        //export a network
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM"))
            .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/export/XIIDM?fileName=%s", NETWORK_UUID_STRING, "myFileName")));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName&formatParameters=%7B%22iidm.export.xml.indent%22%3Afalse%7D", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM"))
            .andExpect(status().isOk());
        TestUtils.getRequestsDone(1, server); // just consume it

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 3", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, "XIIDM"))
            .andExpect(status().isInternalServerError());

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode1.getId(), studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid),
            RootNetworkNodeInfo.builder().nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT)).build());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName",
                        studyNameUserIdUuid,
                        firstRootNetworkUuid,
                        modificationNode1Uuid,
                        "XIIDM"))
            .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/export/XIIDM?variantId=%s&fileName=%s", NETWORK_UUID_STRING, VARIANT_ID, "myFileName")));
    }

    @Test
    void testCreateStudyWithImportParameters(final MockWebServer mockWebServer) throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        ArrayList<String> randomListParam = new ArrayList<>();
        randomListParam.add("paramValue1");
        randomListParam.add("paramValue2");
        importParameters.put("param1", randomListParam);
        UUID studyUuid = createStudyWithImportParameters(mockWebServer, "userId", CASE_UUID, "UCTE", importParameters);

        StudyEntity studyEntity = studyRepository.findById(studyUuid).get();
        assertEquals(studyUuid, studyEntity.getId());
    }

    @Test
    void testCreateStudyWithDuplicateCase(final MockWebServer mockWebServer) throws Exception {
        createStudyWithDuplicateCase(mockWebServer, "userId", CASE_UUID);
    }

    @Test
    void testDeleteStudy(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID()); // does not have default params
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        UUID stubUuid = wireMockUtils.stubNetworkModificationDeleteGroup();
        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockUtils.verifyNetworkModificationDeleteGroup(stubUuid);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(10, server);
        assertEquals(2, requests.stream().filter(r -> r.getPath().matches("/v1/reports")).count());
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/cases/" + CASE_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/" + studyEntity.getVoltageInitParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/" + studyEntity.getLoadFlowParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/" + studyEntity.getSecurityAnalysisParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/" + studyEntity.getSensitivityAnalysisParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/" + studyEntity.getStateEstimationParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/network-visualizations-params/" + studyEntity.getNetworkVisualizationParametersUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/spreadsheet-config-collections/" + studyEntity.getSpreadsheetConfigCollectionUuid())));
    }

    @Test
    void testDeleteStudyWithError(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        doAnswer(invocation -> {
            throw new InterruptedException();
        }).when(caseService).deleteCase(any());

        UUID stubUuid = wireMockUtils.stubNetworkModificationDeleteGroup();
        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isInternalServerError(), content().string(InterruptedException.class.getName()));

        wireMockUtils.verifyNetworkModificationDeleteGroup(stubUuid);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/spreadsheet-config-collections/" + studyEntity.getSpreadsheetConfigCollectionUuid())));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports")));
    }

    @Test
    void testDeleteStudyWithNonExistingCase(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);

        UUID stubUuid = wireMockUtils.stubNetworkModificationDeleteGroup();

        // Changing the study case uuid with a non-existing case
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElse(null);
        assertNotNull(studyEntity);
        UUID nonExistingCaseUuid = UUID.randomUUID();
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(studyUuid);
        rootNetworkEntity.setCaseUuid(nonExistingCaseUuid);
        rootNetworkRepository.save(rootNetworkEntity);

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockUtils.verifyNetworkModificationDeleteGroup(stubUuid);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(9, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/cases/" + nonExistingCaseUuid)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/.*"))); // x 4
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/spreadsheet-config-collections/.*")));
    }

    @Test
    void testMetadata(final MockWebServer mockWebServer) throws Exception {
        UUID studyUuid = createStudy(mockWebServer, "userId", CASE_UUID);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudy(mockWebServer, "userId2", CASE_UUID);

        MvcResult mvcResult = mockMvc
                .perform(get("/v1/studies/metadata?ids="
                        + Stream.of(oldStudyUuid, studyUuid).map(Object::toString).collect(Collectors.joining(",")))
                        .header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertNotNull(createdStudyBasicInfosList);
        assertEquals(2, createdStudyBasicInfosList.size());
        if (!createdStudyBasicInfosList.get(0).getId().equals(oldStudyUuid)) {
            Collections.reverse(createdStudyBasicInfosList);
        }
        assertTrue(createMatcherCreatedStudyBasicInfos(oldStudyUuid)
                .matchesSafely(createdStudyBasicInfosList.get(0)));
        assertTrue(createMatcherCreatedStudyBasicInfos(studyUuid)
                .matchesSafely(createdStudyBasicInfosList.get(1)));
    }

    @Test
    void testNotifyStudyMetadataUpdated() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/studies/{studyUuid}/notification?type=metadata_updated", studyUuid)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkStudyMetadataUpdatedMessagesReceived();

        mockMvc.perform(post("/v1/studies/{studyUuid}/notification?type=NOT_EXISTING_TYPE", UUID.randomUUID())
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogsReport(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?reportType=NETWORK_MODIFICATION", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<Report> reports = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(1, reports.size());
        assertThat(reports.get(0), new MatcherReport(REPORT_TEST));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/.*")));
    }

    @Test
    void testGetNodeReportLogs(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, "userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/{reportId}/logs", studyUuid, firstRootNetworkUuid, rootNodeUuid, REPORT_ID).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<List<ReportLog>>() { });
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.get(0)));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/" + REPORT_ID + "/logs")));

        //test with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/{reportId}/logs?severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid, REPORT_ID).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<List<ReportLog>>() { });
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.get(0)));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/reports/" + REPORT_ID + "/logs\\?severityLevels=WARN&message=testMsgFilter")));
    }

    @Test
    void testGetParentNodesReportLogs(final MockWebServer server) throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudy(server, userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        AbstractNode modificationNode = rootNode.getChildren().get(0);
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID_3, "node3", userId);
        UUID rootNodeReportId = networkModificationTreeService.getReportUuid(rootNode.getId(), firstRootNetworkUuid);
        UUID modificationNodeReportId = networkModificationTreeService.getReportUuid(modificationNode.getId(), firstRootNetworkUuid);
        UUID node1ReportId = networkModificationTreeService.getReportUuid(node1.getId(), firstRootNetworkUuid);
        UUID node2ReportId = networkModificationTreeService.getReportUuid(node2.getId(), firstRootNetworkUuid);

        //          root
        //           |
        //     modificationNode
        //           |
        //         node1
        //         /   \
        //     node2  node3

        //get logs of node2 and all its parents (should not get node3 logs)
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<List<ReportLog>>() { });
        assertEquals(4, reportLogs.size());
        var requests = TestUtils.getRequestsDone(4, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + node2ReportId + "/logs")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + node1ReportId + "/logs")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + modificationNodeReportId + "/logs")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + rootNodeReportId + "/logs")));

        //get logs of node2 and all its parents (should not get node3 logs) with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<List<ReportLog>>() { });
        assertEquals(4, reportLogs.size());
        requests = TestUtils.getRequestsDone(4, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + node2ReportId + "/logs\\?severityLevels=WARN&message=testMsgFilter")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + node1ReportId + "/logs\\?severityLevels=WARN&message=testMsgFilter")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + modificationNodeReportId + "/logs\\?severityLevels=WARN&message=testMsgFilter")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/" + rootNodeReportId + "/logs\\?severityLevels=WARN&message=testMsgFilter")));
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus, String userId) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(NodeBuildStatus.from(buildStatus)).build());

        return modificationNode;
    }

    private UUID createStudy(final MockWebServer mockWebServer, String userId, UUID caseUuid) throws Exception {
        return createStudy(mockWebServer, userId, caseUuid, null, false);
    }

    private UUID createStudy(final MockWebServer server, String userId, UUID caseUuid, String parameterDuplicatedUuid, boolean parameterDuplicationSuccess) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        assertStudyCreation(studyUuid, userId);

        // assert that all http requests have been sent to remote services
        int nbRequest = 13;
        if (parameterDuplicatedUuid != null && !parameterDuplicationSuccess) {
            nbRequest += 6;
        }
        var requests = TestUtils.getRequestsDone(nbRequest, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", caseUuid)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*" + "&caseFormat=UCTE")));
        if (!parameterDuplicationSuccess) {
            assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters/default")));
        }
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/.*")));
        if (!parameterDuplicationSuccess) {
            assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/default")));
        }
        assertTrue(requests.contains(String.format("/v1/cases/%s/disableExpiration", caseUuid)));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + userId + "/profile")));
        if (parameterDuplicatedUuid != null) {
            assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + parameterDuplicatedUuid) ||
                                                             r.equals("/v1/spreadsheet-config-collections?duplicateFrom=" + parameterDuplicatedUuid))); // post duplicate
        }

        return studyUuid;
    }

    protected UUID createStudyWithImportParameters(final MockWebServer server, String userId, UUID caseUuid, String caseFormat, Map<String, Object> importParameters) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                        .header("userId", userId).contentType(MediaType.APPLICATION_JSON)
                        .param(CASE_FORMAT, caseFormat)
                        .content(mapper.writeValueAsString(importParameters)))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        assertStudyCreation(studyUuid, userId);

        // assert that all http requests have been sent to remote services
        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(13, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches(String.format("/v1/cases/%s/exists", caseUuid))));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches(String.format("/v1/cases/%s/disableExpiration", caseUuid))));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/parameters/default")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/spreadsheet-config-collections/default")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports/.*")));

        assertEquals(mapper.writeValueAsString(importParameters), requests.stream()
                .filter(r -> r.getPath().matches("/v1/networks\\?caseUuid=" + caseUuid + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*"))
                .findFirst()
                .orElseThrow()
                .getBody());
        return studyUuid;
    }

    private UUID createStudyWithDuplicateCase(final MockWebServer server, String userId, UUID caseUuid) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                        .param("duplicateCase", "true")
                        .param(CASE_FORMAT, "UCTE")
                        .header("userId", userId))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        assertStudyCreation(studyUuid, userId);

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(14, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", caseUuid)));
        assertTrue(requests.contains(String.format("/v1/cases?duplicateFrom=%s&withExpiration=%s", caseUuid, true)));
        // note : it's a new case UUID
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + CLONED_CASE_UUID_STRING + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*&receiver=.*")));
        assertTrue(requests.contains(String.format("/v1/cases/%s/disableExpiration", CLONED_CASE_UUID_STRING)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters/default")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/spreadsheet-config-collections/default")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + userId + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/.*")));

        return studyUuid;
    }

    private void assertStudyCreation(UUID studyUuid, String userId, String... errorMessage) {
        assertTrue(studyRepository.findById(studyUuid).isPresent());

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));
    }

    @Test
    void testGetNullNetwork() {
        // just for test coverage
        assertNull(networkService.getNetwork(UUID.randomUUID(), PreloadingStrategy.COLLECTION, null));
    }

    @Test
    void testCreateStudyWithErrorDuringCaseImport(final MockWebServer server) throws Exception {
        String userId = "userId";
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_IMPORT_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().is5xxServerError());

       // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");

        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_CAUSING_IMPORT_ERROR)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_IMPORT_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    @Test
    void testCreateStudyCreationFailedWithoutErrorMessage(final MockWebServer server) throws Exception {
        String userId = "userId";
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_CONVERSION_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "XIIDM"))
                .andExpect(status().isOk());

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // checks that the error message has a default value set
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(DEFAULT_ERROR_MESSAGE, headers.get(NotificationService.HEADER_ERROR));

        TestUtils.getRequestsDone(2, server);
    }

    @Test
    void testCreateStudyWithErrorDuringStudyCreation(final MockWebServer server) throws Exception {
        String userId = "userId";
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_STUDY_CREATION_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().isOk());

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // study error message
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(STUDY_CREATION_ERROR_MESSAGE, headers.get(NotificationService.HEADER_ERROR));

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", CASE_UUID_CAUSING_STUDY_CREATION_ERROR)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + CASE_UUID_CAUSING_STUDY_CREATION_ERROR + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
    }

    @SuppressWarnings({"java:S1481", "java:S1854"}) //TODO found better way to test json result that Sonar wouldn't flag because of unused/useless local variables
    @Test
    void testGetStudyCreationRequests(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        countDownLatch = new CountDownLatch(1);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

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

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<>() { });

        countDownLatch = new CountDownLatch(1);

        //insert a study
        mvcResult = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", NEW_STUDY_CASE_UUID, "false")
                                        .header(USER_ID_HEADER, "userId")
                        .param(CASE_FORMAT, "XIIDM"))
                        .andExpect(status().isOk())
                        .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        BasicStudyInfos bsiResult = mapper.readValue(resultAsString, BasicStudyInfos.class);

        assertThat(bsiResult, createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId()));

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests", NEW_STUDY_CASE_UUID, "false")
                                        .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

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
                                        .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies")
                                        .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        csbiListResponse = mapper.readValue(resultAsString, new TypeReference<>() { });

        // assert that all http requests have been sent to remote services
        var requests = TestUtils.getRequestsDone(13, server);
        assertTrue(requests.contains(String.format("/v1/cases/%s/exists", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/networks\\?caseUuid=" + NEW_STUDY_CASE_UUID + "&variantId=" + FIRST_VARIANT_ID + "&reportUuid=.*")));
        assertTrue(requests.contains(String.format("/v1/cases/%s/disableExpiration", NEW_STUDY_CASE_UUID)));
        assertTrue(requests.contains("/v1/parameters/default"));
        assertTrue(requests.contains("/v1/spreadsheet-config-collections/default"));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/userId/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/.*")));
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    private void checkNodeBuildStatusUpdatedMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
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

    private void checkStudyMetadataUpdatedMessagesReceived() {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_METADATA_UPDATED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testCreateStudyWithDefaultLoadflow(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, "userId", CASE_UUID);
        StudyEntity study = studyRepository.findAll().get(0);
        assertEquals(study.getNonEvacuatedEnergyProvider(), defaultNonEvacuatedEnergyProvider);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasNoParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, NO_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, null, false);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING, true);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, true);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, true);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING, true);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING, true);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasInvalidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING, false);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasValidParamsInProfile(final MockWebServer mockWebServer) throws Exception {
        createStudy(mockWebServer, VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID, PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING, true);
    }

    private void testDuplicateStudy(final MockWebServer mockWebServer, UUID study1Uuid, UUID rootNetworkUuid) throws Exception {
        String userId = "userId";
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), rootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node1.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), rootNetworkUuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createLoadAttributes)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node2.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .securityAnalysisResultUuid(UUID.randomUUID())
                .sensitivityAnalysisResultUuid(UUID.randomUUID())
                .shortCircuitAnalysisResultUuid(UUID.randomUUID())
                .oneBusShortCircuitAnalysisResultUuid(UUID.randomUUID())
                .dynamicSimulationResultUuid(UUID.randomUUID())
                .dynamicSecurityAnalysisResultUuid(UUID.randomUUID())
                .voltageInitResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .build()
        );

        // duplicate the study
        StudyEntity duplicatedStudy = duplicateStudy(mockWebServer, study1Uuid);
        assertNotEquals(study1Uuid, duplicatedStudy.getId());

        // Verify that the network was cloned with only one variant
        List<VariantInfos> networkVariants = networkService.getNetworkVariants(CLONED_NETWORK_UUID);
        assertEquals(1, networkVariants.size(), "Network should be cloned with only one variant");

        //Test duplication from a non-existing source study
        mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", UUID.randomUUID())
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDuplicateStudyWithParametersUuid(final MockWebServer mockWebServer) throws Exception {
        UUID study1Uuid = createStudy(mockWebServer, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(UUID.randomUUID());
        studyEntity.setSecurityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID());
        studyEntity.setSensitivityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setStateEstimationParametersUuid(UUID.randomUUID());
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(mockWebServer, study1Uuid, firstRootNetworkUuid);
    }

    @Test
    void testDuplicateStudyWithoutParametersUuid(final MockWebServer mockWebServer) throws Exception {
        UUID study1Uuid = createStudy(mockWebServer, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setNetworkVisualizationParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(null);
        studyRepository.save(studyEntity);
        testDuplicateStudy(mockWebServer, study1Uuid, firstRootNetworkUuid);
    }

    @Test
    void testDuplicateStudyWithErrorDuringCaseDuplication(final MockWebServer mockWebServer) throws Exception {
        UUID studyUuid = createStudy(mockWebServer, "userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyRepository.save(studyEntity);

        doAnswer(invocation -> {
            throw new RuntimeException();
        }).when(caseService).duplicateCase(any(), any());

        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                        .param(CASE_FORMAT, "XIIDM")
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        String duplicatedStudyUuid = mapper.readValue(response, String.class);
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        assertNull(studyRepository.findById(UUID.fromString(duplicatedStudyUuid)).orElse(null));

        //now case are duplicated after parameters, case import error does not prevent parameters from being duplicated
        int numberOfRequests = 0;
        if (studyEntity.getSecurityAnalysisParametersUuid() != null) {
            numberOfRequests++;
        }
        if (studyEntity.getVoltageInitParametersUuid() != null) {
            numberOfRequests++;
        }
        if (studyEntity.getSensitivityAnalysisParametersUuid() != null) {
            numberOfRequests++;
        }
        if (studyEntity.getLoadFlowParametersUuid() != null) {
            numberOfRequests++;
        }
        if (studyEntity.getShortCircuitParametersUuid() != null) {
            numberOfRequests++;
        }
        if (studyEntity.getSpreadsheetConfigCollectionUuid() != null) {
            numberOfRequests++;
        }
        TestUtils.getRequestsWithBodyDone(numberOfRequests, mockWebServer);
    }

    private StudyEntity duplicateStudy(final MockWebServer server, UUID studyUuid) throws Exception {
        UUID stubUuid = wireMockUtils.stubDuplicateModificationGroup();
        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        String newUuid = mapper.readValue(response, String.class);
        StudyEntity sourceStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageOnGoing.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(StudyIndexationStatus.INDEXING_ONGOING.name(), indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));
        Message<byte[]> indexationStatusMessageDone = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageDone.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(StudyIndexationStatus.INDEXED.name(), indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        StudyEntity duplicatedStudy = studyRepository.findById(UUID.fromString(newUuid)).orElse(null);
        assertNotNull(duplicatedStudy);
        RootNode duplicatedRootNode = networkModificationTreeService.getStudyTree(UUID.fromString(newUuid), null);
        assertNotNull(duplicatedRootNode);

        //Check tree node has been duplicated
        assertEquals(1, duplicatedRootNode.getChildren().size());
        NetworkModificationNode duplicatedModificationNode = (NetworkModificationNode) duplicatedRootNode.getChildren().get(0);
        assertEquals(2, duplicatedModificationNode.getChildren().size());

        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getLoadFlowResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getSecurityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getSensitivityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getNonEvacuatedEnergyResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getStateEstimationResultUuid());

        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getLoadFlowResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSecurityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSensitivityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getNonEvacuatedEnergyResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getStateEstimationResultUuid());

        //Check requests to duplicate modification groups has been emitted (3 nodes)
        wireMockUtils.verifyDuplicateModificationGroup(stubUuid, 3);

        Set<RequestWithBody> requests;
        int numberOfRequests = 3;
        if (sourceStudy.getSecurityAnalysisParametersUuid() == null) {
            // if we don't have a securityAnalysisParametersUuid we don't call the security-analysis-server to duplicate them
            assertNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
        } else {
            // else we call the security-analysis-server to duplicate them
            assertNotNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getVoltageInitParametersUuid() == null) {
            assertNull(duplicatedStudy.getVoltageInitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getVoltageInitParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() == null) {
            assertNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            assertNotNull(duplicatedStudy.getLoadFlowParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() == null) {
            assertNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getShortCircuitParametersUuid() == null) {
            assertNull(duplicatedStudy.getShortCircuitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getShortCircuitParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getStateEstimationParametersUuid() == null) {
            assertNull(duplicatedStudy.getStateEstimationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getStateEstimationParametersUuid());
            numberOfRequests++;
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() == null) {
            assertNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
        } else {
            assertNotNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
            numberOfRequests++;
        }
        requests = TestUtils.getRequestsWithBodyDone(numberOfRequests, server);

        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(duplicatedStudy.getId());
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/networks/" + rootNetworkEntity.getNetworkUuid() + "/reindex-all")).count());
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/cases\\?duplicateFrom=.*&withExpiration=false")).count());
        if (sourceStudy.getVoltageInitParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getVoltageInitParametersUuid())).count());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getLoadFlowParametersUuid())).count());
        }
        if (sourceStudy.getShortCircuitParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getShortCircuitParametersUuid())).count());
        }
        if (sourceStudy.getSecurityAnalysisParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getSecurityAnalysisParametersUuid())).count());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getSensitivityAnalysisParametersUuid())).count());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/network-visualizations-params\\?duplicateFrom=" + sourceStudy.getNetworkVisualizationParametersUuid())).count());
        }
        if (sourceStudy.getStateEstimationParametersUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/parameters\\?duplicateFrom=" + sourceStudy.getStateEstimationParametersUuid())).count());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() != null) {
            assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + sourceStudy.getSpreadsheetConfigCollectionUuid())).count());
        }
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*/duplicate")).count());
        return duplicatedStudy;
    }

    @Test
    void testCutAndPasteNode(final MockWebServer mockWebServer) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(mockWebServer, "userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);
        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *       /  \
         *    node1 node2
         *
         */

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node1.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node2.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .build()
        );

        // node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // cut the node1 and paste it after node2
        cutAndPasteNode(study1Uuid, node1, node2.getId(), InsertMode.AFTER, 0, userId);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *       |
         *     node2
         *       |
         *     node1
         */

        //node2 should now have 1 child : node 1
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(List.of(node1.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        //modificationNode should now have 1 child : node2
        assertEquals(List.of(node2.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        // cut and paste the node2 before emptyNode
        cutAndPasteNode(study1Uuid, node2, emptyNode.getId(), InsertMode.BEFORE, 1, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        /*
         *              rootNode
         *              /      \
         * modificationNode   node2
         *       |              |
         *     node1        emptyNode
         */

        //rootNode should now have 2 children : modificationNode and node2
        assertEquals(List.of(modificationNodeUuid, node2.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(rootNode.getId()))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        //node1 parent should be modificiationNode
        assertEquals(List.of(node1.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        // emptyNode parent should be node2
        assertEquals(List.of(emptyNode.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        //cut and paste node2 in a new branch starting from modificationNode
        cutAndPasteNode(study1Uuid, node2, modificationNodeUuid, InsertMode.CHILD, 1, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *     /      \
         *  node1    node2
         */

        //modificationNode should now have 2 children : node1 and node2
        assertEquals(List.of(node1.getId(), node2.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));

        // modificationNode should now have 2 children : emptyNode and modificationNode
        assertEquals(List.of(modificationNodeUuid, emptyNode.getId()), allNodes.stream()
                .filter(nodeEntity ->
                           nodeEntity.getParentNode() != null
                        && nodeEntity.getParentNode().getIdNode().equals(rootNode.getId()))
                .map(NodeEntity::getIdNode)
                .collect(Collectors.toList()));
    }

    @Test
    void testCutAndPasteNodeAroundItself(final MockWebServer mockWebServer) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(mockWebServer, userId, CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);

        UUID stubGetCountUuid = wireMockUtils.stubNetworkModificationCountGet(node1.getModificationGroupUuid().toString(), 0);

        //try to cut and paste a node before itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.BEFORE)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockUtils.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.AFTER)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockUtils.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node in a new branch after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.CHILD)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockUtils.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());
    }

    @Test
    void testCutAndPasteNodeWithoutModification(final MockWebServer server) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(server, userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNodeChild = createNetworkModificationNode(study1Uuid, emptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "emptyNodeChild", BuildStatus.BUILT, userId);

        cutAndPasteNode(study1Uuid, emptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        Set<String> request = TestUtils.getRequestsDone(1, server);
        assertTrue(request.stream().allMatch(r -> r.matches("/v1/reports")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPasteNodeWithModification(final MockWebServer server) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(server, userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode notEmptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID_2, "notEmptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode notEmptyNodeChild = createNetworkModificationNode(study1Uuid, notEmptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "notEmptyNodeChild", BuildStatus.BUILT, userId);

        cutAndPasteNode(study1Uuid, notEmptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        Set<String> request = TestUtils.getRequestsDone(2, server);
        assertTrue(request.stream().allMatch(r -> r.matches("/v1/reports")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPastErrors(final MockWebServer mockWebServer) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(mockWebServer, "userId", CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);

        //try cut non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, UUID.randomUUID(), node1.getId(), InsertMode.AFTER)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        //try to cut to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), UUID.randomUUID(), InsertMode.AFTER)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        //try to cut and paste to before the root node and expect forbidden
        UUID stubUuid = wireMockUtils.stubNetworkModificationCountGet(node1.getModificationGroupUuid().toString(), 0);
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());

        wireMockUtils.verifyNetworkModificationCountsGet(stubUuid, node1.getModificationGroupUuid().toString());
    }

    @Test
    void testCutAndPasteSubtree(final MockWebServer server) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(server, userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNodeChild = createNetworkModificationNode(study1Uuid, emptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "emptyNodeChild", BuildStatus.BUILT, userId);

        mockMvc.perform(post(STUDIES_URL +
                                "/{studyUuid}/tree/subtrees?subtreeToCutParentNodeUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}",
                        study1Uuid, emptyNode.getId(), emptyNodeChild.getId())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(STUDIES_URL +
                                "/{studyUuid}/tree/subtrees?subtreeToCutParentNodeUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}",
                        study1Uuid, emptyNode.getId(), node1.getId())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(emptyNode.getId(), emptyNodeChild.getId()));

        //loadflow_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //securityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //sensitivityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //nonEvacuatedEnergy_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //shortCircuitAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //oneBusShortCircuitAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //dynamicSimulation_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //dynamicSecurityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //voltageInit_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //stateEstimation_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        checkSubtreeMovedMessageSent(study1Uuid, emptyNode.getId(), node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);

        var request = TestUtils.getRequestsDone(1, server);
        assertTrue(request.stream().allMatch(r -> r.matches("/v1/reports")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());

        mockMvc.perform(get(STUDIES_URL +
                        "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
                study1Uuid, emptyNode.getId())
                .header(USER_ID_HEADER, "userId")).andExpect(status().isOk());

        mockMvc.perform(get(STUDIES_URL +
                        "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
                study1Uuid, UUID.randomUUID())
                .header(USER_ID_HEADER, "userId")).andExpect(status().isNotFound());
    }

    @Test
    void testDuplicateNode(final MockWebServer mockWebServer) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(mockWebServer, userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node3", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node1.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node2.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .build()
        );

        //node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // duplicate the node1 after node2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study1Uuid, node1, node2.getId(), InsertMode.AFTER, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                        && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
                .count());

        // duplicate the node2 before node1
        UUID duplicatedNodeUuid2 = duplicateNode(study1Uuid, study1Uuid, node2, node1.getId(), InsertMode.BEFORE, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid2)
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .count());

        //now the tree looks like root -> modificationNode -> duplicatedNode2 -> node1 -> node2 -> duplicatedNode1
        //duplicate node1 in a new branch starting from duplicatedNode2
        UUID duplicatedNodeUuid3 = duplicateNode(study1Uuid, study1Uuid, node1, duplicatedNodeUuid2, InsertMode.CHILD, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        //expect to have modificationNode as a parent
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid3)
                        && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid2))
                .count());
        //and expect that no other node has the new branch create node as parent
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid3)).count());

        //try copy non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, UUID.randomUUID(), node1.getId(), InsertMode.AFTER, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        //try to copy to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), UUID.randomUUID(), InsertMode.AFTER, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        //try to copy to before the root node and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());

        // Test Built status when duplicating an empty node
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), rootNetworkUuid).getGlobalBuildStatus());
        duplicateNode(study1Uuid, study1Uuid, emptyNode, node3.getId(), InsertMode.BEFORE, userId);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testDuplicateSubtree(final MockWebServer server) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(server, userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(study1Uuid, node2.getId(), UUID.randomUUID(), VARIANT_ID, "node3", BuildStatus.BUILT, userId);
        NetworkModificationNode node4 = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        /*tree state
            root
            ├── root children
            │   └── node 1
            │       └── node 2
            │           └── node 3
            └── node 4
         */

        // add modification on node "node1" (not built) -> invalidation of node 3
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTwoWindingsTransformerAttributes)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node1.getId(), node3.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // Invalidation node 3
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), firstRootNetworkUuid).getGlobalBuildStatus());
        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/reports")).count());

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLoadAttributes)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node2.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), firstRootNetworkUuid,
            RootNetworkNodeInfo.builder()
                .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT))
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .build()
        );

        //node 4 should not have any children
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node4.getId())).count());

        // duplicate the node1 after node4
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(study1Uuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubDuplicateUuid = wireMockUtils.stubDuplicateModificationGroup();

        mockMvc.perform(post(STUDIES_URL +
                                "/{study1Uuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                        study1Uuid, node1.getId(), node4.getId(), study1Uuid)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        List<UUID> nodesAfterDuplication = networkModificationTreeService.getAllNodes(study1Uuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        nodesAfterDuplication.removeAll(allNodesBeforeDuplication);
        assertEquals(3, nodesAfterDuplication.size());

        checkSubtreeCreatedMessageSent(study1Uuid, nodesAfterDuplication.get(0), node4.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        wireMockUtils.verifyDuplicateModificationGroup(stubDuplicateUuid, 3);

        /*tree state
            root
            ├── root children
            │   └── node 1
            │       └── node 2
            │           └── node 3
            └── node 4
                └── node 1 duplicated
                    └── node 2 duplicated
                        └── node 3 duplicated
         */

        mockMvc.perform(get(STUDIES_URL +
                                "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
                        study1Uuid, nodesAfterDuplication.get(0))
                        .header(USER_ID_HEADER, "userId")).andExpect(status().isOk());

        mockMvc.perform(get(STUDIES_URL +
                        "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
                study1Uuid, UUID.randomUUID())
                .header(USER_ID_HEADER, "userId")).andExpect(status().isNotFound());

        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        //first root children should still have a children
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(node1.getId())
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .count());

        //node4 should now have 1 child
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(nodesAfterDuplication.get(0))
                        && nodeEntity.getParentNode().getIdNode().equals(node4.getId()))
                .count());

        //node2 should be built
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node2.getId(), firstRootNetworkUuid).getGlobalBuildStatus());
        //duplicated node2 should now be not built
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(nodesAfterDuplication.get(1), firstRootNetworkUuid).getGlobalBuildStatus());

        //try copy non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                                "/{targetStudyUuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                        study1Uuid, UUID.randomUUID(), node1.getId(), study1Uuid)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        //try to copy to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                                "/{targetStudyUuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                        study1Uuid, node1.getId(), UUID.randomUUID(), study1Uuid)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDuplicateNodeBetweenStudies(final MockWebServer mockWebServer) throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudy(mockWebServer, userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        UUID study2Uuid = createStudy(mockWebServer, userId, CASE_UUID);
        RootNode study2RootNode = networkModificationTreeService.getStudyTree(study2Uuid, null);
        UUID study2ModificationNodeUuid = study2RootNode.getChildren().get(0).getId();
        NetworkModificationNode study2Node2 = createNetworkModificationNode(study2Uuid, study2ModificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on study 1 node "node1"
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node1.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(List.of(Optional.empty())));
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node2.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        //study 2 node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId())).count());

        // duplicate the node1 from study 1 after node2 from study 2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study2Uuid, node1, study2Node2.getId(), InsertMode.AFTER, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                        && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId()))
                .count());
    }

    private void cutAndPasteNode(UUID studyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, int childCount, String userId) throws Exception {
        UUID stubUuid = wireMockUtils.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        mockMvc.perform(post(STUDIES_URL +
                "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                studyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode)
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        wireMockUtils.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        boolean nodeHasModifications = networkModificationTreeService.hasModifications(nodeToCopy.getId(), false);
        wireMockUtils.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        /*
         * moving node
         */
        //nodeMoved
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_MOVED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(nodeToCopy.getId(), message.getHeaders().get(NotificationService.HEADER_MOVED_NODE));
        assertEquals(insertMode.name(), message.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        if (nodeHasModifications) {
            /*
             * invalidating old children
             */
            IntStream.rangeClosed(1, childCount).forEach(i -> {
                //nodeUpdated
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //loadflow_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //securityAnalysis_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //sensitivityAnalysis_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //nonEvacuatedEnergy_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //shortCircuitAnalysis_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //oneBusShortCircuitAnalysis_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //dynamicSimulation_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //dynamicSecurityAnalysis_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //voltageInit_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
                //stateEstimation_status
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            });

            /*
             * invalidating new children
             */
            //nodeUpdated
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //loadflow_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //securityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //sensitivityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //sensitivityAnalysisonEvacuated_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //shortCircuitAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //oneBusShortCircuitAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //dynamicSimulation_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //dynamicSecurityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //voltageInit_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //stateEstimation_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        } else {
            /*
             * Invalidating moved node
             */
            //nodeUpdated
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        }
    }

    private UUID duplicateNode(UUID sourceStudyUuid, UUID targetStudyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, String userId) throws Exception {
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(targetStudyUuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubGetCountUuid = wireMockUtils.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        UUID stubDuplicateUuid = wireMockUtils.stubDuplicateModificationGroup();
        if (sourceStudyUuid.equals(targetStudyUuid)) {
            //if source and target are the same no need to pass sourceStudy param
            mockMvc.perform(post(STUDIES_URL +
                        "/{targetStudyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                    targetStudyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode)
                    .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        } else {
            mockMvc.perform(post(STUDIES_URL +
                            "/{targetStudyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                    targetStudyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode, sourceStudyUuid)
                    .header(USER_ID_HEADER, "userId"))
                    .andExpect(status().isOk());
        }

        List<UUID> nodesAfterDuplication = networkModificationTreeService.getAllNodes(targetStudyUuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        nodesAfterDuplication.removeAll(allNodesBeforeDuplication);
        assertEquals(1, nodesAfterDuplication.size());

        output.receive(TIMEOUT, studyUpdateDestination); // nodeCreated
        if (!EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid())) {
            output.receive(TIMEOUT, studyUpdateDestination); // nodeUpdated
        }
        checkUpdateModelsStatusMessagesReceived(targetStudyUuid, nodesAfterDuplication.get(0));
        checkElementUpdatedMessageSent(targetStudyUuid, userId);

        wireMockUtils.verifyNetworkModificationCountsGet(stubGetCountUuid, nodeToCopy.getModificationGroupUuid().toString());
        wireMockUtils.verifyDuplicateModificationGroup(stubDuplicateUuid, 1);

        return nodesAfterDuplication.get(0);
    }

    @Test
    void testGetDefaultProviders(final MockWebServer server) throws Exception {
        // related to LoadFlowTest::testGetDefaultProvidersFromProfile but without a user, so it doesn't use profiles
        mockMvc.perform(get("/v1/loadflow-default-provider")).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        mockMvc.perform(get("/v1/security-analysis-default-provider")).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        mockMvc.perform(get("/v1/sensitivity-analysis-default-provider")).andExpectAll(
                    status().isOk(),
                    content().string(DEFAULT_PROVIDER));
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().allMatch(r -> r.matches("/v1/default-provider")));

        mockMvc.perform(get("/v1/non-evacuated-energy-default-provider")).andExpectAll(
            status().isOk(),
            content().string(defaultNonEvacuatedEnergyProvider));
    }

    private void checkSubtreeMovedMessageSent(UUID studyUuid, UUID movedNodeUuid, UUID referenceNodeUuid) {
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.SUBTREE_MOVED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(movedNodeUuid, message.getHeaders().get(NotificationService.HEADER_MOVED_NODE));
        assertEquals(referenceNodeUuid, message.getHeaders().get(NotificationService.HEADER_PARENT_NODE));

    }

    private void checkSubtreeCreatedMessageSent(UUID studyUuid, UUID newNodeUuid, UUID referenceNodeUuid) {
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.SUBTREE_CREATED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(newNodeUuid, message.getHeaders().get(NotificationService.HEADER_NEW_NODE));
        assertEquals(referenceNodeUuid, message.getHeaders().get(NotificationService.HEADER_PARENT_NODE));

    }

    private void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    @Test
    void reindexStudyTest(final MockWebServer server) throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", UUID.randomUUID(), UUID.randomUUID()))
            .andExpectAll(status().isNotFound());

        UUID notExistingNetworkStudyUuid = createStudy(server, "userId", NOT_EXISTING_NETWORK_CASE_UUID);
        UUID notExistingNetworkRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(notExistingNetworkStudyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpect(status().isInternalServerError());
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        Message<byte[]> indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        var requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().contains("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/reindex-all")));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpectAll(status().isInternalServerError());

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertEquals(1, requests.stream().filter(r -> r.getPath().contains("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/indexed-equipments")).count());

        UUID study1Uuid = createStudy(server, "userId", CASE_UUID);
        UUID study1RootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", study1Uuid, study1RootNetworkUuid))
            .andExpectAll(status().isOk(),
                        content().string("NOT_INDEXED"));
        indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", study1Uuid, study1RootNetworkUuid))
            .andExpect(status().isOk());

        indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        Message<byte[]> indexationStatusMessageDone = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(study1Uuid, indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageDone.getHeaders().get(HEADER_UPDATE_TYPE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", study1Uuid, study1RootNetworkUuid))
            .andExpectAll(status().isOk(),
                        content().string("INDEXED"));

        requests = TestUtils.getRequestsWithBodyDone(4, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().contains("/v1/networks/" + NETWORK_UUID_STRING + "/reindex-all")));
        assertEquals(2, requests.stream().filter(r -> r.getPath().contains("/v1/networks/" + NETWORK_UUID_STRING + "/indexed-equipments")).count());
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/reports")).count());

        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(study1Uuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", study1Uuid, study1RootNetworkUuid))
            .andExpect(status().is5xxServerError());
        indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().contains("/v1/networks/" + NETWORK_UUID_STRING + "/reindex-all")));
    }

    @Test
    void providerTest(final MockWebServer server) throws Exception {
        UUID studyUuid = createStudy(server, USER_ID_HEADER, CASE_UUID);
        assertNotNull(studyUuid);
        mockMvc.perform(get("/v1/studies/{studyUuid}/non-evacuated-energy/provider", studyUuid))
            .andExpectAll(status().isOk(),
                content().string(defaultNonEvacuatedEnergyProvider));

        mockMvc.perform(post("/v1/studies/{studyUuid}/loadflow/provider", studyUuid)
                        .content("SuperLF")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(USER_ID_HEADER, USER_ID_HEADER))
                .andExpect(status().isOk());
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);

        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertNotNull(output.receive(TIMEOUT, elementUpdateDestination));

        mockMvc.perform(post("/v1/studies/{studyUuid}/security-analysis/provider", studyUuid)
                        .content("SuperSA")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(USER_ID_HEADER, USER_ID_HEADER))
                .andExpect(status().isOk());
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertNotNull(output.receive(TIMEOUT, elementUpdateDestination));

        mockMvc.perform(post("/v1/studies/{studyUuid}/non-evacuated-energy/provider", studyUuid)
                .content("SuperNEE")
                .contentType(MediaType.TEXT_PLAIN)
                .header(USER_ID_HEADER, USER_ID_HEADER))
            .andExpect(status().isOk());
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertNotNull(output.receive(TIMEOUT, elementUpdateDestination));

        mockMvc.perform(get("/v1/studies/{studyUuid}/non-evacuated-energy/provider", studyUuid))
            .andExpectAll(status().isOk(),
                content().string("SuperNEE"));

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().allMatch(r -> r.matches("/v1/parameters/.*/provider")));
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, elementUpdateDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
