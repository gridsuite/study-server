/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

import java.util.*;
import java.util.function.Consumer;

import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE;
import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author Souissi Maissa <souissi.maissa at rte-france.com>
 */
@ExtendWith(MockitoExtension.class)
class ConsumerServiceSharedElementUpdateTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private StudyService studyService;
    @Mock
    private CaseService caseService;
    @Mock
    private LoadFlowRestService loadFlowRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private NetworkModificationService networkModificationService;
    @Mock
    private StudyConfigService studyConfigService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private DirectoryService directoryService;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private LoadFlowService loadFlowService;
    @Mock
    private NodeActivityRunnerService nodeActivityRunnerService;
    @Mock
    private NodeActivityService nodeActivityService;

    private Consumer<Message<Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>>>> consumeSharedElementUpdate;

    @BeforeEach
    void setup() {
        ConsumerService consumerService = new ConsumerService(new ObjectMapper(), notificationService, studyService, caseService,
                loadFlowRestService, networkModificationTreeService, networkModificationService, studyConfigService,
                rootNetworkNodeInfoService, rootNetworkService, directoryService, computationParametersService, userAdminService, loadFlowService,
                nodeActivityRunnerService, nodeActivityService);
        consumeSharedElementUpdate = consumerService.consumeSharedElementUpdate();
    }

    @Test
    void directNodeReferenceInvalidatesItsNodeWithoutAnyLookup() {
        UUID nodeUuid = UUID.randomUUID();
        UUID modificationUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(studyNodeReference(modificationUuid, nodeUuid)), List.of()));

        verify(studyService).sharedElementUpdatedNotification(nodeUuid, List.of(modificationUuid));
    }

    @Test
    void compositeReferenceInvalidatesTheNodeCarriedByItsContainer() {
        UUID compositeModificationUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(), List.of(compositeReference(compositeModificationUuid, nodeUuid))));

        verify(studyService).sharedElementUpdatedNotification(nodeUuid, List.of(compositeModificationUuid));
    }

    @Test
    void sameNodeReachedDirectlyAndThroughACompositeIsInvalidatedOnceWithBothModifications() {
        UUID nodeUuid = UUID.randomUUID();
        UUID directModificationUuid = UUID.randomUUID();
        UUID compositeModificationUuid = UUID.randomUUID();

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(
                List.of(studyNodeReference(directModificationUuid, nodeUuid)),
                List.of(compositeReference(compositeModificationUuid, nodeUuid))));

        verify(studyService, times(1)).sharedElementUpdatedNotification(nodeUuid, List.of(directModificationUuid, compositeModificationUuid));
    }

    @Test
    void emptyMessageInvalidatesNothing() {
        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(), List.of()));

        verify(studyService, never()).sharedElementUpdatedNotification(org.mockito.ArgumentMatchers.any(), anyList());
        verifyNoMoreInteractions(networkModificationService, networkModificationTreeService);
    }

    private static ReferenceAttributes studyNodeReference(UUID modificationUuid, UUID nodeUuid) {
        // STUDY_NODE: rootContainerId = studyId, containerId = nodeId
        return ReferenceAttributes.createReferenceAttributes(modificationUuid, UUID.randomUUID(), nodeUuid, STUDY_NODE);
    }

    private static ReferenceAttributes compositeReference(UUID modificationUuid, UUID nodeUuid) {
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
