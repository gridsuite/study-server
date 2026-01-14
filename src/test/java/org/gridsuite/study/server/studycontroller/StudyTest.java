/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
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
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.elasticsearch.client.RestClient;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.networkexport.NetworkExportReceiver;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.MatcherReport;
import org.gridsuite.study.server.utils.MatcherReportLog;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.HEADER_ERROR;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.gridsuite.study.server.utils.TestUtils.USER_DEFAULT_PROFILE_JSON;
import static org.gridsuite.study.server.utils.TestUtils.synchronizeStudyServerExecutionService;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
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
    private static final String NOT_EXISTING_CASE_UUID = "00000000-0000-0000-0000-000000000000";
    private static final String NOT_EXISTING_NETWORK_CASE_UUID_STRING = "00000000-0000-0000-0000-000000000001";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final String USER_ID_HEADER = "userId";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final UUID CLONED_NETWORK_UUID = UUID.fromString(CLONED_NETWORK_UUID_STRING);
    private static final UUID NOT_EXISTING_NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00f");
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID NOT_EXISTING_NETWORK_CASE_UUID = UUID.fromString(NOT_EXISTING_NETWORK_CASE_UUID_STRING);
    private static final UUID CLONED_CASE_UUID = UUID.fromString(CLONED_CASE_UUID_STRING);
    private static final NetworkInfos NETWORK_INFOS = new NetworkInfos(NETWORK_UUID, "20140116_0830_2D4_UX1_pst");
    private static final NetworkInfos NOT_EXISTING_NETWORK_INFOS = new NetworkInfos(NOT_EXISTING_NETWORK_UUID, "not_existing_network_id");
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final Report REPORT_TEST = Report.builder().id(REPORT_UUID).message("test").severity(StudyConstants.Severity.WARN).build();
    private static final UUID REPORT_LOG_PARENT_UUID = UUID.randomUUID();
    private static final UUID REPORT_ID = UUID.randomUUID();
    private static final List<ReportLog> REPORT_LOGS = List.of(new ReportLog("test", StudyConstants.Severity.WARN, 0, REPORT_LOG_PARENT_UUID));
    private static final ReportPage REPORT_PAGE = new ReportPage(0, REPORT_LOGS, 1, 1);
    private static final String VARIANT_ID = "variant_1";
    private static final String POST = "POST";
    private static final String GET = "GET";
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
    private static final String NAD_CONFIG_USER_ID = "nadConfigUser";
    private static final String NAD_ELEMENT_NAME = "nadName";

    private static final String PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "a09f5282-8e36-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "709f0282-8034-48b5-b66c-7ef9f3f36c4f";
    private static final String PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING = "d09f5112-8e34-41b5-b45e-7ef9f3f36c4f";
    private static final String PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING = "409f5782-8114-48b5-b66e-7ff9f3f36c4f";
    private static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING = "473ff5ce-4378-8dd2-9d07-ce73c5ef11d9";
    private static final String PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING = "407a4bec-6f1a-400f-98f0-e5bcf37d4fcf";

    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"name\":\"Profile with broken params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING +
        "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING +
        "\",\"allLinksValid\":false}";

    private static final String PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "2cec4a7b-ab7e-4d78-9dd2-ce73c5ef11d9";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-a87e-4d78-9da7-ce73c5ef11d9";
    private static final String PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING = "5cec4a2b-affe-4d78-91d7-ce73c5ef11d9";
    private static final String PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING = "9cec4a7b-ab74-5d78-9d07-ce73c5ef11d9";
    private static final String PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING = "2c865123-4378-8dd2-9d07-ce73c5ef11d9";
    private static final String PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING = "207a4bec-6f1a-400f-98f0-e5bcf37d4fcf";
    private static final String PROFILE_DIAGRAM_CONFIG_UUID_STRING = "518b5cac-6f1a-400f-98f0-e5bcf37d4fcf";

    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" +
        PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING +
        "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
        "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING +
        "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING +
        "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING +
        "\",\"allLinksValid\":true}";

    private static final String USER_PROFILE_WITH_DIAGRAM_CONFIG_PARAMS_JSON = "{\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" +
            PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING +
            "\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
            "\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING +
            "\",\"shortcircuitParameterId\":\"" + PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING +
            "\",\"voltageInitParameterId\":\"" + PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING +
            "\",\"spreadsheetConfigCollectionId\":\"" + PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING +
            "\",\"networkVisualizationParameterId\":\"" + PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING +
            "\",\"diagramConfigId\":\"" + PROFILE_DIAGRAM_CONFIG_UUID_STRING +
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
    private static final UUID EXPORT_UUID = UUID.randomUUID();

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @MockitoSpyBean
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
    private DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;

    @Autowired
    private StateEstimationService stateEstimationService;

    @Autowired
    private PccMinService pccMinService;

    @Autowired
    private StudyConfigService studyConfigService;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @Autowired
    private DirectoryService directoryService;

    @MockitoBean
    private EquipmentInfosService equipmentInfosService;

    @MockitoBean
    private StudyInfosService studyInfosService;

    @Autowired
    private RestClient restClient;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    // new mock server (use this one to mock API calls)
    private WireMockServer wireMockServer;

    private WireMockStubs wireMockStubs;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;

    //used by testGetStudyCreationRequests to control asynchronous case import
    private CountDownLatch countDownLatch;

    @MockitoBean
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

    @MockitoSpyBean
    private StudyServerExecutionService studyServerExecutionService;

    @MockitoSpyBean
    ConsumerService consumeService;

    private static class CreateStudyStubs {
        UUID stubUserProfileId;
        UUID stubCaseExistsId;
        UUID stubSendReportId;
        String userId;
        String caseUuid;

        public void verify(WireMockStubs wireMockStubs) {
            if (stubCaseExistsId != null) {
                wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, caseUuid);
            }
            if (stubUserProfileId != null) {
                wireMockStubs.verifyUserProfile(stubUserProfileId, userId);
            }
            if (stubSendReportId != null) {
                wireMockStubs.verifySendReport(stubSendReportId);
            }
        }
    }

    private static class CreateParameterStubs {
        UUID stubCreateParametersId;
        UUID stubParametersDefaultId;
        UUID stubSpreadsheetConfigDefaultId;
        UUID stubNetworkVisualizationParamsDefaultId;

        public void verify(WireMockStubs wireMockStubs, int createParametersNbRequests, int parametersDefaultNbRequests, int spreadsheetConfigDefaultNbRequests, int networkVisualizationParamsDefaultNbRequests) {
            if (stubCreateParametersId != null) {
                wireMockStubs.verifyParameters(stubCreateParametersId, createParametersNbRequests);
            }
            if (stubParametersDefaultId != null) {
                wireMockStubs.verifyParametersDefault(stubParametersDefaultId, parametersDefaultNbRequests);
            }
            if (stubSpreadsheetConfigDefaultId != null) {
                wireMockStubs.verifySpreadsheetConfigDefault(stubSpreadsheetConfigDefaultId, spreadsheetConfigDefaultNbRequests);
            }
            if (stubNetworkVisualizationParamsDefaultId != null) {
                wireMockStubs.verifyNetworkVisualizationParamsDefault(stubNetworkVisualizationParamsDefaultId, networkVisualizationParamsDefaultNbRequests);
            }
        }
    }

    private static class DuplicateParameterStubs {
        UUID stubParametersDuplicateFromId;
        UUID stubSpreadsheetConfigDuplicateFromId;
        UUID stubNetworkVisualizationParamsDuplicateFromId;

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

    private static class DeleteStudyStubs {
        UUID stubDeleteParametersId;
        UUID stubDeleteReportsId;
        UUID stubDeleteNetworkVisualizationParamsId;
        UUID stubDeleteSpreadsheetConfigCollectionId;

        public void verify(WireMockStubs wireMockStubs, int deleteParametersNbRequests) {
            wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 2);
            wireMockStubs.verifyDeleteParameters(stubDeleteParametersId, deleteParametersNbRequests);
            wireMockStubs.verifyDeleteNetworkVisualizationParams(stubDeleteNetworkVisualizationParamsId);
            wireMockStubs.verifyDeleteSpreadsheetConfigCollection(stubDeleteSpreadsheetConfigCollectionId);
        }
    }

    private CreateStudyStubs stubCreateStudy(String userId, String userProfileJson, String caseUuid) throws Exception {
        CreateStudyStubs stubs = new CreateStudyStubs();
        stubs.userId = userId;
        stubs.caseUuid = caseUuid;
        if (userProfileJson != null) {
            stubs.stubUserProfileId = wireMockStubs.stubUserProfile(userId, userProfileJson);
        } else {
            stubs.stubUserProfileId = wireMockStubs.stubUserProfile(userId);
        }
        stubs.stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(caseUuid, true);
        stubs.stubSendReportId = wireMockStubs.stubSendReport();
        return stubs;
    }

    private CreateParameterStubs stubCreateParameters() throws Exception {
        CreateParameterStubs stubs = new CreateParameterStubs();
        stubs.stubCreateParametersId = wireMockStubs.stubParameters(mapper.writeValueAsString(UUID.randomUUID()));
        stubs.stubParametersDefaultId = wireMockStubs.stubParametersDefault(mapper.writeValueAsString(UUID.randomUUID()));
        stubs.stubSpreadsheetConfigDefaultId = wireMockStubs.stubSpreadsheetConfigDefault(mapper.writeValueAsString(UUID.randomUUID()));
        stubs.stubNetworkVisualizationParamsDefaultId = wireMockStubs.stubNetworkVisualizationParamsDefault(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
        return stubs;
    }

    private DuplicateParameterStubs stubDuplicateParameters() throws Exception {
        DuplicateParameterStubs stubs = new DuplicateParameterStubs();
        stubs.stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        stubs.stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        stubs.stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
        return stubs;
    }

    private DeleteStudyStubs stubDeleteStudy() {
        DeleteStudyStubs stubs = new DeleteStudyStubs();
        stubs.stubDeleteParametersId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/parameters/.*"))
            .willReturn(WireMock.ok())).getId();
        stubs.stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        stubs.stubDeleteNetworkVisualizationParamsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/network-visualizations-params/.*"))
            .willReturn(WireMock.ok())).getId();
        stubs.stubDeleteSpreadsheetConfigCollectionId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/spreadsheet-config-collections/.*"))
            .willReturn(WireMock.ok())).getId();
        return stubs;
    }

    private void initMockBeans(Network network) {
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

        // Synchronize for tests
        synchronizeStudyServerExecutionService(studyServerExecutionService);
    }

    private void initMockBeansNetworkNotExisting() {
        when(networkStoreService.cloneNetwork(NOT_EXISTING_NETWORK_UUID, Collections.emptyList())).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.COLLECTION)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));
        when(networkStoreService.getNetwork(NOT_EXISTING_NETWORK_UUID, PreloadingStrategy.NONE)).thenThrow(new PowsyblException("Network " + NOT_EXISTING_NETWORK_UUID + " not found"));

        doNothing().when(networkStoreService).deleteNetwork(NOT_EXISTING_NETWORK_UUID);
    }

    @BeforeEach
    void setup() {
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

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    @Test
    void test() throws Exception {
        MvcResult result;
        String resultAsString;
        String userId = "userId";
        UUID stubCaseNotExistsId = wireMockStubs.caseServer.stubCaseExists(NOT_EXISTING_CASE_UUID, false);

        //empty list
        mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //empty list
        mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //insert a study
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
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
                     .andExpectAll(status().isNotFound(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)).andReturn();
        var problemDetail = mapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals("The case '" + NOT_EXISTING_CASE_UUID + "' does not exist", problemDetail.getDetail());

        wireMockStubs.caseServer.verifyCaseExists(stubCaseNotExistsId, NOT_EXISTING_CASE_UUID);

        result = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId"))
                     .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(0), createMatcherCreatedStudyBasicInfos(studyUuid));

        //insert the same study but with another user (should work)
        //even with the same name should work
        studyUuid = createStudyWithStubs("userId2", CASE_UUID);

        resultAsString = mockMvc.perform(get("/v1/studies").header("userId", "userId2"))
                             .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();

        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(1),
            createMatcherCreatedStudyBasicInfos(studyUuid));

        UUID randomUuid = UUID.randomUUID();
        //get a non-existing study -> 404 not found
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", randomUuid).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isNotFound(),
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            .andReturn();
        problemDetail = mapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals("Study not found", problemDetail.getDetail());

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        // expect only 1 study (public one) since the other is private and we use
        // another userId
        result = mockMvc.perform(get("/v1/studies").header("userId", "a"))
                     .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(2, createdStudyBasicInfosList.size());

        //get available export format
        UUID stubExportFormatsId = wireMockStubs.networkConversionServer.stubExportFormats("[\"CGMES\",\"UCTE\",\"XIIDM\"]");
        mockMvc.perform(get("/v1/export-network-formats")).andExpectAll(status().isOk(),
            content().string("[\"CGMES\",\"UCTE\",\"XIIDM\"]"));

        wireMockStubs.networkConversionServer.verifyExportFormats(stubExportFormatsId);

        //export a network
        UUID stubNetworkExportId = wireMockStubs.networkConversionServer.stubNetworkExport(NETWORK_UUID_STRING, "XIIDM", EXPORT_UUID.toString());
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM").header(HEADER_USER_ID, userId)).andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("fileName", WireMock.equalTo("myFileName")));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName&formatParameters=%7B%22iidm.export.xml.indent%22%3Afalse%7D", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM")
                        .header(HEADER_USER_ID, userId)).andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("fileName", WireMock.equalTo("myFileName")));

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 3", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, "XIIDM")
                        .header(HEADER_USER_ID, userId)).andExpect(status().isInternalServerError());

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode1.getId(), studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid),
            RootNetworkNodeInfo.builder().nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT)).build());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName",
                        studyNameUserIdUuid,
                        firstRootNetworkUuid,
                        modificationNode1Uuid,
                        "XIIDM").header(HEADER_USER_ID, userId))
            .andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("variantId", WireMock.equalTo(VARIANT_ID), "fileName", WireMock.equalTo("myFileName")));
    }

    @Test
    void testExportNetworkErrors() throws Exception {
        //insert a study
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        UUID stubNetworkExportErrorId = wireMockStubs.networkConversionServer.stubNetworkExportError(NETWORK_UUID_STRING, "ERROR");

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
                        studyUuid, firstRootNetworkUuid, rootNodeUuid, "ERROR")
                        .param("fileName", "myFileName")
                        .header(HEADER_USER_ID, "userId"))
                .andExpect(status().is5xxServerError());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportErrorId, NETWORK_UUID_STRING, "ERROR",
            Map.of("fileName", WireMock.equalTo("myFileName")));
    }

    @Test
    void testConsumeNetworkExportFinishedSuccess() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        NetworkExportReceiver receiver = new NetworkExportReceiver(studyUuid, userId);
        String receiverJson = mapper.writeValueAsString(receiver);
        String encodedReceiver = URLEncoder.encode(receiverJson, StandardCharsets.UTF_8);
        String errorMessage = "error";
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_RECEIVER, encodedReceiver);
        headers.put(HEADER_EXPORT_UUID, EXPORT_UUID.toString());
        headers.put(HEADER_ERROR, errorMessage);
        Message<String> message = new GenericMessage<>("", headers);
        consumeService.consumeNetworkExportFinished(message);
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        assertEquals(EXPORT_UUID, mess.getHeaders().get(HEADER_EXPORT_UUID));
    }

    @Test
    void testCreateStudyWithDuplicateCase() throws Exception {
        createStudyWithDuplicateCase("userId", CASE_UUID);
    }

    @Test
    void testDeleteStudy() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID()); // does not have default params
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();
        UUID stubDeleteCaseId = wireMockStubs.caseServer.stubDeleteCase(CASE_UUID_STRING);
        DeleteStudyStubs deleteStudyStubs = stubDeleteStudy();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        wireMockStubs.caseServer.verifyDeleteCase(stubDeleteCaseId, CASE_UUID_STRING);
        deleteStudyStubs.verify(wireMockStubs, 7); // voltageInit, loadFlow, securityAnalysis, sensitivityAnalysis, stateEstimation, pccMin, dynamic
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testDeleteStudyWithError(final CapturedOutput capturedOutput) throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setPccMinParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        // We ignore error when remote data async remove
        // Log only
        doAnswer(invocation -> {
            throw new InterruptedException();
        }).when(caseService).deleteCase(any());
        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();
        DeleteStudyStubs deleteStudyStubs = stubDeleteStudy();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
                .andExpectAll(status().isOk());
        assertTrue(capturedOutput.getOut().contains(StudyServerExecutionService.class.getName() + " - " + CompletionException.class.getName() + ": " + InterruptedException.class.getName()));

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        deleteStudyStubs.verify(wireMockStubs, 1); // loadflow, security, sensitivity, stateEstimation, shortCircuit, pccMin
    }

    @Test
    void testDeleteStudyWithNonExistingCase() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID nonExistingCaseUuid = UUID.randomUUID();

        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();

        // Changing the study case uuid with a non-existing case
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElse(null);
        assertNotNull(studyEntity);
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(studyUuid);
        rootNetworkEntity.setCaseUuid(nonExistingCaseUuid);
        rootNetworkRepository.save(rootNetworkEntity);

        UUID stubDeleteCaseId = wireMockStubs.caseServer.stubDeleteCase(nonExistingCaseUuid.toString());
        DeleteStudyStubs deleteStudyStubs = stubDeleteStudy();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        wireMockStubs.caseServer.verifyDeleteCase(stubDeleteCaseId, nonExistingCaseUuid.toString());
        deleteStudyStubs.verify(wireMockStubs, 7);
    }

    @Test
    void testMetadata() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudyWithStubs("userId2", CASE_UUID);

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
        mockMvc.perform(post("/v1/studies/{studyUuid}/notification", studyUuid)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkStudyMetadataUpdatedMessagesReceived();
    }

    @Test
    void testLogsReport() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        UUID stubGetReportId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/reports/[^/]+"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_TEST)))).getId();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?reportType=NETWORK_MODIFICATION", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<Report> reports = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(1, reports.size());
        assertThat(reports.get(0), new MatcherReport(REPORT_TEST));
        wireMockStubs.verifyGetReport(stubGetReportId);
    }

    @Test
    void testGetNodeReportLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        UUID stubGetReportLogsId = wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID, studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.getFirst()));
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, REPORT_ID.toString());

        //test with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.getFirst()));
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, REPORT_ID.toString(), "WARN", "testMsgFilter");
    }

    @Test
    void testGetPagedNodeReportLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        UUID stubGetReportLogsId = wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&paged=true&page=1&size=10", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsPaged(stubGetReportLogsId, REPORT_ID.toString(), 1, 10);

        //test with severityFilter and messageFilter param
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&paged=true&page=1&size=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsPaged(stubGetReportLogsId, REPORT_ID.toString(), 1, 10, "WARN", "testMsgFilter");
    }

    @Test
    void testGetSearchTermMatchesInFilteredLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        UUID stubGetReportLogsId = wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?reportId=" + REPORT_ID + "&searchTerm=testTerm&pageSize=10", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearchWithReportId(stubGetReportLogsId, REPORT_ID.toString(), "testTerm", 10);

        //test with severityFilter and messageFilter param
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?reportId=" + REPORT_ID + "&searchTerm=testTerm&pageSize=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearchWithReportId(stubGetReportLogsId, REPORT_ID.toString(), "testTerm", 10, "WARN", "testMsgFilter");
    }

    @Test
    void testGetSearchTermMatchesInMultipleFilteredLogs() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().getFirst().getId();
        AbstractNode modificationNode = rootNode.getChildren().getFirst();
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        UUID rootNodeReportId = networkModificationTreeService.getReportUuid(rootNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID modificationNodeReportId = networkModificationTreeService.getReportUuid(modificationNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node1ReportId = networkModificationTreeService.getReportUuid(node1.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node2ReportId = networkModificationTreeService.getReportUuid(node2.getId(), firstRootNetworkUuid).orElseThrow();

        UUID stubGetReportLogsSearchId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/reports/logs/search"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_PAGE)))).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?searchTerm=testTerm&pageSize=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearch(stubGetReportLogsSearchId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));
    }

    @Test
    void testGetParentNodesReportLogs() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        AbstractNode modificationNode = rootNode.getChildren().get(0);
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID_3, "node3", userId);
        UUID rootNodeReportId = networkModificationTreeService.getReportUuid(rootNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID modificationNodeReportId = networkModificationTreeService.getReportUuid(modificationNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node1ReportId = networkModificationTreeService.getReportUuid(node1.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node2ReportId = networkModificationTreeService.getReportUuid(node2.getId(), firstRootNetworkUuid).orElseThrow();

        UUID stubGetReportLogsId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/reports/logs"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_PAGE)))).getId();

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
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));

        //get logs of node2 and all its parents (should not get node3 logs) with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.SECURITY, BuildStatus.NOT_BUILT, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.SECURITY, buildStatus, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
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

    private UUID createStudyWithStubs(String userId, UUID caseUuid) throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(userId, null, caseUuid.toString());
        CreateParameterStubs createParameterStubs = stubCreateParameters();

        UUID studyUuid = createStudy(userId, caseUuid);

        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 1, 7, 1, 1);

        return studyUuid;
    }

    private UUID createStudy(String userId, UUID caseUuid) throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(caseUuid.toString(), FIRST_VARIANT_ID, NETWORK_INFOS, "UCTE", countDownLatch);
        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(caseUuid.toString());

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        countDownLatch.await();

        assertStudyCreation(studyUuid, userId);

        // Verify HTTP requests
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), FIRST_VARIANT_ID);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, caseUuid.toString());

        return studyUuid;
    }

    private UUID createStudyWithDuplicateCase(String userId, UUID caseUuid) throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(userId, null, caseUuid.toString());
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        UUID stubDisableCaseExpirationClonedId = wireMockStubs.caseServer.stubDisableCaseExpiration(CLONED_CASE_UUID_STRING);
        UUID stubDuplicateCaseId = wireMockStubs.caseServer.stubDuplicateCaseWithBody(CASE_UUID_STRING, mapper.writeValueAsString(CLONED_CASE_UUID));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        // When duplicateCase=true, the network is imported with the cloned case UUID, not the original one
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(CLONED_CASE_UUID_STRING, FIRST_VARIANT_ID, NETWORK_INFOS, "UCTE", countDownLatch);

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                .param("duplicateCase", "true")
                .param(CASE_FORMAT, "UCTE")
                .header("userId", userId))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        countDownLatch.await();

        assertStudyCreation(studyUuid, userId);

        // Verify HTTP requests
        // note: it's a new case UUID
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, CLONED_CASE_UUID_STRING, FIRST_VARIANT_ID);
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 1, 7, 1, 1);
        wireMockStubs.caseServer.verifyDuplicateCase(stubDuplicateCaseId, caseUuid.toString());
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationClonedId, CLONED_CASE_UUID_STRING);
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
    void testCreateStudyWithErrorDuringCaseImport() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkServerErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithServerError(CASE_UUID_CAUSING_IMPORT_ERROR, FIRST_VARIANT_ID, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_IMPORT_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_IMPORT_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().isInternalServerError());

        countDownLatch.await();

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

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_IMPORT_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkServerErrorId, CASE_UUID_CAUSING_IMPORT_ERROR, FIRST_VARIANT_ID);
    }

    @Test
    void testCreateStudyCreationFailedWithoutErrorMessage() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkConversionErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithError(CASE_UUID_CAUSING_CONVERSION_ERROR, FIRST_VARIANT_ID, null, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_CONVERSION_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_CONVERSION_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "XIIDM"))
                .andExpect(status().isOk());

        countDownLatch.await();

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

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_CONVERSION_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkConversionErrorId, CASE_UUID_CAUSING_CONVERSION_ERROR, FIRST_VARIANT_ID);
    }

    @Test
    void testCreateStudyWithErrorDuringStudyCreation() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkConversionErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithError(CASE_UUID_CAUSING_STUDY_CREATION_ERROR, FIRST_VARIANT_ID, STUDY_CREATION_ERROR_MESSAGE, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_STUDY_CREATION_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_STUDY_CREATION_ERROR)
                        .header("userId", userId)
                        .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().isOk());

        countDownLatch.await();

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

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_STUDY_CREATION_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkConversionErrorId, CASE_UUID_CAUSING_STUDY_CREATION_ERROR, FIRST_VARIANT_ID);
    }

    @SuppressWarnings({"java:S1481", "java:S1854"}) //TODO found better way to test json result that Sonar wouldn't flag because of unused/useless local variables
    @Test
    void testGetStudyCreationRequests() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        CountDownLatch countDownLatch = new CountDownLatch(1);

        CreateStudyStubs createStudyStubs = stubCreateStudy("userId", USER_DEFAULT_PROFILE_JSON, NEW_STUDY_CASE_UUID);
        CreateParameterStubs createParameterStubs = stubCreateParameters();

        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(NEW_STUDY_CASE_UUID, FIRST_VARIANT_ID, NETWORK_INFOS, "UCTE", countDownLatch);
        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(NEW_STUDY_CASE_UUID);

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

        // Verify HTTP requests were sent to remote services
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 1, 7, 1, 1);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, NEW_STUDY_CASE_UUID);
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, NEW_STUDY_CASE_UUID, FIRST_VARIANT_ID);
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
    }

    private void checkNodeBuildStatusUpdatedMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(new TreeSet<>(nodesUuids), new TreeSet<>((List) headersStatus.get(NotificationService.HEADER_NODES)));
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

    private void checkNodeAliasUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_SPREADSHEET_NODE_ALIASES, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
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
    void testCreateStudyWithDefaultLoadflowUserHasNoParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();

        createStudy(NO_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 1, 7, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromNotFoundId = wireMockStubs.stubParametersDuplicateFromNotFound(PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 4, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromNotFoundId, PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFrom(PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING, DUPLICATED_LOADFLOW_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromNotFoundId = wireMockStubs.stubParametersDuplicateFromNotFound(PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 4, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromNotFoundId, PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SECURITY_ANALYSIS_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromNotFoundId = wireMockStubs.stubParametersDuplicateFromNotFound(PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 4, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromNotFoundId, PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SENSITIVITY_ANALYSIS_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromNotFoundId = wireMockStubs.stubParametersDuplicateFromNotFound(PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 4, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromNotFoundId, PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFrom(PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SHORTCIRCUIT_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubSpreadsheetConfigDuplicateFromNotFoundId = wireMockStubs.stubSpreadsheetConfigDuplicateFromNotFound(PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 1, 0);
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromNotFoundId, PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 0, 1);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFrom(PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING, DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 0, 1);
    }

    @Test
    void testCreateStudyWithDefaultNetworkVisualizationsParametersUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubNetworkVisualizationParamsDuplicateFromNotFoundId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromNotFound(PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 1);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromNotFoundId, PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultNetworkVisualizationsParametersUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFrom(PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING, DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromNotFoundId = wireMockStubs.stubParametersDuplicateFromNotFound(PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 1, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromNotFoundId, PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = stubCreateStudy(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        CreateParameterStubs createParameterStubs = stubCreateParameters();
        DuplicateParameterStubs duplicateParameterStubs = stubDuplicateParameters();
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFrom(PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING, DUPLICATED_VOLTAGE_INIT_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        createParameterStubs.verify(wireMockStubs, 0, 3, 0, 0);
        wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1);
    }

    private void testDuplicateStudy(UUID study1Uuid, UUID rootNetworkUuid, String userId) throws Exception {
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        UUID stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), rootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), rootNetworkUuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createLoadAttributes)
            .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

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
                .pccMinResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        // add node aliases to study
        List<NodeAlias> aliases = List.of(new NodeAlias(null, "alias1", null),
            new NodeAlias(node1.getId(), "alias2", "node1"),
            new NodeAlias(node2.getId(), "alias3", "node2"));
        mockMvc.perform(post("/v1/studies/{studyUuid}/node-aliases", study1Uuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectWriter.writeValueAsString(aliases))
        ).andExpect(status().isOk());
        checkNodeAliasUpdateMessageReceived(study1Uuid);

        // duplicate the study
        StudyEntity duplicatedStudy = duplicateStudy(study1Uuid, userId);
        assertNotEquals(study1Uuid, duplicatedStudy.getId());

        // Verify node aliases on the duplicated study
        aliases = mapper.readValue(mockMvc.perform(get("/v1/studies/{studyUuid}/node-aliases", duplicatedStudy.getId())).andExpect(status().isOk()).andReturn()
            .getResponse()
            .getContentAsString(), new TypeReference<>() {
            });
        assertEquals(3, aliases.size());
        assertEquals("alias1", aliases.get(0).alias());
        assertNull(aliases.get(0).id());
        assertNull(aliases.get(0).name());
        assertEquals("alias2", aliases.get(1).alias());
        assertEquals("node1", aliases.get(1).name());
        assertEquals("alias3", aliases.get(2).alias());
        assertEquals("node2", aliases.get(2).name());

        // Verify that the network was cloned with only one variant
        List<VariantInfos> networkVariants = networkService.getNetworkVariants(CLONED_NETWORK_UUID);
        assertEquals(1, networkVariants.size(), "Network should be cloned with only one variant");

        //Test duplication from a non-existing source study
        mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", UUID.randomUUID())
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDuplicateStudyWithParametersUuid() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(UUID.randomUUID());
        studyEntity.setSecurityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID());
        studyEntity.setSensitivityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setStateEstimationParametersUuid(UUID.randomUUID());
        studyEntity.setPccMinParametersUuid(UUID.randomUUID());
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
    }

    @Test
    void testDuplicateStudyWithGridLayout() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(UUID.randomUUID());
        studyEntity.setSecurityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID());
        studyEntity.setSensitivityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setStateEstimationParametersUuid(UUID.randomUUID());
        studyEntity.setPccMinParametersUuid(UUID.randomUUID());
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, NAD_CONFIG_USER_ID);
    }

    @Test
    void testDuplicateStudyWithoutParametersUuid() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setPccMinParametersUuid(null);
        studyEntity.setNetworkVisualizationParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(null);
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
    }

    @Test
    void testDuplicateStudyWithErrorDuringCaseDuplication() throws Exception {
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyRepository.save(studyEntity);

        doAnswer(invocation -> {
            throw new RuntimeException();
        }).when(caseService).duplicateCase(any(), any());

        UUID stubUserProfileNotFoundId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/userId/profile"))
            .willReturn(WireMock.notFound())).getId();

        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                        .param(CASE_FORMAT, "XIIDM")
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = new ObjectMapper();
        String duplicatedStudyUuid = mapper.readValue(response, String.class);
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        assertTrue(studyRepository.findById(UUID.fromString(duplicatedStudyUuid)).isEmpty());

        //now case are duplicated after parameters, case import error does not prevent parameters from being duplicated
        wireMockStubs.verifyParametersDuplicateFromAny(stubParametersDuplicateFromId, 7);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, studyEntity.getNetworkVisualizationParametersUuid().toString());
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, studyEntity.getSpreadsheetConfigCollectionUuid().toString());
        wireMockStubs.verifyUserProfile(stubUserProfileNotFoundId, "userId");
    }

    private StudyEntity duplicateStudy(UUID studyUuid, String userId) throws Exception {
        // Network reindex stubs - using scenarios for stateful behavior
        UUID stubReindexAllId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("indexed")
            .willReturn(WireMock.ok())).getId();
        UUID stubUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));
        UUID stubUserProfileId = wireMockStubs.stubUserProfile(userId);
        UUID stubDuplicateCaseId = wireMockStubs.caseServer.stubDuplicateCaseWithBody(CASE_UUID_STRING, mapper.writeValueAsString(CLONED_CASE_UUID));
        // Reports stubs
        UUID stubReportsDuplicateId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/reports/.*/duplicate"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();

        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        //TODO ?
