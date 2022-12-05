/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.utils.annotations.PostCompletion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Service
public class NotificationService {

    public static final String HEADER_ERROR = "error";
    public static final String HEADER_NODE = "node";
    public static final String HEADER_NODES = "nodes";
    public static final String HEADER_STUDY_UUID = "studyUuid";
    public static final String HEADER_UPDATE_TYPE = "updateType";
    public static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    public static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID = "deletedEquipmentId";
    public static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE = "deletedEquipmentType";
    public static final String HEADER_USER_ID = "userId";

    public static final String UPDATE_TYPE_BUILD_CANCELLED = "buildCancelled";
    public static final String UPDATE_TYPE_BUILD_COMPLETED = "buildCompleted";
    public static final String UPDATE_TYPE_BUILD_FAILED = "buildFailed";
    public static final String UPDATE_TYPE_LINE = "line";
    public static final String UPDATE_TYPE_LOADFLOW = "loadflow";
    public static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_FAILED = "securityAnalysis_failed";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT = "sensitivityAnalysisResult";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS = "sensitivityAnalysis_status";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED = "sensitivityAnalysis_failed";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_RESULT = "shortCircuitAnalysisResult";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_STATUS = "shortCircuitAnalysis_status";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_FAILED = "shortCircuitAnalysis_failed";
    public static final String UPDATE_TYPE_STUDIES = "studies";
    public static final String UPDATE_TYPE_STUDY = "study";
    public static final String UPDATE_TYPE_STUDY_METADATA_UPDATED = "metadata_updated";
    public static final String UPDATE_TYPE_SWITCH = "switch";

    public static final String MODIFICATIONS_CREATING_IN_PROGRESS = "creatingInProgress";
    public static final String MODIFICATIONS_DELETING_IN_PROGRESS = "deletingInProgress";
    public static final String MODIFICATIONS_UPDATING_IN_PROGRESS = "updatingInProgress";
    public static final String MODIFICATIONS_UPDATING_FINISHED = "UPDATE_FINISHED";

    public static final String HEADER_INSERT_MODE = "insertMode";
    public static final String HEADER_NEW_NODE = "newNode";
    public static final String HEADER_MOVED_NODE = "movedNode";
    public static final String HEADER_PARENT_NODE = "parentNode";
    public static final String HEADER_REMOVE_CHILDREN = "removeChildren";

    public static final String NODE_UPDATED = "nodeUpdated";
    public static final String NODE_DELETED = "nodeDeleted";
    public static final String NODE_CREATED = "nodeCreated";
    public static final String NODE_MOVED = "nodeMoved";

    private static final String CATEGORY_BROKER_OUTPUT = NetworkModificationTreeService.class.getName() + ".output-broker-messages";

    private static final Logger MESSAGE_OUTPUT_LOGGER = LoggerFactory.getLogger(CATEGORY_BROKER_OUTPUT);

    @Autowired
    private StreamBridge updatePublisher;

    private void sendUpdateMessage(Message<String> message) {
        MESSAGE_OUTPUT_LOGGER.debug("Sending message : {}", message);
        updatePublisher.send("publishStudyUpdate-out-0", message);
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
    public void emitStudyChanged(UUID studyUuid, UUID nodeUuid, String updateType) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .build());
    }

    public void emitStudyCreationError(UUID studyUuid, String userId, String errorMessage) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDIES)
                .setHeader(HEADER_ERROR, errorMessage)
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
    public void emitStudyEquipmentDeleted(UUID studyUuid, UUID nodeUuid, String updateType, Set<String> substationsIds, String equipmentType, String equipmentId) {
        sendUpdateMessage(MessageBuilder.withPayload("").setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, updateType)
                .setHeader(HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsIds)
                .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE, equipmentType)
                .setHeader(HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, equipmentId)
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
    public void emitNodeInserted(UUID studyUuid, UUID parentNode, UUID nodeCreated, InsertMode insertMode) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_CREATED)
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_NEW_NODE, nodeCreated)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
                .build()
        );
    }

    @PostCompletion
    public void emitNodeMoved(UUID studyUuid, UUID parentNode, UUID nodeMoved, InsertMode insertMode) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_MOVED)
                .setHeader(HEADER_PARENT_NODE, parentNode)
                .setHeader(HEADER_MOVED_NODE, nodeMoved)
                .setHeader(HEADER_INSERT_MODE, insertMode.name())
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
    public void emitNodesDeleted(UUID studyUuid, Collection<UUID> nodes, boolean deleteChildren) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, NODE_DELETED)
                .setHeader(HEADER_NODES, nodes)
                .setHeader(HEADER_REMOVE_CHILDREN, deleteChildren)
                .build()
        );
    }

    public void emitStartModificationEquipmentNotification(UUID studyUuid, UUID nodeUuid, String modificationType) {

        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, modificationType)
                .build()
        );
    }

    @PostCompletion
    public void emitEndModificationEquipmentNotification(UUID studyUuid, UUID nodeUuid) {

        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_PARENT_NODE, nodeUuid)
                .setHeader(HEADER_UPDATE_TYPE, MODIFICATIONS_UPDATING_FINISHED)
                .build()
        );
    }

}
