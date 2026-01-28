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
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.notification.dto.StudyAlert;
import org.gridsuite.study.server.utils.annotations.PostCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Service
public class NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationService.class);

    public static final String HEADER_ERROR = "error";
    public static final String HEADER_NODE = "node";
    public static final String HEADER_ROOT_NETWORK_UUID = "rootNetworkUuid";
    public static final String HEADER_NODES = "nodes";
    public static final String HEADER_ROOT_NETWORKS_UUIDS = "rootNetworksUuids";
    public static final String HEADER_STUDY_UUID = "studyUuid";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String HEADER_COMPUTATION_TYPE = "computationType";
    public static final String HEADER_RESULT_UUID = "resultUuid";
    public static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_MODIFIED_BY = "modifiedBy";
    public static final String HEADER_MODIFICATION_DATE = "modificationDate";
    public static final String HEADER_ELEMENT_UUID = "elementUuid";
    public static final String HEADER_EXPORT_UUID = "exportUuid";
    public static final String HEADER_EXPORT_TO_GRID_EXPLORE = "exportToGridExplore";
    public static final String HEADER_WORKSPACE_UUID = "workspaceUuid";
    public static final String HEADER_PANEL_ID = "panelId";
    public static final String HEADER_CLIENT_ID = "clientId";
    public static final String NETWORK_EXPORT_FINISHED = "networkExportFinished";

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
    public static final String UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_FAILED = "dynamicMarginCalculation_failed";
    public static final String UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_RESULT = "dynamicMarginCalculationResult";
    public static final String UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS = "dynamicMarginCalculation_status";
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
    public static final String UPDATE_TYPE_PCC_MIN_FAILED = "pccMin_failed";
    public static final String UPDATE_TYPE_PCC_MIN_RESULT = "pccMinResult";
    public static final String UPDATE_TYPE_PCC_MIN_STATUS = "pccMin_status";
    public static final String UPDATE_TYPE_COMPUTATION_PARAMETERS = "computationParametersUpdated";
    public static final String UPDATE_NETWORK_VISUALIZATION_PARAMETERS = "networkVisualizationParametersUpdated";
    public static final String UPDATE_SPREADSHEET_NODE_ALIASES = "nodeAliasesUpdated";
    public static final String UPDATE_SPREADSHEET_TAB = "spreadsheetTabUpdated";
    public static final String UPDATE_SPREADSHEET_COLLECTION = "spreadsheetCollectionUpdated";
    public static final String UPDATE_SPREADSHEET_PARAMETERS = "spreadsheetParametersUpdated";
    public static final String UPDATE_WORKSPACE_RENAMED = "workspaceRenamed";
    public static final String UPDATE_WORKSPACE_PANELS = "workspacePanelsUpdated";
    public static final String DELETE_WORKSPACE_PANELS = "workspacePanelsDeleted";
    public static final String UPDATE_WORKSPACE_NAD_CONFIG = "workspaceNadConfigUpdated";

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
    public static final String NODE_EDITED = "nodeEdited";
    public static final String NODE_BUILD_STATUS_UPDATED = "nodeBuildStatusUpdated";
    public static final String SUBTREE_MOVED = "subtreeMoved";
    public static final String NODES_COLUMN_POSITIONS_CHANGED = "nodesColumnPositionsChanged";
    public static final String SUBTREE_CREATED = "subtreeCreated";
    public static final String MESSAGE_LOG = "Sending message : {}";
    public static final String DEFAULT_ERROR_MESSAGE = "Unknown error";

    public static final String ROOT_NETWORKS_UPDATED = "rootNetworksUpdated";
    public static final String ROOT_NETWORKS_DELETION_STARTED = "rootNetworksDeletionStarted";
    public static final String ROOT_NETWORKS_UPDATE_FAILED = "rootNetworksUpdateFailed";

    public static final String STUDY_ALERT = "STUDY_ALERT";
    public static final String COMPUTATION_DEBUG_FILE_STATUS = "computationDebugFileStatus";

    private static final String CATEGORY_BROKER_OUTPUT = NotificationService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    public static final List<String> ALL_COMPUTATION_STATUS = List.of(
            UPDATE_TYPE_LOADFLOW_STATUS,
            UPDATE_TYPE_SECURITY_ANALYSIS_STATUS,
            UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS,
            UPDATE_TYPE_SHORT_CIRCUIT_STATUS,
            UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS,
            UPDATE_TYPE_VOLTAGE_INIT_STATUS,
            UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS,
            UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS,
            UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS,
            UPDATE_TYPE_STATE_ESTIMATION_STATUS,
            UPDATE_TYPE_PCC_MIN_STATUS
    );

    private final StreamBridge updatePublisher;

    private final ObjectMapper objectMapper;

    public NotificationService(StreamBridge updatePublisher,
                               ObjectMapper objectMapper) {
        this.updatePublisher = updatePublisher;
        this.objectMapper = objectMapper;
    }

    // For publishStudyUpdate-out-0 queue
    private void sendStudyUpdateMessage(@NonNull UUID studyUuid, @NonNull String type, @NonNull MessageBuilder<String> builder) {
        // Always give the concerned studyUuid and define a notification type
        Message<?> message = builder.setHeader(HEADER_STUDY_UUID, studyUuid).setHeader(HEADER_UPDATE_TYPE, type).build();
        MESSAGE_OUTPUT_LOGGER.debug(MESSAGE_LOG, message);
        updatePublisher.send("publishStudyUpdate-out-0", message);
    }

    @PostCompletion
    public void emitStudiesChanged(UUID studyUuid, String userId) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_STUDIES, MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
        );
    }

    @PostCompletion
    public void emitStudyNetworkRecreationDone(UUID studyUuid, String userId) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_STUDY_NETWORK_RECREATION_DONE, MessageBuilder.withPayload("")
            .setHeader(HEADER_USER_ID, userId)
        );
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String updateType) {
        sendStudyUpdateMessage(studyUuid, updateType, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
        );
    }

    @PostCompletion
    public void emitComputationParamsChanged(UUID studyUuid, ComputationType computationType) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_COMPUTATION_PARAMETERS, MessageBuilder.withPayload("")
               .setHeader(HEADER_COMPUTATION_TYPE, computationType.name())
        );
    }

    @PostCompletion
    public void emitSpreadsheetNodeAliasesChanged(UUID studyUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_SPREADSHEET_NODE_ALIASES, MessageBuilder.withPayload("")
        );
    }

    @PostCompletion
    public void emitSpreadsheetConfigChanged(UUID studyUuid, UUID configUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_SPREADSHEET_TAB, MessageBuilder.withPayload(configUuid.toString()));
    }

    @PostCompletion
    public void emitSpreadsheetCollectionChanged(UUID studyUuid, UUID collectionUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_SPREADSHEET_COLLECTION, MessageBuilder.withPayload(collectionUuid.toString()));
    }

    @PostCompletion
    public void emitWorkspaceUpdated(UUID studyUuid, UUID workspaceId, String clientId) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(workspaceId.toString());
        if (clientId != null) {
            builder.setHeader(HEADER_CLIENT_ID, clientId);
        }
        sendStudyUpdateMessage(studyUuid, UPDATE_WORKSPACE_RENAMED, builder);
    }

    @PostCompletion
    public void emitWorkspacePanelsUpdated(UUID studyUuid, UUID workspaceId, String panelIds, String clientId) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(panelIds)
                .setHeader(HEADER_WORKSPACE_UUID, workspaceId.toString());
        if (clientId != null) {
            builder.setHeader(HEADER_CLIENT_ID, clientId);
        }
        sendStudyUpdateMessage(studyUuid, UPDATE_WORKSPACE_PANELS, builder);
    }

    @PostCompletion
    public void emitWorkspacePanelsDeleted(UUID studyUuid, UUID workspaceId, String panelIds, String clientId) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(panelIds)
                .setHeader(HEADER_WORKSPACE_UUID, workspaceId.toString());
        if (clientId != null) {
            builder.setHeader(HEADER_CLIENT_ID, clientId);
        }
        sendStudyUpdateMessage(studyUuid, DELETE_WORKSPACE_PANELS, builder);
    }

    @PostCompletion
    public void emitWorkspaceNadConfigUpdated(UUID studyUuid, UUID workspaceId, UUID panelId, UUID workspaceNadConfigUuid, String clientId) {
        MessageBuilder<String> builder = MessageBuilder.withPayload(workspaceNadConfigUuid.toString())
                .setHeader(HEADER_WORKSPACE_UUID, workspaceId.toString())
                .setHeader(HEADER_PANEL_ID, panelId.toString());
        if (clientId != null) {
            builder.setHeader(HEADER_CLIENT_ID, clientId);
        }
        sendStudyUpdateMessage(studyUuid, UPDATE_WORKSPACE_NAD_CONFIG, builder);
    }

    @PostCompletion
    public void emitSpreadsheetParametersChange(UUID studyUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_SPREADSHEET_PARAMETERS, MessageBuilder.withPayload(""));
    }

    @PostCompletion
    public void emitNetworkVisualizationParamsChanged(UUID studyUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_NETWORK_VISUALIZATION_PARAMETERS, MessageBuilder.withPayload("")
        );
    }

    public void emitStudyCreationError(UUID studyUuid, String userId, String errorMessage) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_STUDIES, MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                // an error message is needed in order for this message to be interpreted later as an error notification
                .setHeader(HEADER_ERROR, (errorMessage == null || errorMessage.isEmpty()) ?
                        DEFAULT_ERROR_MESSAGE :
                        errorMessage)
        );
    }

    @PostCompletion
    public void emitStudyError(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String updateType, String errorMessage, String userId) {
        sendStudyUpdateMessage(studyUuid, updateType, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
                .setHeader(HEADER_ERROR, errorMessage)
                .setHeader(HEADER_USER_ID, userId)
        );
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String updateType, Set<String> substationsIds) {
        sendStudyUpdateMessage(studyUuid, updateType, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
                .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
        );
    }

    @PostCompletion
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String updateType, NetworkImpactsInfos networkImpactsInfos) {
        try {
            sendStudyUpdateMessage(studyUuid, updateType, MessageBuilder.withPayload(objectMapper.writeValueAsString(networkImpactsInfos))
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
            );
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on study update", e);
        }
    }

    @PostCompletion
    public void emitRootNetworkIndexationStatusChanged(UUID studyUuid, UUID rootNetworkUuid, RootNetworkIndexationStatus status) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_INDEXATION_STATUS, MessageBuilder.withPayload("")
                .setHeader(HEADER_INDEXATION_STATUS, status.name())
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
        );
    }

    @PostCompletion
    public void emitStudyMetadataChanged(UUID studyUuid) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_STUDY_METADATA_UPDATED, MessageBuilder.withPayload("")
        );
    }

    @PostCompletion
    public void emitNodeInserted(UUID studyUuid, UUID parentNode, UUID nodeCreated, InsertMode insertMode, UUID referenceNodeUuid) {
        sendStudyUpdateMessage(studyUuid, NODE_CREATED, MessageBuilder.withPayload("")
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_NEW_NODE, nodeCreated)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
                .setHeader(HEADER_REFERENCE_NODE_UUID, referenceNodeUuid)
        );
    }

    @PostCompletion
    public void emitNodeMoved(UUID studyUuid, UUID parentNode, UUID nodeMoved, InsertMode insertMode, UUID referenceNodeUuid) {
        sendStudyUpdateMessage(studyUuid, NODE_MOVED, MessageBuilder.withPayload("")
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_MOVED_NODE, nodeMoved)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
                .setHeader(HEADER_REFERENCE_NODE_UUID, referenceNodeUuid)
        );
    }

    @PostCompletion
    public void emitNodesChanged(UUID studyUuid, Collection<UUID> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        sendStudyUpdateMessage(studyUuid, NODE_UPDATED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODES, nodes)
        );
    }

    @PostCompletion
    public void emitNodeBuildFailed(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String errorMessage) {
        sendStudyUpdateMessage(studyUuid, UPDATE_TYPE_BUILD_FAILED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
                .setHeader(HEADER_ERROR, errorMessage)
        );
    }

    @PostCompletion
    public void emitSubtreeMoved(UUID studyUuid, UUID parentNodeSubtreeMoved, UUID referenceNodeUuid) {
        sendStudyUpdateMessage(studyUuid, SUBTREE_MOVED, MessageBuilder.withPayload("")
                .setHeader(HEADER_MOVED_NODE, parentNodeSubtreeMoved)
                .setHeader(HEADER_PARENT_NODE, referenceNodeUuid)
        );
    }

    @PostCompletion
    public void emitColumnsChanged(UUID studyUuid, UUID parentNodeUuid, List<UUID> orderedUuids) {
        try {
            sendStudyUpdateMessage(studyUuid, NODES_COLUMN_POSITIONS_CHANGED, MessageBuilder.withPayload(objectMapper.writeValueAsString(orderedUuids))
                    .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
            );
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on column positions update", e);
        }
    }

    @PostCompletion
    public void emitSubtreeInserted(UUID studyUuid, UUID parentNodeSubtreeInserted, UUID referenceNodeUuid) {
        sendStudyUpdateMessage(studyUuid, SUBTREE_CREATED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NEW_NODE, parentNodeSubtreeInserted)
                .setHeader(HEADER_PARENT_NODE, referenceNodeUuid)
        );
    }

    @PostCompletion
    public void emitNodeBuildStatusUpdated(UUID studyUuid, List<UUID> nodes, UUID rootNetworkUuid) {
        sendStudyUpdateMessage(studyUuid, NODE_BUILD_STATUS_UPDATED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODES, nodes)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
        );
    }

    @PostCompletion
    public void emitNodeEdited(UUID studyUuid, UUID nodeUuid) {
        sendStudyUpdateMessage(studyUuid, NODE_EDITED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODE, nodeUuid)
        );
    }

    @PostCompletion
    public void emitNodesDeleted(UUID studyUuid, Collection<UUID> nodes, boolean deleteChildren) {
        sendStudyUpdateMessage(studyUuid, NODE_DELETED, MessageBuilder.withPayload("")
                .setHeader(HEADER_NODES, nodes)
                .setHeader(HEADER_REMOVE_CHILDREN, deleteChildren)
        );
    }

    public void emitStartModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids, String modificationType) {
        emitStartModificationEquipmentNotification(studyUuid, parentNodeUuid, Optional.empty(), childrenUuids, modificationType);
    }

    public void emitStartModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Optional<UUID> rootNetworkUuid, Collection<UUID> childrenUuids, String modificationType) {
        MessageBuilder<String> builder = MessageBuilder.withPayload("")
            .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
            .setHeader(HEADER_NODES, childrenUuids);
        rootNetworkUuid.ifPresent(uuid -> builder.setHeader(HEADER_ROOT_NETWORK_UUID, uuid));

        sendStudyUpdateMessage(studyUuid, modificationType, builder);
    }

    @PostCompletion
    public void emitEndModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        emitEndModificationEquipmentNotification(studyUuid, parentNodeUuid, Optional.empty(), childrenUuids);
    }

    @PostCompletion
    public void emitEndModificationEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Optional<UUID> rootNetworkUuid, Collection<UUID> childrenUuids) {
        MessageBuilder<String> builder = MessageBuilder.withPayload("")
            .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
            .setHeader(HEADER_NODES, childrenUuids);
        rootNetworkUuid.ifPresent(uuid -> builder.setHeader(HEADER_ROOT_NETWORK_UUID, uuid));

        sendStudyUpdateMessage(studyUuid, MODIFICATIONS_UPDATING_FINISHED, builder);
    }

    @PostCompletion
    public void emitEndDeletionEquipmentNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        sendStudyUpdateMessage(studyUuid, MODIFICATIONS_DELETING_FINISHED, MessageBuilder.withPayload("")
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
        );
    }

    public void emitStartEventCrudNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids, String crudType) {
        sendStudyUpdateMessage(studyUuid, crudType, MessageBuilder.withPayload("")
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
        );
    }

    @PostCompletion
    public void emitEndEventCrudNotification(UUID studyUuid, UUID parentNodeUuid, Collection<UUID> childrenUuids) {
        sendStudyUpdateMessage(studyUuid, EVENTS_CRUD_FINISHED, MessageBuilder.withPayload("")
                .setHeader(HEADER_PARENT_NODE, parentNodeUuid)
                .setHeader(HEADER_NODES, childrenUuids)
        );
    }

    @PostCompletion
    public void emitStudyAlert(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId, StudyAlert studyAlert) {
        try {
            sendStudyUpdateMessage(studyUuid, STUDY_ALERT, MessageBuilder.withPayload(objectMapper.writeValueAsString(studyAlert))
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
            );
        } catch (JsonProcessingException e) {
            LOGGER.error("Unable to notify on study alert", e);
        }
    }

    @PostCompletion
    public void emitComputationDebugFileStatus(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, ComputationType computationType, String userId, UUID resultUuid, @Nullable String error) {
        sendStudyUpdateMessage(studyUuid, COMPUTATION_DEBUG_FILE_STATUS, MessageBuilder.withPayload("")
            .setHeader(HEADER_NODE, nodeUuid)
            .setHeader(HEADER_ROOT_NETWORK_UUID, rootNetworkUuid)
            .setHeader(HEADER_COMPUTATION_TYPE, computationType.name())
            .setHeader(HEADER_USER_ID, userId)
            .setHeader(HEADER_RESULT_UUID, resultUuid)
            .setHeader(HEADER_ERROR, error)
        );
    }

    private void emitRootNetworksUpdated(UUID studyUuid, List<UUID> rootNetworksUuids) {
        MessageBuilder<String> builder = MessageBuilder.withPayload("");
        if (!rootNetworksUuids.isEmpty()) {
            builder.setHeader(HEADER_ROOT_NETWORKS_UUIDS, rootNetworksUuids);
        }
        sendStudyUpdateMessage(studyUuid, ROOT_NETWORKS_UPDATED, builder);
    }

    @PostCompletion
    public void emitRootNetworksUpdated(UUID studyUuid) {
        emitRootNetworksUpdated(studyUuid, Collections.emptyList());
    }

    @PostCompletion
    public void emitRootNetworkUpdated(UUID studyUuid, UUID rootNetworkUuid) {
        emitRootNetworksUpdated(studyUuid, List.of(rootNetworkUuid));
    }

    public void emitRootNetworksDeletionStarted(UUID studyUuid, List<UUID> rootNetworksUuids) {
        sendStudyUpdateMessage(studyUuid, ROOT_NETWORKS_DELETION_STARTED, MessageBuilder.withPayload("")
            .setHeader(HEADER_ROOT_NETWORKS_UUIDS, rootNetworksUuids)
        );
    }

    @PostCompletion
    public void emitRootNetworksUpdateFailed(UUID studyUuid, String errorMessage) {
        sendStudyUpdateMessage(studyUuid, ROOT_NETWORKS_UPDATE_FAILED, MessageBuilder.withPayload("")
            .setHeader(HEADER_ERROR, errorMessage)
        );
    }

    // For publishElementUpdate-out-0 queue
    private void sendElementUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug(MESSAGE_LOG, message);
        updatePublisher.send("publishElementUpdate-out-0", message);
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
    public void emitNetworkExportFinished(UUID studyUuid, UUID exportUuid, @NonNull Boolean exportoGridExplore, String userId, @Nullable String error) {
        sendStudyUpdateMessage(studyUuid, NETWORK_EXPORT_FINISHED, MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_EXPORT_UUID, exportUuid)
                .setHeader(HEADER_EXPORT_TO_GRID_EXPLORE, exportoGridExplore)
                .setHeader(HEADER_ERROR, error)
        );
    }
}
