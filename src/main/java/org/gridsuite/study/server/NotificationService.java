/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

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

import static org.gridsuite.study.server.StudyService.*;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com
 */
@Service
public class NotificationService {

    static final String HEADER_USER_ID = "userId";
    static final String HEADER_STUDY_UUID = "studyUuid";
    static final String HEADER_NODE = "node";
    static final String HEADER_UPDATE_TYPE = "updateType";
    static final String UPDATE_TYPE_STUDIES = "studies";
    static final String UPDATE_TYPE_STUDY_DELETE = "deleteStudy";
    static final String HEADER_ERROR = "error";
    static final String HEADER_UPDATE_TYPE_SUBSTATIONS_IDS = "substationsIds";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID = "deletedEquipmentId";
    static final String HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE = "deletedEquipmentType";

    static final String HEADER_NODES = "nodes";
    static final String HEADER_PARENT_NODE = "parentNode";
    static final String HEADER_NEW_NODE = "newNode";
    static final String HEADER_REMOVE_CHILDREN = "removeChildren";
    static final String NODE_UPDATED = "nodeUpdated";
    static final String NODE_DELETED = "nodeDeleted";
    static final String NODE_CREATED = "nodeCreated";
    static final String HEADER_INSERT_MODE = "insertMode";

    static final String UPDATE_TYPE_STUDY_METADATA_UPDATED = "metadata_updated";

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
    public void emitStudyDelete(UUID studyUuid, String userId) {
        sendUpdateMessage(MessageBuilder.withPayload("")
                .setHeader(HEADER_USER_ID, userId)
                .setHeader(HEADER_STUDY_UUID, studyUuid)
                .setHeader(HEADER_UPDATE_TYPE, UPDATE_TYPE_STUDY_DELETE)
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
