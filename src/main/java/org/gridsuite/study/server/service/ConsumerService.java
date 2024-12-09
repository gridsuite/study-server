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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    static final String RESULT_UUID = "resultUuid";
    static final String NETWORK_UUID = "networkUuid";
    static final String NETWORK_ID = "networkId";
    static final String HEADER_CASE_FORMAT = "caseFormat";
    static final String HEADER_CASE_NAME = "caseName";
    static final String HEADER_ERROR_MESSAGE = "errorMessage";

    private final ObjectMapper objectMapper;

    private final NotificationService notificationService;
    private final StudyService studyService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final CaseService caseService;
    private final LoadFlowService loadFlowService;
    private final UserAdminService userAdminService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final StudyRepository studyRepository;
    private final ShortCircuitService shortCircuitService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final RootNetworkService rootNetworkService;

    @Autowired
    public ConsumerService(ObjectMapper objectMapper,
                           NotificationService notificationService,
                           StudyService studyService,
                           SecurityAnalysisService securityAnalysisService,
                           CaseService caseService,
                           LoadFlowService loadFlowService,
                           ShortCircuitService shortCircuitService,
                           UserAdminService userAdminService,
                           NetworkModificationTreeService networkModificationTreeService,
                           SensitivityAnalysisService sensitivityAnalysisService,
                           StudyRepository studyRepository,
                           RootNetworkNodeInfoService rootNetworkNodeInfoService,
                           RootNetworkService rootNetworkservice) {
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.studyService = studyService;
        this.securityAnalysisService = securityAnalysisService;
        this.caseService = caseService;
        this.loadFlowService = loadFlowService;
        this.userAdminService = userAdminService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.studyRepository = studyRepository;
        this.shortCircuitService = shortCircuitService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.rootNetworkService = rootNetworkservice;
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

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(),
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

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

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

                    networkModificationTreeService.updateNodeBuildStatus(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), NodeBuildStatus.from(BuildStatus.NOT_BUILT));

                    // send notification
                    UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                    notificationService.emitNodeBuildFailed(studyUuid, receiverObj.getNodeUuid(), message.getHeaders().get(HEADER_MESSAGE, String.class));
                } catch (JsonProcessingException e) {
                    LOGGER.error(e.toString());
                }
            }
        };
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

                UUID caseUuid = receiver.getCaseUuid();
                UUID studyUuid = receiver.getStudyUuid();
                String userId = receiver.getUserId();
                Long startTime = receiver.getStartTime();
                UUID importReportUuid = receiver.getReportUuid();
                UUID rootNetworkUuid = receiver.getRootNetworkUuid();
                CaseImportAction caseImportAction = receiver.getCaseImportAction();

                CaseInfos caseInfos = new CaseInfos(caseUuid, caseName, caseFormat);
                NetworkInfos networkInfos = new NetworkInfos(networkUuid, networkId);
                StudyEntity studyEntity = studyRepository.findWithRootNetworksById(studyUuid).orElse(null);
                try {
                    switch (caseImportAction) {
                        case STUDY_CREATION ->
                            insertStudy(studyUuid, userId, networkInfos, caseInfos, importParameters, importReportUuid);
                        case ROOT_NETWORK_CREATION ->
                            rootNetworkService.createRootNetworkFromRequest(studyEntity, RootNetworkInfos.builder()
                                .id(rootNetworkUuid)
                                .caseInfos(caseInfos)
                                .reportUuid(importReportUuid)
                                .networkInfos(networkInfos)
                                .importParameters(importParameters)
                                .build());
                        case NETWORK_RECREATION ->
                            recreateNetworkOfRootNetwork(studyEntity, rootNetworkUuid, userId, networkInfos);
                    }
                    caseService.disableCaseExpiration(caseUuid);
                } catch (Exception e) {
                    LOGGER.error("Error while importing case", e);
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

    private void insertStudy(UUID studyUuid, String userId, NetworkInfos networkInfos, CaseInfos caseInfos,
                             Map<String, String> importParameters, UUID importReportUuid) {
        DynamicSimulationParametersInfos dynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
        UUID loadFlowParametersUuid = createDefaultLoadFlowParameters(userId, getUserProfile(userId));
        UUID shortCircuitParametersUuid = createDefaultShortCircuitAnalysisParameters();
        UUID securityAnalysisParametersUuid = createDefaultSecurityAnalysisParameters();
        UUID sensitivityAnalysisParametersUuid = createDefaultSensitivityAnalysisParameters();
        studyService.insertStudy(studyUuid, userId, networkInfos, caseInfos, loadFlowParametersUuid, shortCircuitParametersUuid, DynamicSimulationService.toEntity(dynamicSimulationParameters, objectMapper), null, securityAnalysisParametersUuid, sensitivityAnalysisParametersUuid, importParameters, importReportUuid);
    }

    private void recreateNetworkOfRootNetwork(StudyEntity studyEntity, UUID rootNetworkUuid, String userId, NetworkInfos networkInfos) {
        // TODO: what to do here ? throwing exception will provoke retried and won't notify frontend
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkUuid).orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
        studyService.updateStudyNetwork(studyEntity, rootNetworkEntity, userId, networkInfos);
    }

    private UserProfileInfos getUserProfile(String userId) {
        try {
            return userAdminService.getUserProfile(userId).orElse(null);
        } catch (Exception e) {
            LOGGER.error(String.format("Could not access to profile for user '%s'", userId), e);
        }
        return null;
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

    private UUID createDefaultShortCircuitAnalysisParameters() {
        try {
            return shortCircuitService.createParameters(null);
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for ShortCircuit analysis", e);
            return null;
        }
    }

    private UUID createDefaultSensitivityAnalysisParameters() {
        try {
            return sensitivityAnalysisService.createDefaultSensitivityAnalysisParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for Sensitivity analysis", e);
            return null;
        }
    }

    private UUID createDefaultSecurityAnalysisParameters() {
        try {
            return securityAnalysisService.createDefaultSecurityAnalysisParameters();
        } catch (final Exception e) {
            LOGGER.error("Error while creating default parameters for Security analysis", e);
            return null;
        }
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
     * processes the error message from the computation microservice and uses its data to notify the front
     */
    public void consumeCalculationFailed(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        String errorMessage = msg.getHeaders().get(HEADER_MESSAGE, String.class);
        String userId = msg.getHeaders().get(HEADER_USER_ID, String.class);
        UUID resultUuid = null;
        // resultUuid is only used for the voltage initialization computation, I don't know why
        if (computationType == VOLTAGE_INITIALIZATION) {
            String resultId = msg.getHeaders().get(RESULT_UUID, String.class);
            if (resultId != null) {
                resultUuid = UUID.fromString(resultId);
            }
        }
        if (!Strings.isBlank(receiver)) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                LOGGER.info("{} failed for node '{}'", computationType.getLabel(), receiverObj.getNodeUuid());

                // delete computation results from the databases
                // ==> will probably be removed soon because it prevents the front from recovering the resultId ; or 'null' parameter will be replaced by null like in VOLTAGE_INITIALIZATION
                rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), resultUuid, computationType);

                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notification for failed computation
                notificationService.emitStudyError(
                        studyUuid,
                        receiverObj.getNodeUuid(),
                        computationType.getUpdateFailedType(),
                        errorMessage,
                        userId);
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
            }
        }
    }

    public void consumeCalculationStopped(Message<String> msg, ComputationType computationType) {
        String receiver = msg.getHeaders().get(HEADER_RECEIVER, String.class);
        if (!Strings.isBlank(receiver)) {
            NodeReceiver receiverObj;
            try {
                receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8), NodeReceiver.class);

                // delete computation results from the database
                rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), null, computationType);
                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notification for stopped computation
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), computationType.getUpdateStatusType());

                LOGGER.info("{} stopped for node '{}'", computationType.getLabel(), receiverObj.getNodeUuid());
            } catch (JsonProcessingException e) {
                LOGGER.error(e.toString());
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

    public void consumeCalculationResult(Message<String> msg, ComputationType computationType) {
        Optional.ofNullable(msg.getHeaders().get(RESULT_UUID, String.class))
            .map(UUID::fromString)
            .ifPresent(resultUuid -> getNodeReceiver(msg).ifPresent(receiverObj -> {
                LOGGER.info("{} result '{}' available for node '{}'",
                    computationType.getLabel(),
                    resultUuid,
                    receiverObj.getNodeUuid());

                // update DB
                rootNetworkNodeInfoService.updateComputationResultUuid(receiverObj.getNodeUuid(), receiverObj.getRootNetworkUuid(), resultUuid, computationType);

                UUID studyUuid = networkModificationTreeService.getStudyUuidForNodeId(receiverObj.getNodeUuid());
                // send notifications
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), computationType.getUpdateStatusType());
                notificationService.emitStudyChanged(studyUuid, receiverObj.getNodeUuid(), computationType.getUpdateResultType());
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
    public Consumer<Message<String>> consumeNonEvacuatedEnergyResult() {
        return message -> consumeCalculationResult(message, NON_EVACUATED_ENERGY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeNonEvacuatedEnergyStopped() {
        return message -> consumeCalculationStopped(message, NON_EVACUATED_ENERGY_ANALYSIS);
    }

    @Bean
    public Consumer<Message<String>> consumeNonEvacuatedEnergyFailed() {
        return message -> consumeCalculationFailed(message, NON_EVACUATED_ENERGY_ANALYSIS);
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
}
