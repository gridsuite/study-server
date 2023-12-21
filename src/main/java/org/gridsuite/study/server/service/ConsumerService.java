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
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
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
import org.gridsuite.study.server.dto.ComputationType;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;
import static org.gridsuite.study.server.dto.ComputationType.SHORT_CIRCUIT;
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;

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
    StudyRepository studyRepository;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper,
                           NotificationService notificationService,
                           StudyService studyService,
                           CaseService caseService,
                           NetworkModificationTreeService networkModificationTreeService,
                           StudyRepository studyRepository) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.studyService = studyService;
        this.caseService = caseService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.studyRepository = studyRepository;
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

                StudyEntity studyEntity = studyRepository.findById(studyUuid).orElse(null);
                try {
                    LoadFlowParameters loadFlowParameters = LoadFlowParameters.load();
                    ShortCircuitParameters shortCircuitParameters = ShortCircuitService.getDefaultShortCircuitParameters();
                    DynamicSimulationParametersInfos dynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
                    if (studyEntity != null) {
                        // if studyEntity is not null, it means we are recreating network for existing study
                        // we only update network infos sent by network conversion server
                        studyService.updateStudyNetwork(studyEntity, userId, networkInfos);
                    } else {
                        studyService.insertStudy(studyUuid, userId, networkInfos, caseFormat, caseUuid, caseName, LoadFlowService.toEntity(loadFlowParameters, List.of()), ShortCircuitService.toEntity(shortCircuitParameters, ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP), DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper), null, importParameters, importReportUuid);
                    }

                    caseService.disableCaseExpiration(caseUuid);
                } catch (Exception e) {
                    LOGGER.error(e.toString(), e);
                } finally {
                    // if studyEntity is already existing, we don't delete anything in the end of the process
                    if (studyEntity == null) {
                        studyService.deleteStudyIfNotCreationInProgress(studyUuid, userId);
                    }
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

    /**
     * parse the error message from the computation microservice and uses its data to notify the front
     */
    public void consumeCalculationFailed(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        String errorMessage = msg.getHeaders().get(HEADER_MESSAGE, String.class);
        String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
        UUID resultUuid = null;
        String resultId = msg.getHeaders().get(RESULT_UUID, String.class);
        if (resultId != null) {
            resultUuid = UUID.fromString(resultId);
        }
        if (receiver != null && !Strings.isBlank(receiver)) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                LOGGER.info("{} failed for node '{}'", computationType, receiverObj.getNodeUuid());

                String updateType = "";
                // delete computtion results from the databases
                // ==> will probably be removed soon because it prevents the front from recovering the resultId ; or 'null' parameter will be replaced by null like in VOLTAGE_INITIALIZATION
                switch (computationType) {
                    case LOAD_FLOW : {
                        networkModificationTreeService.updateLoadFlowResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_LOADFLOW_FAILED;
                    } break;
                    case SECURITY_ANALYSIS : {
                        networkModificationTreeService.updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED;
                    } break;
                    case SENSITIVITY_ANALYSIS : {
                        networkModificationTreeService.updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED;
                    } break;
                    case SHORT_CIRCUIT : {
                        String busId = msg.getHeaders().get(HEADER_BUS_ID, String.class);
                        ShortcircuitAnalysisType analysisType = (busId == null) ?
                                ShortcircuitAnalysisType.ALL_BUSES :
                                ShortcircuitAnalysisType.ONE_BUS;
                        if (analysisType == ShortcircuitAnalysisType.ALL_BUSES) {
                            networkModificationTreeService.updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                            updateType = NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED;
                        } else {
                            networkModificationTreeService.updateOneBusShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                            updateType = NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED;
                        }
                    } break;
                    case VOLTAGE_INITIALIZATION : {
                        networkModificationTreeService.updateVoltageInitResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateType = NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED;
                    } break;
                    case DYNAMIC_SIMULATION : {
                        networkModificationTreeService.updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED;
                    } break;
                }

                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notification for failed computation
                notificationService.emitStudyError(
                        studyUuid,
                        receiverObj.getNodeUuid(),
                        updateType,
                        errorMessage,
                        userId);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            }
        }
    }

    public void consumeCalculationStopped(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (receiver != null) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                LOGGER.info("{} stopped for node '{}'", computationType, receiverObj.getNodeUuid());

                String updateType = "";
                // delete computation results from the database
                switch (computationType) {
                    case LOAD_FLOW: {
                        networkModificationTreeService.updateLoadFlowResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_LOADFLOW_STATUS;
                    } break;
                    case SECURITY_ANALYSIS: {
                        networkModificationTreeService.updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS;
                    } break;
                    case SENSITIVITY_ANALYSIS: {
                        networkModificationTreeService.updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS;
                    } break;
                    case SHORT_CIRCUIT: {
                        // TODO : this Stopped function is the only one when SHORT_CIRCUIT doesn't handle buses => might cause a bug
                        networkModificationTreeService.updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS;
                    } break;
                    case VOLTAGE_INITIALIZATION: {
                        networkModificationTreeService.updateVoltageInitResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS;
                    } break;
                    case DYNAMIC_SIMULATION: {
                        networkModificationTreeService.updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), null);
                        updateType = NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;
                    } break;
                }

                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notification for stopped computation
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), updateType);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            }
        }
    }

    public void consumeCalculationResult(Message<String> msg, ComputationType computationType) {
        UUID resultUuid = null;
        String resultId = msg.getHeaders().get(RESULT_UUID, String.class);
        if (resultId != null) {
            resultUuid = UUID.fromString(resultId);
        }
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (receiver != null) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                LOGGER.info("{} result '{}' available for node '{}'",
                        computationType,
                        resultUuid,
                        receiverObj.getNodeUuid());

                String updateStatusType = "";
                String updateResultType = "";
                // update DB
                switch (computationType) {
                    case LOAD_FLOW: {
                        networkModificationTreeService.updateLoadFlowResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateStatusType = NotificationService.UPDATE_TYPE_LOADFLOW_STATUS;
                        updateResultType = NotificationService.UPDATE_TYPE_LOADFLOW_RESULT;
                    } break;
                    case SECURITY_ANALYSIS: {
                        networkModificationTreeService.updateSecurityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateStatusType = NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS;
                        updateResultType = NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT;
                    } break;
                    case SENSITIVITY_ANALYSIS: {
                        networkModificationTreeService.updateSensitivityAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateStatusType = NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS;
                        updateResultType = NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT;
                    } break;
                    case SHORT_CIRCUIT: {
                        String busId = msg.getHeaders().get(HEADER_BUS_ID, String.class);
                        ShortcircuitAnalysisType analysisType = (busId == null) ?
                                ShortcircuitAnalysisType.ALL_BUSES :
                                ShortcircuitAnalysisType.ONE_BUS;
                        if (analysisType == ShortcircuitAnalysisType.ALL_BUSES) {
                            networkModificationTreeService.updateShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                            updateStatusType = NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS;
                            updateResultType = NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT;
                        } else {
                            networkModificationTreeService.updateOneBusShortCircuitAnalysisResultUuid(receiverObj.getNodeUuid(), resultUuid);
                            updateStatusType = NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS;
                            updateResultType = NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT;
                        }

                    } break;
                    case VOLTAGE_INITIALIZATION: {
                        networkModificationTreeService.updateVoltageInitResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateStatusType = NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS;
                        updateResultType = NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT;
                    } break;
                    case DYNAMIC_SIMULATION: {
                        networkModificationTreeService.updateDynamicSimulationResultUuid(receiverObj.getNodeUuid(), resultUuid);
                        updateStatusType = NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;
                        updateResultType = NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT;
                    } break;
                }

                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notifications
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), updateStatusType);
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), updateResultType);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            }
        }
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
    public Consumer<Message<String>> consumeShortCircuitAnalysisResult() {
        return message -> consumeCalculationResult(message, SHORT_CIRCUIT);
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisStopped() {
        return message -> consumeCalculationStopped(message, SHORT_CIRCUIT);
    }

    @Bean
    public Consumer<Message<String>> consumeShortCircuitAnalysisFailed() {
        return message -> consumeCalculationFailed(message, SHORT_CIRCUIT);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitResult() {
        return message -> consumeCalculationResult(message, VOLTAGE_INITIALIZATION);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitStopped() {
        return message -> consumeCalculationStopped(message, VOLTAGE_INITIALIZATION);
    }

    @Bean
    public Consumer<Message<String>> consumeVoltageInitFailed() {
        return message -> consumeCalculationFailed(message, VOLTAGE_INITIALIZATION);
    }
}
