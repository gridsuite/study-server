/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE;
import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

/**
 * @author Souissi Maissa <souissi.maissa at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class ConsumerServiceSharedElementUpdateTest {

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private StudyService studyService;
    @MockitoBean
    private CaseService caseService;
    @MockitoBean
    private LoadFlowRestService loadFlowRestService;
    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private StudyConfigService studyConfigService;
    @MockitoBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockitoBean
    private RootNetworkService rootNetworkService;
    @MockitoBean
    private DirectoryService directoryService;
    @MockitoBean
    private ComputationParametersService computationParametersService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoBean
    private LoadFlowService loadFlowService;
    @MockitoBean
    private NodeActivityRunnerService nodeActivityRunnerService;
    @MockitoBean
    private NodeActivityService nodeActivityService;

    @Autowired
    private ConsumerService consumerService;

    private Consumer<Message<Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>>>> consumeSharedElementUpdate;

    @BeforeEach
    void setup() {
        consumeSharedElementUpdate = consumerService.consumeSharedElementUpdate();
    }

    @Test
    void testStudyNodeReference() {
        UUID nodeUuid = UUID.randomUUID();
        UUID modificationUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(createStudyNodeReference(modificationUuid, nodeUuid)), List.of()));

        verify(studyService, times(1)).sharedModificationsUpdatedNotification(any(), anyList());
        verify(studyService).sharedModificationsUpdatedNotification(nodeUuid, List.of(modificationUuid));
    }

    @Test
    void testNodeCompositeReference() {
        UUID compositeModificationUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(), List.of(createCompositeReference(compositeModificationUuid, nodeUuid))));

        verify(studyService, times(1)).sharedModificationsUpdatedNotification(any(), anyList());
        verify(studyService).sharedModificationsUpdatedNotification(nodeUuid, List.of(compositeModificationUuid));
    }

    @Test
    void testStudyNodeAndCompositeReferencesInSameNode() {
        UUID nodeUuid = UUID.randomUUID();
        UUID directModificationUuid = UUID.randomUUID();
        UUID compositeModificationUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(createStudyNodeReference(directModificationUuid, nodeUuid)),
                List.of(createCompositeReference(compositeModificationUuid, nodeUuid))));

        verify(studyService, times(1)).sharedModificationsUpdatedNotification(any(), anyList());
        verify(studyService, times(1)).sharedModificationsUpdatedNotification(nodeUuid, List.of(directModificationUuid, compositeModificationUuid));
    }

    @Test
    void testStudyNodeAndCompositeReferencesInDifferentNode() {
        UUID node1Uuid = UUID.randomUUID();
        UUID node2Uuid = UUID.randomUUID();
        UUID directModificationUuid = UUID.randomUUID();
        UUID compositeModificationUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
            List.of(createStudyNodeReference(directModificationUuid, node1Uuid)),
            List.of(createCompositeReference(compositeModificationUuid, node2Uuid))));

        verify(studyService, times(2)).sharedModificationsUpdatedNotification(any(), anyList());
        verify(studyService, times(1)).sharedModificationsUpdatedNotification(node1Uuid, List.of(directModificationUuid));
        verify(studyService, times(1)).sharedModificationsUpdatedNotification(node2Uuid, List.of(compositeModificationUuid));
    }

    @Test
    void testNoSharedCompositeUpdated() {
        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(), List.of()));

        verify(studyService, never()).sharedModificationsUpdatedNotification(any(), anyList());
        verifyNoMoreInteractions(networkModificationService, networkModificationTreeService);
    }

    private static ReferenceAttributes createStudyNodeReference(UUID modificationUuid, UUID nodeUuid) {
        // STUDY_NODE: rootContainerId = studyId, containerId = nodeId
        return ReferenceAttributes.createReferenceAttributes(modificationUuid, UUID.randomUUID(), nodeUuid, STUDY_NODE);
    }

    private static ReferenceAttributes createCompositeReference(UUID modificationUuid, UUID nodeUuid) {
        // STUDY_NODE_NETWORK_MODIFICATION: rootContainerId = nodeId, containerId = parentCompositeId
        return ReferenceAttributes.createReferenceAttributes(modificationUuid, nodeUuid, UUID.randomUUID(), STUDY_NODE_NETWORK_MODIFICATION);
    }

    private static Message<Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>>> sharedElementUpdateMessage(
            List<ReferenceAttributes> studyNodeReferences, List<ReferenceAttributes> networkModificationReferences) {
        Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>> referencesByType = new EnumMap<>(ReferenceAttributes.ReferenceType.class);
        if (!studyNodeReferences.isEmpty()) {
            referencesByType.put(STUDY_NODE, studyNodeReferences);
        }
        if (!networkModificationReferences.isEmpty()) {
            referencesByType.put(STUDY_NODE_NETWORK_MODIFICATION, networkModificationReferences);
        }
        return MessageBuilder.withPayload(referencesByType).build();
    }
}
