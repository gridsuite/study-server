package org.gridsuite.study.server.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gridsuite.study.server.dto.Receiver;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsumerService.class);

    static final String HEADER_RECEIVER = "receiver";
    static final String RESULT_UUID = "resultUuid";

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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
                Receiver receiverObj;
                try {
                    receiverObj = objectMapper.readValue(URLDecoder.decode(receiver, StandardCharsets.UTF_8),
                            Receiver.class);

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

    void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        networkModificationTreeService.updateSecurityAnalysisResultUuid(nodeUuid, securityAnalysisResultUuid);
    }

    private void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        networkModificationTreeService.updateBuildStatus(nodeUuid, buildStatus);
    }
}
