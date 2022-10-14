/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.powsybl.shortcircuit.ShortCircuitParameters;
import org.gridsuite.study.server.dto.CaseImportReceiver;
import org.gridsuite.study.server.dto.NetworkInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;

@Service
public class ConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    static final String HEADER_RECEIVER = "receiver";
    static final String RESULT_UUID = "resultUuid";
    static final String NETWORK_UUID = "networkUuid";
    static final String NETWORK_ID = "networkId";
    static final String HEADER_CASE_FORMAT = "caseFormat";
    static final String HEADER_CASE_NAME = "caseName";
    static final String HEADER_ERROR_MESSAGE = "errorMessage";

    private final ObjectMapper objectMapper;

    NotificationService notificationService;
    StudyService studyService;
    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper,
            NotificationService notificationService,
            StudyService studyService,
            NetworkModificationTreeService networkModificationTreeService) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
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
    public Consumer<Message<String>> consumeBuildResult() {
        return message -> {
            Set<String> substationsIds = Stream.of(message.getPayload().trim().split(",")).collect(Collectors.toSet());
            String receiver = message.getHeaders().get(HEADER_RECEIVER, String.class);
            if (receiver != null) {
                NodeReceiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            NodeReceiver.class);

                    LOGGER.info("Build completed for node '{}'", receiverObj.getNodeUuid());

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.BUILT);

                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), NotificationService.UPDATE_TYPE_BUILD_COMPLETED, substationsIds);
                } catch (JsonProcessingException e) {
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

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT);
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

                    updateBuildStatus(receiverObj.getNodeUuid(), BuildStatus.NOT_BUILT);
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
                    ShortCircuitParameters shortCircuitParameters = ShortCircuitAnalysisService.getDefaultShortCircuitParameters();
                    studyService.insertStudy(studyUuid, userId, networkInfos, caseFormat, caseUuid, false, caseName, LoadflowService.toEntity(loadFlowParameters), ShortCircuitAnalysisService.toEntity(shortCircuitParameters), importReportUuid);
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

    private void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        networkModificationTreeService.updateBuildStatus(nodeUuid, buildStatus);
    }

    void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(nodeUuid, sensitivityAnalysisResultUuid);
    }
}
