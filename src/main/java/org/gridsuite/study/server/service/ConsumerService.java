/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.CaseImportReceiver;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitParametersInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class ConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    static final String RESULT_UUID = "resultUuid";
    static final String NETWORK_UUID = "networkUuid";
    static final String NETWORK_ID = "networkId";
    static final String HEADER_CASE_FORMAT = "caseFormat";
    static final String HEADER_CASE_NAME = "caseName";
    static final String HEADER_ERROR_MESSAGE = "errorMessage";

    private final ObjectMapper objectMapper;

    NotificationService notificationService;
    StudyService studyService;
    CaseService caseService;
    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper,
                           NotificationService notificationService,
                           StudyService studyService,
                           CaseService caseService,
                           NetworkModificationTreeService networkModificationTreeService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.studyService = studyService;
        this.caseService = caseService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    @Bean
    public Consumer<Message<String>> consumeDsResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (!Strings.isBlank(receiver)) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Dynamic Simulation result '{}' available for node '{}'", resultUuid,
                            receiverObj.getNodeUuid());

                    // insert resultUuid into DB
                    updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), resultUuid);
                    // send notifications
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeDsStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (!Strings.isBlank(receiver)) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Dynamic Simulation stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete dynamic simulation resultUuid into DB
                    updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeDsFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (!Strings.isBlank(receiver)) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Dynamic Simulation failed for node '{}'", receiverObj.getNodeUuid());

                    // delete dynamic simulation resultUuid into DB
                    updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSaResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Security analysis result '{}' available for node '{}'", resultUuid,
                            receiverObj.getNodeUuid());

                    // update DB
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                                // send notifications
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSaStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Security analysis stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete security analysis result in database
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSaFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Security analysis failed for node '{}'", receiverObj.getNodeUuid());

                    // delete security analysis result in database
                    updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED);

                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
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

                    LOGGER.info("Build completed for node '{}'", receiverObj.getNodeUuid());

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(),
                            NodeBuildStatus.from(networkModificationResult.getLastGroupApplicationStatus(), networkModificationResult.getApplicationStatus()));

                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_COMPLETED, networkModificationResult.getImpactedSubstationsIds());
                } catch (Exception e) {
                    LOGGER.error(e.toString());
                }
            }
        };
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

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

                    // send notification
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_CANCELLED);
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

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

                    // send notification
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_FAILED);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCaseImportSucceeded() {
        return message -> {
            String receiverString = message.getHeaders().get(HEADER_RECEIVER, String.class);
            UUID networkUuid = UUID.fromString(message.getHeaders().get(NETWORK_UUID, String.class));
            String networkId = message.getHeaders().get(NETWORK_ID, String.class);
            String caseFormat = message.getHeaders().get(HEADER_CASE_FORMAT, String.class);
            String caseName = message.getHeaders().get(HEADER_CASE_NAME, String.class);
            Map<String, String> importParameters = message.getHeaders().get(HEADER_IMPORT_PARAMETERS, Map.class);

            NetworkInfos networkInfos = new NetworkInfos(networkUuid, networkId);

            if (receiverString != null) {
                CaseImportReceiver receiver;
                try {
                    receiver = objectMapper.readValue(URLDecoder.decode(receiverString, StandardCharsets.UTF_8),
                            CaseImportReceiver.class);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                    return;
                }

                UUID caseUuid = receiver.getCaseUuid();
                UUID studyUuid = receiver.getStudyUuid();
                String userId = receiver.getUserId();
                Long startTime = receiver.getStartTime();
                UUID importReportUuid = receiver.getReportUuid();

                try {
                    LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                    ShortCircuitParameters shortCircuitParameters = ShortCircuitService.getDefaultShortCircuitParameters();
                    DynamicSimulationParametersInfos dynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
                    VoltageInitParametersInfos voltageInitParametersInfos = VoltageInitService.getDefaultVoltageInitParameters();
                    studyService.insertStudy(studyUuid, userId, networkInfos, caseFormat, caseUuid, caseName, LoadFlowService.toEntity(loadFlowParameters, List.of()), ShortCircuitService.toEntity(shortCircuitParameters), DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper), VoltageInitService.toEntity(voltageInitParametersInfos), importParameters, importReportUuid);
                    caseService.disableCaseExpiration(caseUuid);
                } catch (Exception e) {
                    LOGGER.error(e.toString(), e);
                } finally {
                    studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
                    LOGGER.trace("Create study '{}' : {} seconds", studyUuid,
                            TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime));
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeCaseImportFailed() {
        return message -> {
            String receiverString = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String errorMessage = message.getHeaders().get(HEADER_ERROR_MESSAGE, String.class);

            if (receiverString != null) {
                CaseImportReceiver receiver;
                try {
                    receiver = objectMapper.readValue(URLDecoder.decode(receiverString, StandardCharsets.UTF_8),
                            CaseImportReceiver.class);
                    UUID studyUuid = receiver.getStudyUuid();
                    String userId = receiver.getUserId();

                    studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
                    notificationService.emitStudyCreationError(studyUuid, userId, errorMessage);
                } catch (Exception e) {
                    LOGGER.error(e.toString(), e);
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Sensitivity analysis result '{}' available for node '{}'", resultUuid, receiverObj.getNodeUuid());

                    // update DB
                    updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);

                    // send notifications
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Sensitivity analysis stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete sensitivity analysis result in database
                    updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeSensitivityAnalysisFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Sensitivity analysis failed for node '{}'", receiverObj.getNodeUuid());

                    // delete sensitivity analysis result in database
                    updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        networkModificationTreeService.updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid);
    }

    void updateDynamicSimulationResultUuid(UUID nodeUuid, UUID dynamicSimulationResultUuid) {
        networkModificationTreeService.updateDynamicSimulationResultUuid(nodeUuid, dynamicSimulationResultUuid);
    }

    void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(nodeUuid, sensitivityAnalysisResultUuid);
    }

    void updateShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        networkModificationTreeService.updateShortCircuitAnalysisResultUuid(nodeUuid, shortCircuitAnalysisResultUuid);
    }

    void updateOneBusShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        networkModificationTreeService.updateOneBusShortCircuitAnalysisResultUuid(nodeUuid, shortCircuitAnalysisResultUuid);
    }

    void updateLoadFlowResultUuid(UUID nodeUuid, UUID loadFlowResultUuid) {
        networkModificationTreeService.updateLoadFlowResultUuid(nodeUuid, loadFlowResultUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Loadflow result '{}' available for node '{}'", resultUuid, receiverObj.getNodeUuid());

                    // update DB
                    updateLoadFlowResultUuid(receiverObj.getNodeUuid(), resultUuid);

                    // send notifications
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Loadflow stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete loadflow result in database
                    updateLoadFlowResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeLoadFlowFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String errorMessage = message.getHeaders().get(HEADER_MESSAGE, String.class);
            String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("LoadFlow failed for node '{}'", receiverObj.getNodeUuid());

                    // delete LoadFlow result in database
                    updateLoadFlowResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyError(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_LOADFLOW_FAILED, errorMessage, userId);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String busId = message.getHeaders().get(HEADER_BUS_ID, String.class);
            ShortcircuitAnalysisType analysisType = busId == null ? ShortcircuitAnalysisType.ALL_BUSES : ShortcircuitAnalysisType.ONE_BUS;
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Short circuit analysis result '{}' available for node '{}'", resultUuid, receiverObj.getNodeUuid());

                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    // update DB
                    if (analysisType == ShortcircuitAnalysisType.ALL_BUSES) {
                        updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);

                        // send notifications
                        notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
                        notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);
                    } else {
                        updateOneBusShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);

                        // send notifications
                        notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
                        notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT);
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Short circuit analysis stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete Short circuit analysis result in database
                    updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String errorMessage = message.getHeaders().get(HEADER_MESSAGE, String.class);
            String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
            String busId = message.getHeaders().get(HEADER_BUS_ID, String.class);
            ShortcircuitAnalysisType analysisType = busId == null ? ShortcircuitAnalysisType.ALL_BUSES : ShortcircuitAnalysisType.ONE_BUS;
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Short circuit analysis failed for node '{}'", receiverObj.getNodeUuid());

                    if (analysisType == ShortcircuitAnalysisType.ALL_BUSES) {
                        // delete Short circuit analysis result in database
                        updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);

                        // send notification for failed computation
                        UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                        notificationService.emitStudyError(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED, errorMessage, userId);
                    } else {
                        // delete one bus Short circuit analysis result in database
                        updateOneBusShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);

                        // send notification for failed computation
                        UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                        notificationService.emitStudyError(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED, errorMessage, userId);
                    }
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    void updateVoltageInitResultUuid(UUID nodeUuid, UUID voltageInitResultUuid) {
        networkModificationTreeService.updateVoltageInitResultUuid(nodeUuid, voltageInitResultUuid);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitResult() {
        return message -> {
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Voltage init result '{}' available for node '{}'", resultUuid, receiverObj.getNodeUuid());

                    // update DB
                    updateVoltageInitResultUuid(receiverObj.getNodeUuid(), resultUuid);

                    // send notifications
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitStopped() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Voltage init stopped for node '{}'", receiverObj.getNodeUuid());

                    // delete Voltage Init analysis result in database
                    updateVoltageInitResultUuid(receiverObj.getNodeUuid(), null);

                    // send notification for stopped computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitFailed() {
        return message -> {
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            String errorMessage = message.getHeaders().get(HEADER_MESSAGE, String.class);
            String userId = message.getHeaders().get(HEADER_USER_ID, String.class);
            UUID resultUuid = UUID.fromString(message.getHeaders().get(RESULT_UUID, String.class));
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                    LOGGER.info("Voltage init failed for node '{}'", receiverObj.getNodeUuid());

                    updateVoltageInitResultUuid(receiverObj.getNodeUuid(), resultUuid);

                    // send notification for failed computation
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());

                    notificationService.emitStudyError(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED, errorMessage, userId);
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
    }
}