//        // Diagram grid layout stubs
//        UUID stubDiagramGridLayoutId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/diagram-grid-layout"))
//            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();
//
//        // Network area diagram config stubs
//        UUID stubNetworkAreaDiagramConfigId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-area-diagram/config"))
//            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
//            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();
//
//        // Directory elements stubs
//        UUID stubElementNameId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/elements/" + PROFILE_DIAGRAM_CONFIG_UUID_STRING + "/name"))
//            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(NAD_ELEMENT_NAME)))).getId();

        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        ObjectMapper mapper = new ObjectMapper();
        String newUuid = mapper.readValue(response, String.class);
        StudyEntity sourceStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageOnGoing.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(RootNetworkIndexationStatus.INDEXING_ONGOING.name(), indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));
        Message<byte[]> indexationStatusMessageDone = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageDone.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(RootNetworkIndexationStatus.INDEXED.name(), indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));

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
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getStateEstimationResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getPccMinResultUuid());

        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getLoadFlowResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSecurityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSensitivityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getStateEstimationResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getPccMinResultUuid());

        //Check requests to duplicate modification groups has been emitted (3 nodes)
        wireMockStubs.verifyDuplicateModificationGroup(stubUuid, 3);

        if (sourceStudy.getSecurityAnalysisParametersUuid() == null) {
            // if we don't have a securityAnalysisParametersUuid we don't call the security-analysis-server to duplicate them
            assertNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
        } else {
            // else we call the security-analysis-server to duplicate them
            assertNotNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
        }
        if (sourceStudy.getVoltageInitParametersUuid() == null) {
            assertNull(duplicatedStudy.getVoltageInitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getVoltageInitParametersUuid());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() == null) {
            assertNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            assertNotNull(duplicatedStudy.getLoadFlowParametersUuid());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() == null) {
            assertNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
        }
        if (sourceStudy.getShortCircuitParametersUuid() == null) {
            assertNull(duplicatedStudy.getShortCircuitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getShortCircuitParametersUuid());
        }
        if (sourceStudy.getStateEstimationParametersUuid() == null) {
            assertNull(duplicatedStudy.getStateEstimationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getStateEstimationParametersUuid());
        }
        if (sourceStudy.getPccMinParametersUuid() == null) {
            assertNull(duplicatedStudy.getPccMinParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getPccMinParametersUuid());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() == null) {
            assertNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
        } else {
            assertNotNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
        }

        // Verify HTTP requests
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(duplicatedStudy.getId());
        wireMockStubs.verifyReindexAll(stubReindexAllId, rootNetworkEntity.getNetworkUuid().toString());
        wireMockStubs.caseServer.verifyDuplicateCase(stubDuplicateCaseId, CASE_UUID_STRING, "false");
        wireMockStubs.verifyUserProfile(stubUserProfileId, userId);
        if (sourceStudy.getVoltageInitParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getVoltageInitParametersUuid().toString());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getLoadFlowParametersUuid().toString());
        }
        if (sourceStudy.getShortCircuitParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getShortCircuitParametersUuid().toString());
        }
        if (sourceStudy.getSecurityAnalysisParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getSecurityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getSensitivityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() != null) {
            wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, sourceStudy.getNetworkVisualizationParametersUuid().toString());
        }
        if (sourceStudy.getStateEstimationParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getStateEstimationParametersUuid().toString());
        }
        if (sourceStudy.getPccMinParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(stubParametersDuplicateFromId, sourceStudy.getPccMinParametersUuid().toString());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() != null) {
            wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, sourceStudy.getSpreadsheetConfigCollectionUuid().toString());
        }
        if (NAD_CONFIG_USER_ID.equals(userId)) {
            //TODO why broken ?
//            wireMockStubs.verifyNetworkAreaDiagramConfig(stubNetworkAreaDiagramConfigId);
//            wireMockStubs.verifyElementNameGet(stubElementNameId, PROFILE_DIAGRAM_CONFIG_UUID_STRING);
//            wireMockStubs.verifyDiagramGridLayout(stubDiagramGridLayoutId);
        }
        wireMockStubs.verifyReportsDuplicate(stubReportsDuplicateId);

        return duplicatedStudy;
    }

    @Test
    void testCutAndPasteNode() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
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
        UUID stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
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
    void testCutAndPasteNodeAroundItself() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);

        UUID stubGetCountUuid = wireMockStubs.stubNetworkModificationCountGet(node1.getModificationGroupUuid().toString(), 0);

        //try to cut and paste a node before itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.BEFORE)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.AFTER)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node in a new branch after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
            study1Uuid, node1.getId(), node1.getId(), InsertMode.CHILD)
            .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());
    }

    @Test
    void testCutAndPasteNodeWithoutModification() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNodeChild = createNetworkModificationNode(study1Uuid, emptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "emptyNodeChild", BuildStatus.BUILT, userId);

        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();

        cutAndPasteNode(study1Uuid, emptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPasteNodeWithModification() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode notEmptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID_2, "notEmptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode notEmptyNodeChild = createNetworkModificationNode(study1Uuid, notEmptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "notEmptyNodeChild", BuildStatus.BUILT, userId);

        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();

        cutAndPasteNode(study1Uuid, notEmptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 2);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPastErrors() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
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
        mockMvc.perform(post(STUDIES_URL +
                        "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCutAndPasteSubtree() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
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

        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        mockMvc.perform(post(STUDIES_URL +
                                "/{studyUuid}/tree/subtrees?subtreeToCutParentNodeUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}",
                        study1Uuid, emptyNode.getId(), node1.getId())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub);

        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(emptyNode.getId(), emptyNodeChild.getId()));
        checkComputationStatusMessageReceived();

        checkSubtreeMovedMessageSent(study1Uuid, emptyNode.getId(), node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

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

    private void checkComputationStatusMessageReceived() {
        //loadflow_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //securityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //sensitivityAnalysis_status
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
        //pccMin_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
    }

    @Test
    void testDuplicateNode() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node3", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        //node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // duplicate the node1 after node2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study1Uuid, node1, node2.getId(), InsertMode.AFTER, true, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                        && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
                .count());

        // duplicate the node2 before node1
        UUID duplicatedNodeUuid2 = duplicateNode(study1Uuid, study1Uuid, node2, node1.getId(), InsertMode.BEFORE, true, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid2)
                        && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
                .count());

        //now the tree looks like root -> modificationNode -> duplicatedNode2 -> node1 -> node2 -> duplicatedNode1
        //duplicate node1 in a new branch starting from duplicatedNode2
        UUID duplicatedNodeUuid3 = duplicateNode(study1Uuid, study1Uuid, node1, duplicatedNodeUuid2, InsertMode.CHILD, true, userId);
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
        duplicateNode(study1Uuid, study1Uuid, emptyNode, node3.getId(), InsertMode.BEFORE, false, userId);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testDuplicateSubtree() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
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
        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        UUID stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createTwoWindingsTransformerAttributes)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node3.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));
        wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub);

        // Invalidation node 3
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), firstRootNetworkUuid).getGlobalBuildStatus());

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLoadAttributes)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), firstRootNetworkUuid,
            RootNetworkNodeInfo.builder()
                .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT))
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        //node 4 should not have any children
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node4.getId())).count());

        // duplicate the node1 after node4
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(study1Uuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubDuplicateUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));

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
        wireMockStubs.verifyDuplicateModificationGroup(stubDuplicateUuid, 3);

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
    void testDuplicateNodeBetweenStudies() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        UUID study2Uuid = createStudyWithStubs(userId, CASE_UUID);
        RootNode study2RootNode = networkModificationTreeService.getStudyTree(study2Uuid, null);
        UUID study2ModificationNodeUuid = study2RootNode.getChildren().get(0).getId();
        NetworkModificationNode study2Node2 = createNetworkModificationNode(study2Uuid, study2ModificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on study 1 node "node1"
        UUID stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        stubPostId = wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(stubPostId, getModificationContextJsonString(mapper, modificationBody));

        //study 2 node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId())).count());

        // duplicate the node1 from study 1 after node2 from study 2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study2Uuid, node1, study2Node2.getId(), InsertMode.AFTER, true, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(1, allNodes.stream()
                .filter(nodeEntity -> nodeEntity.getParentNode() != null
                        && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                        && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId()))
                .count());
    }

    private void cutAndPasteNode(UUID studyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, int childCount, String userId) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        boolean wasBuilt = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeToCopy.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid)).get().getNodeBuildStatus().toDto().isBuilt();
        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        output.receive(TIMEOUT, studyUpdateDestination);
        mockMvc.perform(post(STUDIES_URL +
                "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                studyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode)
                .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        wireMockStubs.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        boolean nodeHasModifications = networkModificationTreeService.hasModifications(nodeToCopy.getId(), false);

        wireMockStubs.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        /*
         * moving node
         */
        //nodeMoved
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_MOVED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(nodeToCopy.getId(), message.getHeaders().get(NotificationService.HEADER_MOVED_NODE));
        assertEquals(insertMode.name(), message.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        /*
         * invalidating moving node
         */
        //nodeUpdated
        if (wasBuilt) {
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        }
        //loadflow_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //securityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //sensitivityAnalysis_status
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
        //pccMin_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        if (!nodeHasModifications) {
            return;
        }

        /*
         * invalidating old children
         */
        IntStream.rangeClosed(1, childCount).forEach(i -> {
            //nodeUpdated
            if (wasBuilt) {
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            }
            //loadflow_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //securityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //sensitivityAnalysis_status
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
            //pccMin_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        });

        if (wasBuilt) {
            wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub, 1 + childCount);
        }
    }

    private UUID duplicateNode(UUID sourceStudyUuid, UUID targetStudyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, boolean checkMessagesForStatusModels, String userId) throws Exception {
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(targetStudyUuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubGetCountUuid = wireMockStubs.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        UUID stubDuplicateUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));
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

        if (checkMessagesForStatusModels) {
            checkUpdateModelsStatusMessagesReceived(targetStudyUuid, nodesAfterDuplication.get(0));
        }
        checkElementUpdatedMessageSent(targetStudyUuid, userId);

        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, nodeToCopy.getModificationGroupUuid().toString());
        wireMockStubs.verifyDuplicateModificationGroup(stubDuplicateUuid, 1);

        return nodesAfterDuplication.get(0);
    }

    @Test
    void testGetDefaultProviders() throws Exception {
        UUID stubDefaultProviderId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/default-provider"))
            .willReturn(WireMock.ok().withBody(DEFAULT_PROVIDER))).getId();
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

        wireMockStubs.verifyDefaultProvider(stubDefaultProviderId, 3);
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
    void reindexRootNetworkTest() throws Exception {
        // Network reindex stubs - using scenarios for stateful behavior
        // NOT_EXISTING_NETWORK always returns 404
        UUID stubReindexAllNotFoundId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/reindex-all"))
            .willReturn(WireMock.notFound())).getId();
        // First reindex call returns 200 and transitions to "indexed" state
        UUID stubReindexAllId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("indexed")
            .willReturn(WireMock.ok())).getId();
        // Subsequent reindex calls return 500 (server error)
        UUID stubReindexAllErrorId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs("indexed")
            .willReturn(WireMock.serverError())).getId();

        // Indexed equipments stubs - using scenarios for stateful behavior
        // NOT_EXISTING_NETWORK always returns 404
        UUID stubIndexedEquipmentsNotFoundId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathEqualTo("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/indexed-equipments"))
            .willReturn(WireMock.notFound())).getId();
        // Before reindex: return 204 (no content = not indexed)
        UUID stubIndexedEquipmentsNoContentId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathMatching("/v1/networks/.*/indexed-equipments"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.noContent())).getId();
        // After reindex: return 200 (ok = indexed)
        UUID stubIndexedEquipmentsId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathMatching("/v1/networks/.*/indexed-equipments"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs("indexed")
            .willReturn(WireMock.ok())).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", UUID.randomUUID(), UUID.randomUUID()))
            .andExpectAll(status().isNotFound());

        UUID notExistingNetworkStudyUuid = createStudyWithStubs("userId", NOT_EXISTING_NETWORK_CASE_UUID);
        UUID notExistingNetworkRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(notExistingNetworkStudyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpect(status().isNotFound());
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        Message<byte[]> indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        wireMockStubs.verifyReindexAll(stubReindexAllNotFoundId, NOT_EXISTING_NETWORK_UUID.toString());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpectAll(status().isNotFound());

        wireMockStubs.verifyIndexedEquipments(stubIndexedEquipmentsNotFoundId, NOT_EXISTING_NETWORK_UUID.toString());

        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
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

        wireMockStubs.verifyReindexAll(stubReindexAllId, NETWORK_UUID_STRING);
        wireMockStubs.verifyIndexedEquipments(stubIndexedEquipmentsId, NETWORK_UUID_STRING, 2);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", study1Uuid, study1RootNetworkUuid))
            .andExpect(status().is5xxServerError());
        indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        wireMockStubs.verifyReindexAll(stubReindexAllErrorId, NETWORK_UUID_STRING);
    }

    @Test
    void providerTest() throws Exception {
        UUID stubParametersProviderId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/parameters/.*/provider"))
            .willReturn(WireMock.ok())).getId();
        UUID studyUuid = createStudyWithStubs(USER_ID_HEADER, CASE_UUID);
        assertNotNull(studyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/loadflow/provider", studyUuid)
                        .content("SuperLF")
                        .contentType(MediaType.TEXT_PLAIN)
                        .header(USER_ID_HEADER, USER_ID_HEADER))
                .andExpect(status().isOk());
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, studyUpdateDestination);
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

        wireMockStubs.verifyParametersProvider(stubParametersProviderId, 2);
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
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
