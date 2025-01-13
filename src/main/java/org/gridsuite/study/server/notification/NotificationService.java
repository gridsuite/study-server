/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.notification.dto.StudyAlert;
import org.gridsuite.study.server.utils.annotations.PostCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    public static final String HEADER_ERROR = "error";
    public static final String HEADER_NODE = "node";
    public static final String HEADER_NODES = "nodes";
    public static final String HEADER_STUDY_UUID = "studyUuid";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String HEADER_COMPUTATION_TYPE = "computationType";
    public static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";

    public static final String UPDATE_TYPE_BUILD_CANCELLED = "buildCancelled";
    public static final String UPDATE_TYPE_BUILD_COMPLETED = "buildCompleted";
    public static final String UPDATE_TYPE_BUILD_FAILED = "buildFailed";
    public static final String UPDATE_TYPE_LOADFLOW_RESULT = "loadflowResult";
    public static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    public static final String UPDATE_TYPE_LOADFLOW_FAILED = "loadflow_failed";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_FAILED = "securityAnalysis_failed";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT = "sensitivityAnalysisResult";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS = "sensitivityAnalysis_status";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED = "sensitivityAnalysis_failed";
    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_RESULT = "nonEvacuatedEnergyResult";
    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS = "nonEvacuatedEnergy_status";
    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_FAILED = "nonEvacuatedEnergy_failed";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_RESULT = "shortCircuitAnalysisResult";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_STATUS = "shortCircuitAnalysis_status";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_FAILED = "shortCircuitAnalysis_failed";
    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT = "oneBusShortCircuitAnalysisResult";
    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS = "oneBusShortCircuitAnalysis_status";
    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED = "oneBusShortCircuitAnalysis_failed";
    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED = "dynamicSimulation_failed";
    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT = "dynamicSimulationResult";
    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS = "dynamicSimulation_status";
    public static final String UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_FAILED = "dynamicSecurityAnalysis_failed";
    public static final String UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_RESULT = "dynamicSecurityAnalysisResult";
    public static final String UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS = "dynamicSecurityAnalysis_status";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_RESULT = "voltageInitResult";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_STATUS = "voltageInit_status";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_FAILED = "voltageInit_failed";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_CANCEL_FAILED = "voltageInit_cancel_failed";
    public static final String UPDATE_TYPE_STUDIES = "studies";
    public static final String UPDATE_TYPE_STUDY_NETWORK_RECREATION_DONE = "study_network_recreation_done";
    public static final String UPDATE_TYPE_STUDY = "study";
    public static final String UPDATE_TYPE_STUDY_METADATA_UPDATED = "metadata_updated";
    public static final String UPDATE_TYPE_INDEXATION_STATUS = "indexation_status_updated";
    public static final String UPDATE_TYPE_STATE_ESTIMATION_FAILED = "stateEstimation_failed";
    public static final String UPDATE_TYPE_STATE_ESTIMATION_RESULT = "stateEstimationResult";
    public static final String UPDATE_TYPE_STATE_ESTIMATION_STATUS = "stateEstimation_status";
    public static final String UPDATE_TYPE_COMPUTATION_PARAMETERS = "computationParametersUpdated";
    public static final String UPDATE_NETWORK_VISUALIZATION_PARAMETERS = "networkVisualizationParametersUpdated";

    public static final String MODIFICATIONS_CREATING_IN_PROGRESS = "creatingInProgress";
    public static final String MODIFICATIONS_STASHING_IN_PROGRESS = "stashingInProgress";
    public static final String MODIFICATIONS_RESTORING_IN_PROGRESS = "restoringInProgress";
    public static final String MODIFICATIONS_DELETING_IN_PROGRESS = "deletingInProgress";
    public static final String MODIFICATIONS_UPDATING_IN_PROGRESS = "updatingInProgress";
    public static final String MODIFICATIONS_UPDATING_FINISHED = "UPDATE_FINISHED";
    public static final String MODIFICATIONS_DELETING_FINISHED = "DELETE_FINISHED";

    public static final String EVENTS_CRUD_CREATING_IN_PROGRESS = "eventCreatingInProgress";
    public static final String EVENTS_CRUD_DELETING_IN_PROGRESS = "eventDeletingInProgress";
    public static final String EVENTS_CRUD_UPDATING_IN_PROGRESS = "eventUpdatingInProgress";
    public static final String EVENTS_CRUD_FINISHED = "EVENT_CRUD_FINISHED";

    public static final String HEADER_INSERT_MODE = "insertMode";
    public static final String HEADER_NEW_NODE = "newNode";
    public static final String HEADER_REFERENCE_NODE_UUID = "referenceNodeUuid";
    public static final String HEADER_MOVED_NODE = "movedNode";
    public static final String HEADER_PARENT_NODE = "parentNode";
    public static final String HEADER_REMOVE_CHILDREN = "removeChildren";
    public static final String HEADER_INDEXATION_STATUS = "indexation_status";

    public static final String NODE_UPDATED = "nodeUpdated";
    public static final String NODE_DELETED = "nodeDeleted";
    public static final String NODE_CREATED = "nodeCreated";
    public static final String NODE_MOVED = "nodeMoved";
    public static final String NODE_RENAMED = "nodeRenamed";
    public static final String NODE_BUILD_STATUS_UPDATED = "nodeBuildStatusUpdated";
    public static final String SUBTREE_MOVED = "subtreeMoved";
    public static final String NODES_COLUMN_POSITIONS_CHANGED = "nodesColumnPositionsChanged";
    public static final String SUBTREE_CREATED = "subtreeCreated";
    public static final String MESSAGE_LOG = "Sending message : {}";
    public static final String DEFAULT_ERROR_MESSAGE = "Unknown error";

    public static final String STUDY_ALERT = "STUDY_ALERT";

    private static final String CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    private final StreamBridge updatePublisher;

    private final ObjectMapper objectMapper;

    public NotificationService(StreamBridge updatePublisher,
                               ObjectMapper objectMapper) {
        this.updatePublisher = updatePublisher;
        this.objectMapper = objectMapper;
    }

    private void sendUpdateMessage(Message<?> message) {
        MESSAGE_OUTPUT_LOGGER.debug(MESSAGE_LOG, message);
        updatePublisher.send("publishStudyUpdate-out-0", message);
    }

    private void sendElementUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug(MESSAGE_LOG, message);
        updatePublisher.send("publishElementUpdate-out-0", message);
    }

    @PostCompletion
    public void emitStudiesChanged(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
                .build());
    }

    @PostCompletion
    public void emitStudyNetworkRecreationDone(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_STUDY_UUID, studyUuid)
            .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_NETWORK_RECREATION_DONE)
            .build());
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build());
    }

    @PostCompletion
    public void emitComputationParamsChanged(UUID studyUuid, ComputationType computationType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
               .setHeader(HEADER_STUDY_UUID, studyUuid)
               .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_COMPUTATION_PARAMETERS)
               .setHeader(HEADER_COMPUTATION_TYPE, computationType.name())
               .build());
    }

    @PostCompletion
    public void emitNetworkVisualizationParamsChanged(UUID studyUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_NETWORK_VISUALIZATION_PARAMETERS)
                .build());
    }

    public void emitStudyCreationError(UUID studyUuid, String userId, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
                // an error message is needed in order for this message to be interpreted later as an error notification
                .setHeader(HEADER_ERROR, (errorMessage == null || errorMessage.isEmpty()) ?
                        DEFAULT_ERROR_MESSAGE :
                        errorMessage)
                .build());
    }

    @PostCompletion
    public void emitStudyError(UUID studyUuid, UUID nodeUuid, String updateType, String errorMessage, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_ERROR, errorMessage)
                .setHeader(HEADER_USER_ID, userId)
                .build());
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
                .build());
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType, NetworkImpactsInfos networkImpactsInfos) {
        try {
            sendUpdateMessage(MessageBuilder.withPayload(objectMapper.writeValueAsString(networkImpactsInfos)).setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build());
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on study update", e);
        }
    }

    @PostCompletion
    public void emitStudyIndexationStatusChanged(UUID studyUuid, StudyIndexationStatus status) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_INDEXATION_STATUS)
                .setHeader(HEADER_INDEXATION_STATUS, status.name())
                .build());
    }

    @PostCompletion
    public void emitStudyMetadataChanged(UUID studyUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_METADATA_UPDATED)
                .build());
    }

    @PostCompletion
    public void emitNodeInserted(UUID studyUuid, UUID parentNode, UUID nodeCreated, InsertMode insertMode, UUID referenceNodeUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_CREATED)
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_NEW_NODE, nodeCreated)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
                .setHeader(HEADER_REFERENCE_NODE_UUID, referenceNodeUuid)
                .build()
        );
    }

    @PostCompletion
    public void emitNodeMoved(UUID studyUuid, UUID parentNode, UUID nodeMoved, InsertMode insertMode, UUID referenceNodeUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_MOVED)
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_MOVED_NODE, nodeMoved)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
                .setHeader(HEADER_REFERENCE_NODE_UUID, referenceNodeUuid)
                .build()
        );
    }

    @PostCompletion
    public void emitNodesChanged(UUID studyUuid, Collection<UUID> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_UPDATED)
                .setHeader(HEADER_NODES, nodes)
                .build()
        );
    }

    @PostCompletion
    public void emitNodeBuildFailed(UUID studyUuid, UUID nodeUuid, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_BUILD_FAILED)
                .setHeader(HEADER_ERROR, errorMessage)
                .build());
    }

    @PostCompletion
    public void emitSubtreeMoved(UUID studyUuid, UUID parentNodeSubtreeMoved, UUID referenceNodeUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, SUBTREE_MOVED)
                .setHeader(HEADER_MOVED_NODE, parentNodeSubtreeMoved)
                .setHeader(HEADER_PARENT_NODE, referenceNodeUuid)
                .build()
        );
    }

    @PostCompletion
    public void emitColumnsChanged(UUID studyUuid, UUID parentNodeUuid, List<UUID> orderedUuids) {
        try {
            sendUpdateMessage(MessageBuilder.withPayload(objectMapper.writeValueAsString(orderedUuids))
                    .setHeader(HEADER_STUDY_UUID, studyUuid)
                    .setHeader(HEADER_UPDATE_TYPE, NODES_COLUMN_POSITIONS_CHANGED)
                    .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                    .build()
            );
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on column positions update", e);
        }
    }

    @PostCompletion
    public void emitSubtreeInserted(UUID studyUuid, UUID parentNodeSubtreeInserted, UUID referenceNodeUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, SUBTREE_CREATED)
                .setHeader(HEADER_NEW_NODE, parentNodeSubtreeInserted)
                .setHeader(HEADER_PARENT_NODE, referenceNodeUuid)
                .build()
        );
    }

    @PostCompletion
    public void emitNodeBuildStatusUpdated(UUID studyUuid, Collection<UUID> nodes) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_BUILD_STATUS_UPDATED)
                .setHeader(HEADER_NODES, nodes)
                .build()
        );
    }

    @PostCompletion
    public void emitNodeRenamed(UUID studyUuid, UUID nodeUuid) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_RENAMED)
                .setHeader(HEADER_NODE, nodeUuid)
                .build()
        );
    }

    @PostCompletion
    public void emitNodesDeleted(UUID studyUuid, Collection<UUID> nodes, boolean deleteChildren) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_DELETED)
                .setHeader(HEADER_NODES, nodes)
                .setHeader(HEADER_REMOVE_CHILDREN, deleteChildren)
                .build()
        );
    }

    public void emitStartModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids, String modificationType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
                .setHeader(HEADER_UPDATE_TYPE, modificationType)
                .build()
        );
    }

    @PostCompletion
    public void emitEndModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
                .setHeader(HEADER_UPDATE_TYPE, MODIFICATIONS_UPDATING_FINISHED)
                .build()
        );
    }

    @PostCompletion
    public void emitEndDeletionEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
                .setHeader(HEADER_UPDATE_TYPE, MODIFICATIONS_DELETING_FINISHED)
                .build()
        );
    }

    public void emitStartEventCrudNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids, String crudType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
                .setHeader(HEADER_UPDATE_TYPE, crudType)
                .build()
        );
    }

    @PostCompletion
    public void emitEndEventCrudNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
                .setHeader(HEADER_UPDATE_TYPE, EVENTS_CRUD_FINISHED)
                .build()
        );
    }

    @PostCompletion
    public void emitElementUpdated(UUID elementUuid, String modifiedBy) {
        sendElementUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_ELEMENT_UUID, elementUuid)
                .setHeader(HEADER_MODIFIED_BY, modifiedBy)
                .setHeader(HEADER_MODIFICATION_DATE, Instant.now())
                .build()
        );
    }

    @PostCompletion
    public void emitStudyAlert(UUID studyUuid, UUID nodeUuid, String userId, StudyAlert studyAlert) {
        try {
            sendUpdateMessage(MessageBuilder.withPayload(objectMapper.writeValueAsString(studyAlert))
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, STUDY_ALERT)
                .build()
            );
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on study alert", e);
        }
    }
}
