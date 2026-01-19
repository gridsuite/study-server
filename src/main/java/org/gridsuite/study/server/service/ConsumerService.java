/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.dto.networkexport.NetworkExportReceiver;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.dto.workflow.WorkflowType;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.dto.networkexport.NodeExportInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.ComputationType.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class ConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    private static final String RESULT_UUID = "resultUuid";
    private static final String NETWORK_UUID = "networkUuid";
    private static final String NETWORK_ID = "networkId";
    private static final String HEADER_CASE_FORMAT = "caseFormat";
    private static final String HEADER_CASE_NAME = "caseName";
    private static final String HEADER_WITH_RATIO_TAP_CHANGERS = "withRatioTapChangers";
    private static final String HEADER_ERROR_MESSAGE = "errorMessage";
    private static final String HEADER_EXPORT_UUID = "exportUuid";
    private final ObjectMapper objectMapper;

    private final NotificationService notificationService;
    private final StudyService studyService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final CaseService caseService;
    private final LoadFlowService loadFlowService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final StudyConfigService studyConfigService;
    private final ShortCircuitService shortCircuitService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final VoltageInitService voltageInitService;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    private final StateEstimationService stateEstimationService;
    private final PccMinService pccMinService;
    private final DirectoryService directoryService;

    public ConsumerService(ObjectMapper objectMapper,
                           NotificationService notificationService,
                           StudyService studyService,
                           SecurityAnalysisService securityAnalysisService,
                           CaseService caseService,
                           LoadFlowService loadFlowService,
                           ShortCircuitService shortCircuitService,
                           NetworkModificationTreeService networkModificationTreeService,
                           SensitivityAnalysisService sensitivityAnalysisService,
                           StudyConfigService studyConfigService,
                           RootNetworkNodeInfoService rootNetworkNodeInfoService,
                           VoltageInitService voltageInitService,
                           DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
                           StateEstimationService stateEstimationService, PccMinService pccMinService,
                           DirectoryService directoryService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.studyService = studyService;
        this.securityAnalysisService = securityAnalysisService;
        this.caseService = caseService;
        this.loadFlowService = loadFlowService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.studyConfigService = studyConfigService;
        this.shortCircuitService = shortCircuitService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.voltageInitService = voltageInitService;
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
        this.stateEstimationService = stateEstimationService;
        this.pccMinService = pccMinService;
        this.directoryService = directoryService;
    }

    @Bean
    public Consumer<Message<NetworkModificationResult>> consumeBuildResult() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    NetworkModificationResult networkModificationResult = message.getPayload();
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                        NodeReceiver.class);

                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    studyService.handleBuildSuccess(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), networkModificationResult);
                    handleBuildResultWorkflow(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), message);
                } catch (Exception e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    private void handleBuildResultWorkflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Message<NetworkModificationResult> message) throws JsonProcessingException {
        String workflowTypeStr = message.getHeaders().get(HEADER_WORKFLOW_TYPE, String.class);
        String workflowInfosStr = message.getHeaders().get(HEADER_WORKFLOW_INFOS, String.class);
        if (workflowTypeStr != null && workflowInfosStr != null) {
            WorkflowType workflowType = WorkflowType.valueOf(workflowTypeStr);
            if (WorkflowType.RERUN_LOAD_FLOW.equals(workflowType)) {
                RerunLoadFlowInfos workflowInfos = objectMapper.readValue(URLDecoder.decode(workflowInfosStr, StandardCharsets.UTF_8), RerunLoadFlowInfos.class);
                studyService.sendLoadflowRequestWorflow(studyUuid, nodeUuid, rootNetworkUuid, workflowInfos.getLoadflowResultUuid(), workflowInfos.isWithRatioTapChangers(), workflowInfos.getUserId());
            }
        }
    }

    @Bean
    public Consumer<Message<String>> consumeBuildStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                        NodeReceiver.class);

                    LOGGER.info("Build stopped for node '{}'", receiverObj.getNodeUuid());

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

                    // send notification
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), NotificationService.UPDATE_TYPE_BUILD_CANCELLED);
                    handleBuildCanceledOrFailedWorkflow(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), message);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeBuildFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                        NodeReceiver.class);

                    LOGGER.info("Build failed for node '{}'", receiverObj.getNodeUuid());

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

                    // send notification
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitNodeBuildFailed(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), message.getHeaders().get(StudyConstants.HEADER_ERROR_MESSAGE, String.class));
                    handleBuildCanceledOrFailedWorkflow(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), message);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    private void handleBuildCanceledOrFailedWorkflow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Message<String> message) throws JsonProcessingException {
        String workflowTypeStr = message.getHeaders().get(HEADER_WORKFLOW_TYPE, String.class);
        String workflowInfosStr = message.getHeaders().get(HEADER_WORKFLOW_INFOS, String.class);
        if (workflowTypeStr != null && workflowInfosStr != null) {
            WorkflowType workflowType = WorkflowType.valueOf(workflowTypeStr);
            if (WorkflowType.RERUN_LOAD_FLOW.equals(workflowType)) {
                RerunLoadFlowInfos workflowInfos = objectMapper.readValue(URLDecoder.decode(workflowInfosStr, StandardCharsets.UTF_8), RerunLoadFlowInfos.class);
                studyService.deleteLoadflowResult(studyUuid, nodeUuid, rootNetworkUuid, workflowInfos.getLoadflowResultUuid());
            }
        }
    }

    //TODO: should be linked to a specific rootNetwork
    @Bean
    public Consumer<Message<String>> consumeCaseImportSucceeded() {
        return message -> {
            String receiverString = message.getHeaders().get(HEADER_RECEIVER, String.class);
            UUID networkUuid = UUID.fromString(message.getHeaders().get(NETWORK_UUID, String.class));
            String networkId = message.getHeaders().get(NETWORK_ID, String.class);
            String caseFormat = message.getHeaders().get(HEADER_CASE_FORMAT, String.class);
            String caseName = message.getHeaders().get(HEADER_CASE_NAME, String.class);
            Map<String, Object> rawParameters = message.getHeaders().get(HEADER_IMPORT_PARAMETERS, Map.class);
            // String longer than 1024 bytes are converted to com.rabbitmq.client.LongString (https://docs.spring.io/spring-amqp/docs/3.0.0/reference/html/#message-properties-converters)
            Map<String, String> importParameters = new HashMap<>();
            if (rawParameters != null) {
                rawParameters.forEach((key, value) -> importParameters.put(key, value.toString()));
            }

            if (receiverString != null) {
                CaseImportReceiver receiver;
                try {
                    receiver = objectMapper.readValue(URLDecoder.decode(receiverString, StandardCharsets.UTF_8), CaseImportReceiver.class);
                } catch (JsonProcessingException e) {
                    LOGGER.error("Error while parsing CaseImportReceiver data", e);
                    return;
                }

                handleConsumeCaseImportSucceeded(receiver, networkUuid, networkId, caseName, caseFormat, importParameters);
            }
        };
    }

    private void handleConsumeCaseImportSucceeded(CaseImportReceiver receiver, UUID networkUuid, String networkId, String caseName, String caseFormat, Map<String, String> importParameters) {
        UUID caseUuid = receiver.getCaseUuid();
        UUID studyUuid = receiver.getStudyUuid();
        String userId = receiver.getUserId();
        Long startTime = receiver.getStartTime();
        UUID importReportUuid = receiver.getReportUuid();
        UUID rootNetworkUuid = receiver.getRootNetworkUuid();
        CaseImportAction caseImportAction = receiver.getCaseImportAction();

        CaseInfos caseInfos = new CaseInfos(caseUuid, receiver.getOriginalCaseUuid(), caseName, caseFormat);
        NetworkInfos networkInfos = new NetworkInfos(networkUuid, networkId);
        try {
            switch (caseImportAction) {
                case STUDY_CREATION ->
                    insertStudy(studyUuid, userId, networkInfos, caseInfos, importParameters, importReportUuid);
                case ROOT_NETWORK_CREATION -> studyService.createRootNetwork(studyUuid, RootNetworkInfos.builder()
                    .id(rootNetworkUuid)
                    .caseInfos(caseInfos)
                    .reportUuid(importReportUuid)
                    .networkInfos(networkInfos)
                    .importParameters(importParameters)
                    .build());
                case NETWORK_RECREATION -> studyService.updateNetwork(studyUuid, rootNetworkUuid, networkInfos, userId);
                case ROOT_NETWORK_MODIFICATION -> studyService.modifyRootNetwork(studyUuid, RootNetworkInfos.builder()
                    .id(rootNetworkUuid)
                    .networkInfos(networkInfos)
                    .caseInfos(caseInfos)
                    .importParameters(importParameters)
                    .reportUuid(importReportUuid)
                    .build());
            }
            caseService.disableCaseExpiration(caseUuid);
        } catch (Exception e) {
            LOGGER.error("Error while importing case", e);
        } finally {
            // if studyEntity is already existing, we don't delete anything in the end of the process
            if (caseImportAction == CaseImportAction.STUDY_CREATION) {
                studyService.deleteStudyIfNotCreationInProgress(studyUuid);
            }
            if (caseImportAction == CaseImportAction.ROOT_NETWORK_MODIFICATION) {
                UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
                networkModificationTreeService.unblockNodeTree(rootNetworkUuid, rootNodeUuid);
            }
            LOGGER.trace("{} for study uuid '{}' : {} seconds", caseImportAction.getLabel(), studyUuid,
                TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));
        }
    }

    private void insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos,
                             Map<String, String> importParameters, UUID importReportUuid) {
        UserProfileInfos userProfileInfos = studyService.getUserProfile(userId);

        DynamicSimulationParametersInfos dynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
        UUID loadFlowParametersUuid = createDefaultLoadFlowParameters(userId, userProfileInfos);
        UUID shortCircuitParametersUuid = createDefaultShortCircuitAnalysisParameters(userId, userProfileInfos);
        UUID securityAnalysisParametersUuid = createDefaultSecurityAnalysisParameters(userId, userProfileInfos);
        UUID sensitivityAnalysisParametersUuid = createDefaultSensitivityAnalysisParameters(userId, userProfileInfos);
        UUID networkVisualizationParametersUuid = createDefaultNetworkVisualizationParameters(userId, userProfileInfos);
        UUID voltageInitParametersUuid = createDefaultVoltageInitParameters(userId, userProfileInfos);
        UUID dynamicSecurityAnalysisParametersUuid = createDefaultDynamicSecurityAnalysisParameters(userId, userProfileInfos);
        UUID stateEstimationParametersUuid = createDefaultStateEstimationParameters();
        UUID pccMinParametersUuid = createDefaultPccMinParameters();
        UUID spreadsheetConfigCollectionUuid = createDefaultSpreadsheetConfigCollection(userId, userProfileInfos);
        UUID diagramGridLayoutUuid = studyService.createGridLayoutFromNadDiagram(userId, userProfileInfos);

        studyService.insertStudy(studyUuid, userId, networkInfos, caseInfos, loadFlowParametersUuid,
            shortCircuitParametersUuid, DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper),
            voltageInitParametersUuid, securityAnalysisParametersUuid, sensitivityAnalysisParametersUuid,
            networkVisualizationParametersUuid, dynamicSecurityAnalysisParametersUuid, stateEstimationParametersUuid, pccMinParametersUuid, spreadsheetConfigCollectionUuid, diagramGridLayoutUuid,
            importParameters, importReportUuid);
    }

    private UUID createDefaultLoadFlowParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getLoadFlowParameterId() != null) {
            // try to access/duplicate the user profile LF parameters
            try {
                return loadFlowService.duplicateLoadFlowParameters(userProfileInfos.getLoadFlowParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate loadflow parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getLoadFlowParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad LF parameters in profile => use default values
        try {
            return loadFlowService.createDefaultLoadFlowParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for LoadFlow analysis", e);
            return null;
        }
    }

    private UUID createDefaultShortCircuitAnalysisParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getShortcircuitParameterId() != null) {
            // try to access/duplicate the user profile shortcircuit parameters
            try {
                return shortCircuitService.duplicateParameters(userProfileInfos.getShortcircuitParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate shortcircuit parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getShortcircuitParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad shortcircuit parameters in profile => use default values
        try {
            return shortCircuitService.createParameters(null);
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for ShortCircuit analysis", e);
            return null;
        }
    }

    private UUID createDefaultSensitivityAnalysisParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getSensitivityAnalysisParameterId() != null) {
            // try to access/duplicate the user profile sensitivity analysis parameters
            try {
                return sensitivityAnalysisService.duplicateSensitivityAnalysisParameters(userProfileInfos.getSensitivityAnalysisParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate sensitivity analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getSensitivityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad sensitivity analysis parameters in profile => use default values
        try {
            return sensitivityAnalysisService.createDefaultSensitivityAnalysisParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for Sensitivity analysis", e);
            return null;
        }
    }

    private UUID createDefaultSecurityAnalysisParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getSecurityAnalysisParameterId() != null) {
            // try to access/duplicate the user profile security analysis parameters
            try {
                return securityAnalysisService.duplicateSecurityAnalysisParameters(userProfileInfos.getSecurityAnalysisParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate security analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getSecurityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad security analysis parameters in profile => use default values
        try {
            return securityAnalysisService.createDefaultSecurityAnalysisParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for Security analysis", e);
            return null;
        }
    }

    private UUID createDefaultVoltageInitParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getVoltageInitParameterId() != null) {
            // try to access/duplicate the user profile voltage init parameters
            try {
                return voltageInitService.duplicateVoltageInitParameters(userProfileInfos.getVoltageInitParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate voltage init parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getVoltageInitParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad voltage init parameters in profile => use default values
        try {
            return voltageInitService.createVoltageInitParameters(null);
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for voltage init", e);
            return null;
        }
    }

    private UUID createDefaultNetworkVisualizationParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getNetworkVisualizationParameterId() != null) {
            // try to access/duplicate the user profile network visualization parameters
            try {
                return studyConfigService.duplicateNetworkVisualizationParameters(userProfileInfos.getNetworkVisualizationParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate network visualization parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                    userProfileInfos.getNetworkVisualizationParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad network visualization parameters in profile => use default values
        try {
            return studyConfigService.createDefaultNetworkVisualizationParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating network visualization default parameters", e);
            return null;
        }
    }

    private UUID createDefaultDynamicSecurityAnalysisParameters(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getDynamicSecurityAnalysisParameterId() != null) {
            // try to access/duplicate the user profile Dynamic Security Analysis parameters
            try {
                return dynamicSecurityAnalysisService.duplicateParameters(userProfileInfos.getDynamicSecurityAnalysisParameterId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate dynamic security analysis parameters with id '%s' from user/profile '%s/%s'. Using default parameters",
                        userProfileInfos.getDynamicSecurityAnalysisParameterId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad dynamic security analysis parameters in profile => use default values
        try {
            return dynamicSecurityAnalysisService.createDefaultParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for dynamic security analysis", e);
            return null;
        }
    }

    private UUID createDefaultStateEstimationParameters() {
        try {
            return stateEstimationService.createDefaultStateEstimationParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating state estimation default parameters", e);
            return null;
        }
    }

    private UUID createDefaultPccMinParameters() {
        try {
            return pccMinService.createDefaultPccMinParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating pcc min default parameters", e);
            return null;
        }
    }

    private UUID createDefaultSpreadsheetConfigCollection(String userId, UserProfileInfos userProfileInfos) {
        if (userProfileInfos != null && userProfileInfos.getSpreadsheetConfigCollectionId() != null) {
            // try to access/duplicate the user profile spreadsheet config collection
            try {
                return studyConfigService.duplicateSpreadsheetConfigCollection(userProfileInfos.getSpreadsheetConfigCollectionId());
            } catch (Exception e) {
                // TODO try to report a log in Root subreporter ?
                LOGGER.error(String.format("Could not duplicate spreadsheet config collection with id '%s' from user/profile '%s/%s'. Using default spreadsheet config collection",
                    userProfileInfos.getSpreadsheetConfigCollectionId(), userId, userProfileInfos.getName()), e);
            }
        }
        // no profile, or no/bad spreadsheet config collection in profile => use default values
        try {
            return studyConfigService.createDefaultSpreadsheetConfigCollection();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default spreadsheet config collection", e);
            return null;
        }
    }

    @Bean
    public Consumer<Message<String>> consumeCaseImportFailed() {
        return message -> {
            String receiverString = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String errorMessage = message.getHeaders().get(StudyConstants.HEADER_ERROR_MESSAGE, String.class);

            if (receiverString != null) {
                CaseImportReceiver receiver;
                try {
                    receiver = objectMapper.readValue(URLDecoder.decode(receiverString, StandardCharsets.UTF_8),
                        CaseImportReceiver.class);
                    UUID studyUuid = receiver.getStudyUuid();
                    String userId = receiver.getUserId();
                    UUID rootNetworkUuid = receiver.getRootNetworkUuid();

                    if (receiver.getCaseImportAction() == CaseImportAction.STUDY_CREATION) {
                        studyService.deleteStudyIfNotCreationInProgress(studyUuid);
                        notificationService.emitStudyCreationError(studyUuid, userId, errorMessage);
                    } else {
                        if (receiver.getCaseImportAction() == CaseImportAction.ROOT_NETWORK_CREATION) {
                            studyService.deleteRootNetworkRequest(rootNetworkUuid);
                        }
                        notificationService.emitRootNetworksUpdateFailed(studyUuid, errorMessage);
                    }
                } catch (Exception e) {
                    LOGGER.error(e.toString(), e);
                }
            }
        };
    }

    /**
     * processes the error message from the computation microservice and uses its data to notify the front
     */
    public void consumeCalculationFailed(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        String errorMessage = msg.getHeaders().get(StudyConstants.HEADER_ERROR_MESSAGE, String.class);
        String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
        UUID resultUuid = null;

        var computationsToReset = List.of(DYNAMIC_SIMULATION, DYNAMIC_SECURITY_ANALYSIS);
        if (!computationsToReset.contains(computationType)) {
            String resultId = msg.getHeaders().get(RESULT_UUID, String.class);
            if (resultId != null) {
                resultUuid = UUID.fromString(resultId);
            }
        }
        if (!Strings.isBlank(receiver)) {
            NodeReceiver receiverObj = null;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                LOGGER.info("{} failed for node '{}' with error message: {}", computationType.getLabel(), receiverObj.getNodeUuid(), errorMessage);

                // delete computation results from the databases
                // ==> will probably be removed soon because it prevents the front from recovering the resultId ; or 'null' parameter will be replaced by null like in VOLTAGE_INITIALIZATION
                rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), resultUuid, computationType);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            } finally {
                if (receiverObj != null) {
                    handleUnblockNode(receiverObj, computationType);

                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyError(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), computationType.getUpdateFailedType(), errorMessage, userId);
                }
            }
        }
    }

    public void consumeCalculationStopped(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (!Strings.isBlank(receiver)) {
            NodeReceiver receiverObj = null;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                // delete computation results from the database
                rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), null, computationType);
                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notification for stopped computation
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), computationType.getUpdateStatusType());

                LOGGER.info("{} stopped for node '{}'", computationType.getLabel(), receiverObj.getNodeUuid());
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            } finally {
                if (receiverObj != null) {
                    handleUnblockNode(receiverObj, computationType);
                }
            }
        }
    }

    public void consumeCalculationCancelFailed(Message<String> msg, ComputationType computationType, String updateType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (!Strings.isBlank(receiver)) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);
                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                String errorMessage = msg.getHeaders().get(HEADER_MESSAGE, String.class);
                String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
                // send notification for cancel computation fail
                notificationService.emitStudyError(
                    studyUuid,
                    receiverObj.getNodeUuid(),
                    receiverObj.getRootNetworkUuid(),
                    updateType,
                    errorMessage,
                    userId
                );
                LOGGER.info("{} cancellation could not be stopped for node '{}'", computationType.getLabel(), receiverObj.getNodeUuid());
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            }
        }
    }

    private void handleUnblockNode(NodeReceiver receiverObj, ComputationType computationType) {
        if (computationType == LOAD_FLOW && networkModificationTreeService.isSecurityNode(receiverObj.getNodeUuid())) {
            networkModificationTreeService.unblockNodeTree(receiverObj.getRootNetworkUuid(), receiverObj.getNodeUuid());
        } else {
            networkModificationTreeService.unblockNode(receiverObj.getRootNetworkUuid(), receiverObj.getNodeUuid());
        }
    }

    public void consumeCalculationDebug(Message<String> msg, ComputationType computationType) {
        Optional.ofNullable(msg.getHeaders().get(RESULT_UUID, String.class))
            .map(UUID::fromString)
            .ifPresent(resultUuid -> getNodeReceiver(msg).ifPresent(receiverObj -> {
                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                String errorMessage = (String) msg.getHeaders().get(HEADER_ERROR_MESSAGE);
                String userId = (String) msg.getHeaders().get(HEADER_USER_ID);
                notificationService.emitComputationDebugFileStatus(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), computationType, userId, resultUuid, errorMessage);
            }));
    }

    public void consumeCalculationResult(Message<String> msg, ComputationType computationType) {
        Optional.ofNullable(msg.getHeaders().get(RESULT_UUID, String.class))
            .map(UUID::fromString)
            .ifPresent(resultUuid -> getNodeReceiver(msg).ifPresent(receiverObj -> {
                LOGGER.info("{} result '{}' available for node '{}'",
                    computationType.getLabel(),
                    resultUuid,
                    receiverObj.getNodeUuid());

                // update DB
                if (computationType == LOAD_FLOW) {
                    Boolean withRatioTapChangers = msg.getHeaders().get(HEADER_WITH_RATIO_TAP_CHANGERS, Boolean.class);
                    rootNetworkNodeInfoService.updateLoadflowResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), resultUuid, withRatioTapChangers);
                } else {
                    rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), resultUuid, computationType);
                }

                // unblock node
                handleUnblockNode(receiverObj, computationType);

                // send notifications
                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), computationType.getUpdateStatusType());
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), computationType.getUpdateResultType());
            }));
    }

    Optional<NodeReceiver> getNodeReceiver(Message<String> msg) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (Strings.isBlank(receiver)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class));
        } catch (JsonProcessingException e) {
            LOGGER.error(e.toString());
            return Optional.empty();
        }
    }

    Optional<UUID> getStudyUuid(Message<String> msg) {
        Optional<NodeReceiver> receiverObj = getNodeReceiver(msg);
        return receiverObj.map(r -> networkModificationTreeService.getStudyUuidForNodeId(r.getNodeUuid()));
    }

    @Bean
    public Consumer<Message<String>> consumeDsDebug() {
        return message -> consumeCalculationDebug(message, DYNAMIC_SIMULATION);
    }

    @Bean
    public Consumer<Message<String>> consumeDsResult() {
        return message -> consumeCalculationResult(message, DYNAMIC_SIMULATION);
    }

    @Bean
    public Consumer<Message<String>> consumeDsStopped() {
        return message -> consumeCalculationStopped(message, DYNAMIC_SIMULATION);
    }

    @Bean
    public Consumer<Message<String>> consumeDsFailed() {
        return message -> consumeCalculationFailed(message, DYNAMIC_SIMULATION);
    }

    @Bean
    public Consumer<Message<String>> consumeDsaDebug() {
        return message -> consumeCalculationDebug(message, DYNAMIC_SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeDsaResult() {
        return message -> consumeCalculationResult(message, DYNAMIC_SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeDsaStopped() {
        return message -> consumeCalculationStopped(message, DYNAMIC_SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeDsaFailed() {
        return message -> consumeCalculationFailed(message, DYNAMIC_SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSaResult() {
        return message -> consumeCalculationResult(message, SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSaStopped() {
        return message -> consumeCalculationStopped(message, SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSaFailed() {
        return message -> consumeCalculationFailed(message, SECURITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisResult() {
        return message -> consumeCalculationResult(message, SENSITIVITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisStopped() {
        return message -> consumeCalculationStopped(message, SENSITIVITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisFailed() {
        return message -> consumeCalculationFailed(message, SENSITIVITY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowResult() {
        return message -> consumeCalculationResult(message, LOAD_FLOW);
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowStopped() {
        return message -> consumeCalculationStopped(message, LOAD_FLOW);
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowFailed() {
        return message -> consumeCalculationFailed(message, LOAD_FLOW);
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisDebug() {
        return message -> {
            String busId = message.getHeaders().get(HEADER_BUS_ID, String.class);
            ComputationType computationType = !StringUtils.isEmpty(busId) ? SHORT_CIRCUIT_ONE_BUS : SHORT_CIRCUIT;
            consumeCalculationDebug(message, computationType);
        };
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisResult() {
        return message -> {
            String busId = message.getHeaders().get(HEADER_BUS_ID, String.class);
            if (!StringUtils.isEmpty(busId)) {
                consumeCalculationResult(message, SHORT_CIRCUIT_ONE_BUS);
            } else {
                consumeCalculationResult(message, SHORT_CIRCUIT);
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisStopped() {
        return message -> consumeCalculationStopped(message, SHORT_CIRCUIT);
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisFailed() {
        return message -> {
            String busId = message.getHeaders().get(HEADER_BUS_ID, String.class);
            if (!StringUtils.isEmpty(busId)) {
                consumeCalculationFailed(message, SHORT_CIRCUIT_ONE_BUS);
            } else {
                consumeCalculationFailed(message, SHORT_CIRCUIT);
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitDebug() {
        return message -> consumeCalculationDebug(message, VOLTAGE_INITIALIZATION);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitStopped() {
        return message -> consumeCalculationStopped(message, VOLTAGE_INITIALIZATION);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitFailed() {
        return message -> consumeCalculationFailed(message, VOLTAGE_INITIALIZATION);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitCancelFailed() {
        return message -> consumeCalculationCancelFailed(message, VOLTAGE_INITIALIZATION, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_CANCEL_FAILED);
    }

    @Bean
    public Consumer<Message<String>> consumeStateEstimationResult() {
        return message -> consumeCalculationResult(message, STATE_ESTIMATION);
    }

    @Bean
    public Consumer<Message<String>> consumeStateEstimationStopped() {
        return message -> consumeCalculationStopped(message, STATE_ESTIMATION);
    }

    @Bean
    public Consumer<Message<String>> consumeStateEstimationFailed() {
        return message -> consumeCalculationFailed(message, STATE_ESTIMATION);
    }

    @Bean
    public Consumer<Message<String>> consumePccMinResult() {
        return message -> consumeCalculationResult(message, PCC_MIN);
    }

    @Bean
    public Consumer<Message<String>> consumePccMinStopped() {
        return message -> consumeCalculationStopped(message, PCC_MIN);
    }

    @Bean
    public Consumer<Message<String>> consumePccMinFailed() {
        return message -> consumeCalculationFailed(message, PCC_MIN);
    }

    public void consumeNetworkExportFinished(Message<String> msg) {
        String receiverString = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        String exportFolder = msg.getHeaders().get(HEADER_EXPORT_FOLDER, String.class);
        String exportInfosStr = msg.getHeaders().get(HEADER_EXPORT_INFOS, String.class);

        if (receiverString != null) {
            NetworkExportReceiver receiver;
            try {
                receiver = objectMapper.readValue(URLDecoder.decode(receiverString, StandardCharsets.UTF_8), NetworkExportReceiver.class);
                UUID studyUuid = receiver.getStudyUuid();
                String userId = receiver.getUserId();
                UUID exportUuid = msg.getHeaders().containsKey(HEADER_EXPORT_UUID) ? UUID.fromString((String) Objects.requireNonNull(msg.getHeaders().get(HEADER_EXPORT_UUID))) : null;

                // With export uuid get
                NodeExportInfos nodeExport = null;
                if (exportInfosStr != null) {
                    nodeExport = objectMapper.readValue(URLDecoder.decode(exportInfosStr, StandardCharsets.UTF_8), NodeExportInfos.class);
                }

                boolean exportToExplorer = false;
                String errorMessage = (String) msg.getHeaders().get(HEADER_ERROR);

                if (nodeExport != null && nodeExport.exportToExplorer()) {
                    //Call case server and create case in directory
                    exportToExplorer = true;
                    if (StringUtils.isEmpty(errorMessage)) {
                        errorMessage = createCase(exportUuid, exportFolder, nodeExport, userId);
                    }
                }

                networkModificationTreeService.updateExportNetworkStatus(exportUuid, errorMessage == null ? ExportNetworkStatus.SUCCESS : ExportNetworkStatus.FAILED);
                notificationService.emitNetworkExportFinished(studyUuid, exportUuid, exportToExplorer, userId, errorMessage);
            } catch (Exception e) {
                LOGGER.error(e.toString(), e);
            }
        }
    }

    private String createCase(UUID exportUuid, String exportFolder, NodeExportInfos nodeExport, String userId) {
        String errorMessage = null;

        try {
            UUID caseUuid = caseService.createCase(exportUuid, exportFolder, nodeExport.fileName());
            directoryService.createElement(nodeExport.directoryUuid(), nodeExport.description(), caseUuid, nodeExport.fileName(), DirectoryService.CASE, userId);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        return errorMessage;
    }

    @Bean
    public Consumer<Message<String>> consumeNetworkExportFinished() {
        return this::consumeNetworkExportFinished;
    }
}
