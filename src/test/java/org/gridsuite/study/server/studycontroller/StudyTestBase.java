/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.model.VariantInfos;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.utils.TestUtils.synchronizeStudyServerExecutionService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Florent MILLOT {@literal <florent.millot_externe at rte-france.com>}
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyTestBase.class);

    protected static final String FIRST_VARIANT_ID = "first_variant_id";
    protected static final long TIMEOUT = 1000;
    protected static final String STUDIES_URL = "/v1/studies";
    protected static final String URI_NETWORK_MODIF = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications?rootNetworkUuid={rootNetworkUuid}";
    protected static final String TEST_FILE = "testCase.xiidm";
    protected static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00e";
    protected static final String CLONED_NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    protected static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    protected static final String CLONED_CASE_UUID_STRING = "22222222-1111-0000-0000-000000000000";
    protected static final String NEW_STUDY_CASE_UUID = "11888888-0000-0000-0000-000000000000";
    protected static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    protected static final String NOT_EXISTING_NETWORK_CASE_UUID_STRING = "00000000-0000-0000-0000-000000000001";
    protected static final String HEADER_UPDATE_TYPE = "updateType";
    protected static final String USER_ID_HEADER = "userId";
    protected static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    protected static final UUID CLONED_NETWORK_UUID = UUID.fromString(CLONED_NETWORK_UUID_STRING);
    protected static final UUID NOT_EXISTING_NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00f");
    protected static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    protected static final UUID NOT_EXISTING_NETWORK_CASE_UUID = UUID.fromString(NOT_EXISTING_NETWORK_CASE_UUID_STRING);
    protected static final UUID CLONED_CASE_UUID = UUID.fromString(CLONED_CASE_UUID_STRING);
    protected static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    protected static final NetworkInfos NOT_EXISTING_NETWORK_INFOS = new NetworkInfos(NOT_EXISTING_NETWORK_UUID, "not_existing_network_id");
    protected static final UUID REPORT_UUID = UUID.randomUUID();
    protected static final Report REPORT_TEST = Report.builder().id(REPORT_UUID).message("test").severity(StudyConstants.Severity.WARN).build();
    protected static final UUID REPORT_LOG_PARENT_UUID = UUID.randomUUID();
    protected static final UUID REPORT_ID = UUID.randomUUID();
    protected static final List<ReportLog> REPORT_LOGS = List.of(new ReportLog("test", StudyConstants.Severity.WARN, 0, REPORT_LOG_PARENT_UUID));
    protected static final ReportPage REPORT_PAGE = new ReportPage(0, REPORT_LOGS, 1, 1);
    protected static final String VARIANT_ID = "variant_1";
    protected static final String VARIANT_ID_2 = "variant_2";
    protected static final String VARIANT_ID_3 = "variant_3";
    protected static final String CASE_UUID_CAUSING_IMPORT_ERROR = "178719f5-cccc-48be-be46-e92345951e32";
    protected static final String CASE_UUID_CAUSING_STUDY_CREATION_ERROR = "278719f5-cccc-48be-be46-e92345951e32";
    protected static final String CASE_UUID_CAUSING_CONVERSION_ERROR = "278719f5-cccc-48be-be46-e92345951e33";
    protected static final UUID EMPTY_MODIFICATION_GROUP_UUID = UUID.randomUUID();
    protected static final String STUDY_CREATION_ERROR_MESSAGE = "Une erreur est survenue lors de la création de l'étude";
    protected static final String CASE_FORMAT = "caseFormat";
    protected static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    protected static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    protected static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    protected static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    protected static final String NAD_CONFIG_USER_ID = "nadConfigUser";
    protected static final String NAD_ELEMENT_NAME = "nadName";

    protected static final String PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    protected static final String PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "a09f5282-8e36-48b5-b66e-7ef9f3f36c4f";
    protected static final String PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "709f0282-8034-48b5-b66c-7ef9f3f36c4f";
    protected static final String PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING = "d09f5112-8e34-41b5-b45e-7ef9f3f36c4f";
    protected static final String PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING = "409f5782-8114-48b5-b66e-7ff9f3f36c4f";
    protected static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING = "473ff5ce-4378-8dd2-9d07-ce73c5ef11d9";
    protected static final String PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING = "407a4bec-6f1a-400f-98f0-e5bcf37d4fcf";

    protected static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"name\":\"Profile with broken params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING +
        "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING +
        "\",\"allLinksValid\":false}";

    protected static final String PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    protected static final String PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "2cec4a7b-ab7e-4d78-9dd2-ce73c5ef11d9";
    protected static final String PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-a87e-4d78-9da7-ce73c5ef11d9";
    protected static final String PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING = "5cec4a2b-affe-4d78-91d7-ce73c5ef11d9";
    protected static final String PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-ab74-5d78-9d07-ce73c5ef11d9";
    protected static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING = "2c865123-4378-8dd2-9d07-ce73c5ef11d9";
    protected static final String PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING = "207a4bec-6f1a-400f-98f0-e5bcf37d4fcf";
    protected static final String PROFILE_DIAGRAM_CONFIG_UUID_STRING = "518b5cac-6f1a-400f-98f0-e5bcf37d4fcf";

    protected static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING +
        "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING +
        "\",\"allLinksValid\":true}";

    protected static final String USER_PROFILE_WITH_DIAGRAM_CONFIG_PARAMS_JSON = "{\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING +
        "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING +
        "\",\"diagramConfigId\":\"" + PROFILE_DIAGRAM_CONFIG_UUID_STRING +
        "\",\"allLinksValid\":true}";

    protected static final String PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    protected static final String DUPLICATED_LOADFLOW_PARAMS_JSON = "\"" + PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "b4ce25e1-56a7-401d-acd1-04425fe24587";
    protected static final String DUPLICATED_SECURITY_ANALYSIS_PARAMS_JSON = "\"" + PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "f4ce37e1-59a7-501d-abb1-04425fe24587";
    protected static final String DUPLICATED_SENSITIVITY_ANALYSIS_PARAMS_JSON = "\"" + PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String PROFILE_SHORTCIRCUIT_DUPLICATED_PARAMETERS_UUID_STRING = "d4ce28e3-59a7-422d-abb1-04425fe24587";
    protected static final String DUPLICATED_SHORTCIRCUIT_PARAMS_JSON = "\"" + PROFILE_SHORTCIRCUIT_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING = "d4ce25e1-27a7-401d-a721-04425fe24587";
    protected static final String DUPLICATED_VOLTAGE_INIT_PARAMS_JSON = "\"" + PROFILE_VOLTAGE_INIT_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String NETWORK_VISUALIZATION_DUPLICATED_PARAMETERS_UUID_STRING = "407a4bec-6f1a-400f-98f0-e5bcf37d4fcf";
    protected static final String DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON = "\"" + NETWORK_VISUALIZATION_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    protected static final String SPREADSHEET_CONFIG_COLLECTION_UUID_STRING = "f4ce25e1-59a7-401d-abb1-04425fe24587";
    protected static final String DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON = "\"" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "\"";

    protected static final String DEFAULT_PROVIDER = "defaultProvider";
    protected static final UUID EXPORT_UUID = UUID.randomUUID();

    //output destinations
    protected final String studyUpdateDestination = "study.update";
    protected final String elementUpdateDestination = "element.update";

    // UTILS
    @Autowired
    protected ObjectMapper mapper;

    protected ObjectWriter objectWriter;

    // TEST UTILS
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected OutputDestination output;

    @Autowired
    protected InputDestination input;

    protected WireMockServer wireMockServer;

    protected WireMockStubs wireMockStubs;

    @Autowired
    protected TestUtils studyTestUtils;

    // SERVICES
    @Autowired
    protected NetworkService networkService;

    @Autowired
    protected NetworkConversionService networkConversionService;

    @Autowired
    protected NetworkModificationService networkModificationService;

    @Autowired
    protected ReportService reportService;

    @Autowired
    protected UserAdminService userAdminService;

    @Autowired
    protected SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    protected SecurityAnalysisService securityAnalysisService;

    @Autowired
    protected VoltageInitService voltageInitService;

    @Autowired
    protected LoadFlowService loadflowService;

    @Autowired
    protected ShortCircuitService shortCircuitService;

    @Autowired
    protected DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;

    @Autowired
    protected StateEstimationService stateEstimationService;

    @Autowired
    protected PccMinService pccMinService;

    @Autowired
    protected StudyConfigService studyConfigService;

    @Autowired
    protected SingleLineDiagramService singleLineDiagramService;

    @Autowired
    protected DirectoryService directoryService;

    @MockitoBean
    protected EquipmentInfosService equipmentInfosService;

    @MockitoBean
    protected StudyInfosService studyInfosService;

    @Autowired
    protected NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    protected RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @MockitoBean
    protected NetworkStoreService networkStoreService;

    @MockitoSpyBean
    protected StudyServerExecutionService studyServerExecutionService;

    @MockitoSpyBean
    ConsumerService consumeService;

    @MockitoSpyBean
    protected CaseService caseService;

    // REPOSITORIES
    @Autowired
    protected StudyRepository studyRepository;

    @Autowired
    protected StudyCreationRequestRepository studyCreationRequestRepository;

    @Autowired
    protected RootNetworkRepository rootNetworkRepository;

    private void initMockBeans(Network network) {
        when(equipmentInfosService.getEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("32"));
        when(equipmentInfosService.getEquipmentInfosCount(StudyTest.NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("16"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("8"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount(StudyTest.NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("4"));

        when(networkStoreService.cloneNetwork(StudyTest.NETWORK_UUID, List.of(VariantManagerConstants.INITIAL_VARIANT_ID))).thenReturn(network);
        when(networkStoreService.getNetworkUuid(network)).thenReturn(StudyTest.NETWORK_UUID);
        when(networkStoreService.getNetwork(StudyTest.NETWORK_UUID)).thenReturn(network);
        when(networkStoreService.getVariantsInfos(StudyTest.NETWORK_UUID))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0),
                new VariantInfos(StudyTest.VARIANT_ID, 1)));
        when(networkStoreService.getVariantsInfos(StudyTest.CLONED_NETWORK_UUID))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0)));

        doNothing().when(networkStoreService).deleteNetwork(StudyTest.NETWORK_UUID);

        // Synchronize for tests
        synchronizeStudyServerExecutionService(studyServerExecutionService);
    }

    private void initMockBeansNetworkNotExisting() {
        when(networkStoreService.cloneNetwork(StudyTest.NOT_EXISTING_NETWORK_UUID, Collections.emptyList())).thenThrow(new PowsyblException("Network " + StudyTest.NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(StudyTest.NOT_EXISTING_NETWORK_UUID)).thenThrow(new PowsyblException("Network " + StudyTest.NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(StudyTest.NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenThrow(new PowsyblException("Network " + StudyTest.NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(StudyTest.NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.NONE)).thenThrow(new PowsyblException("Network " + StudyTest.NOT_EXISTING_NETWORK_UUID + " not found"));

        doNothing().when(networkStoreService).deleteNetwork(StudyTest.NOT_EXISTING_NETWORK_UUID);
    }

    @BeforeEach
    void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", StudyTest.TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, StudyTest.VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        Network notExistingNetwork = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        notExistingNetwork.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, StudyTest.VARIANT_ID);
        notExistingNetwork.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeansNetworkNotExisting();

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockStubs = new WireMockStubs(wireMockServer);

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        wireMockServer.start();

        // Configure all services to use WireMock URL
        String baseUrl = wireMockServer.baseUrl();
        caseService.setCaseServerBaseUri(baseUrl);
        networkConversionService.setNetworkConversionServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        voltageInitService.setVoltageInitServerBaseUri(baseUrl);
        loadflowService.setLoadFlowServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        dynamicSecurityAnalysisClient.setBaseUri(baseUrl);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);
        pccMinService.setPccMinServerBaseUri(baseUrl);
        studyConfigService.setStudyConfigServerBaseUri(baseUrl);
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrl);
        directoryService.setDirectoryServerServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
    }

    protected UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    protected UUID createStudyWithStubs(String userId, UUID caseUuid) throws Exception {
        return createStudyWithStubs(userId, caseUuid, StudyTest.NETWORK_INFOS);
    }

    protected UUID createStudyWithStubs(String userId, UUID caseUuid, NetworkInfos networkInfos) throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(userId, null, caseUuid.toString());
        setupCreateParametersStubs();

        UUID studyUuid = createStudy(userId, caseUuid, networkInfos);

        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(1, 7, 1, 1);

        return studyUuid;
    }

    protected UUID createStudy(String userId, UUID caseUuid) throws Exception {
        return createStudy(userId, caseUuid, StudyTest.NETWORK_INFOS);
    }

    private UUID createStudy(String userId, UUID caseUuid, NetworkInfos networkInfos) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(caseUuid.toString(), StudyTest.FIRST_VARIANT_ID, networkInfos, "UCTE", countDownLatch);
        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(caseUuid.toString());

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                .header("userId", userId)
                .param(StudyTest.CASE_FORMAT, "UCTE"))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        countDownLatch.await();

        assertStudyCreation(studyUuid, userId);

        // Verify HTTP requests
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), StudyTest.FIRST_VARIANT_ID);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, caseUuid.toString());

        return studyUuid;
    }

    protected NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName, userId);
    }

    protected NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                    UUID modificationGroupUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
            modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.SECURITY, BuildStatus.NOT_BUILT, userId);
    }

    protected NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                    UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.SECURITY, buildStatus, userId);
    }

    protected NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                    UUID modificationGroupUuid, String variantId, String nodeName, NetworkModificationNodeType nodeType, BuildStatus buildStatus, String userId) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
            .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
            .nodeType(nodeType)
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

    protected void assertStudyCreation(UUID studyUuid, String userId, String... errorMessage) {
        Assertions.assertTrue(studyRepository.findById(studyUuid).isPresent());

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(StudyTest.TIMEOUT, studyUpdateDestination);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(StudyTest.HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(StudyTest.TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(StudyTest.HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(StudyTest.TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    protected void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
    }

    protected void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    protected void checkEquipmentCreatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    protected void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, elementUpdateDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            StudyTestBase.LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    // STUBS HELPERS
    protected record CreateStudyStubs(UUID stubUserProfileId, UUID stubCaseExistsId, UUID stubSendReportId,
                                      String userId, String caseUuid) {
        public void verify(WireMockStubs wireMockStubs) {
            if (stubCaseExistsId != null) {
                wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, caseUuid);
            }
            if (stubUserProfileId != null) {
                wireMockStubs.userAdminServerStubs.verifyUserProfile(stubUserProfileId, userId);
            }
            if (stubSendReportId != null) {
                wireMockStubs.verifySendReport(stubSendReportId);
            }
        }
    }

    protected record DuplicateParameterStubs(UUID stubParametersDuplicateFromId,
                                             UUID stubSpreadsheetConfigDuplicateFromId,
                                             UUID stubNetworkVisualizationParamsDuplicateFromId) {
        public void verify(WireMockStubs wireMockStubs, int parametersDuplicateFromNbRequests, int spreadsheetConfigDuplicateFromNbRequests, int networkVisualizationParamsDuplicateFromNbRequests) {
            if (stubParametersDuplicateFromId != null) {
                wireMockStubs.verifyParametersDuplicateFromAny(stubParametersDuplicateFromId, parametersDuplicateFromNbRequests);
            }
            if (stubSpreadsheetConfigDuplicateFromId != null) {
                wireMockStubs.verifySpreadsheetConfigDuplicateFromAny(stubSpreadsheetConfigDuplicateFromId, spreadsheetConfigDuplicateFromNbRequests);
            }
            if (stubNetworkVisualizationParamsDuplicateFromId != null) {
                wireMockStubs.verifyNetworkVisualizationParamsDuplicateFromAny(stubNetworkVisualizationParamsDuplicateFromId, networkVisualizationParamsDuplicateFromNbRequests);
            }
        }
    }

    protected record DeleteStudyStubs(UUID stubDeleteParametersId, UUID stubDeleteReportsId,
                                      UUID stubDeleteNetworkVisualizationParamsId,
                                      UUID stubDeleteSpreadsheetConfigCollectionId) {
        public void verify(WireMockStubs wireMockStubs, int deleteParametersNbRequests) {
            wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 2);
            wireMockStubs.verifyDeleteParameters(stubDeleteParametersId, deleteParametersNbRequests);
            wireMockStubs.verifyDeleteNetworkVisualizationParams(stubDeleteNetworkVisualizationParamsId);
            wireMockStubs.verifyDeleteSpreadsheetConfigCollection(stubDeleteSpreadsheetConfigCollectionId);
        }
    }

    protected CreateStudyStubs setupCreateStudyStubs(String userId, String userProfileJson, String caseUuid) {
        UUID stubUserProfileId = userProfileJson != null
            ? wireMockStubs.userAdminServerStubs.stubUserProfile(userId, userProfileJson)
            : wireMockStubs.userAdminServerStubs.stubUserProfile(userId);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(caseUuid, true);
        UUID stubSendReportId = wireMockStubs.stubSendReport();
        return new CreateStudyStubs(stubUserProfileId, stubCaseExistsId, stubSendReportId, userId, caseUuid);
    }

    protected void setupCreateParametersStubs() throws Exception {
        wireMockStubs.stubParameters(mapper.writeValueAsString(UUID.randomUUID()));
        wireMockStubs.stubParametersDefault(mapper.writeValueAsString(UUID.randomUUID()));
        wireMockStubs.stubSpreadsheetConfigDefault(mapper.writeValueAsString(UUID.randomUUID()));
        wireMockStubs.stubNetworkVisualizationParamsDefault(StudyTest.DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
    }

    protected void verifyCreateParameters(int createParametersNbRequests, int parametersDefaultNbRequests, int spreadsheetConfigDefaultNbRequests, int networkVisualizationParamsDefaultNbRequests) {
        wireMockStubs.computationServerStubs.verifyParameters(createParametersNbRequests);
        wireMockStubs.computationServerStubs.verifyParametersDefault(parametersDefaultNbRequests);
        wireMockStubs.verifySpreadsheetConfigDefault(spreadsheetConfigDefaultNbRequests);
        wireMockStubs.verifyNetworkVisualizationParamsDefault(networkVisualizationParamsDefaultNbRequests);
    }

    protected DuplicateParameterStubs setupDuplicateParametersStubs() throws Exception {
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(StudyTest.DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
        return new DuplicateParameterStubs(stubParametersDuplicateFromId, stubSpreadsheetConfigDuplicateFromId, stubNetworkVisualizationParamsDuplicateFromId);
    }

    protected DeleteStudyStubs setupDeleteStudyStubs() {
        UUID stubDeleteParametersId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/parameters/.*"))
            .willReturn(WireMock.ok())).getId();
        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        UUID stubDeleteNetworkVisualizationParamsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/network-visualizations-params/.*"))
            .willReturn(WireMock.ok())).getId();
        UUID stubDeleteSpreadsheetConfigCollectionId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/spreadsheet-config-collections/.*"))
            .willReturn(WireMock.ok())).getId();
        return new DeleteStudyStubs(stubDeleteParametersId, stubDeleteReportsId, stubDeleteNetworkVisualizationParamsId, stubDeleteSpreadsheetConfigCollectionId);
    }
}
