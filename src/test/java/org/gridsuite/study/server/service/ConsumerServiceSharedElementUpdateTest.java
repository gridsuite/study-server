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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.NETWORK_MODIFICATION;
import static org.gridsuite.study.server.dto.ReferenceAttributes.ReferenceType.STUDY_NODE;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void directNodeReferenceInvalidatesThatNodeWithoutResolvingModifications() {
        UUID nodeUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        when(networkModificationTreeService.getStudyUuidForNodeId(nodeUuid)).thenReturn(studyUuid);

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(nodeUuid), List.of()));

        verify(studyService).invalidateNodeTreeWhenSharedModificationChanged(studyUuid, nodeUuid);
        verify(networkModificationService, never()).findRootGroupByModification(anyList());
    }

    @Test
    void compositeReferenceIsResolvedThroughItsRootGroupToItsNode() {
        UUID compositeUuid = UUID.randomUUID();
        UUID groupUuid = UUID.randomUUID();
        UUID nodeUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        when(networkModificationService.findRootGroupByModification(List.of(compositeUuid))).thenReturn(Map.of(compositeUuid, groupUuid));
        when(networkModificationTreeService.getNodeUuidsByModificationGroups(List.of(groupUuid))).thenReturn(Map.of(groupUuid, nodeUuid));
        when(networkModificationTreeService.getStudyUuidForNodeId(nodeUuid)).thenReturn(studyUuid);

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(), List.of(compositeUuid)));

        verify(studyService).invalidateNodeTreeWhenSharedModificationChanged(studyUuid, nodeUuid);
    }

    @Test
    void sameNodeReachedDirectlyAndThroughACompositeIsInvalidatedOnce() {
        UUID nodeUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        UUID compositeUuid = UUID.randomUUID();
        UUID groupUuid = UUID.randomUUID();
        when(networkModificationService.findRootGroupByModification(List.of(compositeUuid))).thenReturn(Map.of(compositeUuid, groupUuid));
        when(networkModificationTreeService.getNodeUuidsByModificationGroups(List.of(groupUuid))).thenReturn(Map.of(groupUuid, nodeUuid));
        when(networkModificationTreeService.getStudyUuidForNodeId(nodeUuid)).thenReturn(studyUuid);

        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(nodeUuid), List.of(compositeUuid)));

        verify(studyService, org.mockito.Mockito.times(1)).invalidateNodeTreeWhenSharedModificationChanged(studyUuid, nodeUuid);
    }

    @Test
    void emptyMessageInvalidatesNothing() {
        consumeSharedElementUpdate.accept(sharedElementUpdateMessage(List.of(), List.of()));

        verify(studyService, never()).invalidateNodeTreeWhenSharedModificationChanged(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(networkModificationService, never()).findRootGroupByModification(anyList());
    }

    private static Message<Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>>> sharedElementUpdateMessage(List<UUID> studyNodeUuids, List<UUID> networkModificationUuids) {
        Map<ReferenceAttributes.ReferenceType, List<ReferenceAttributes>> referencesByType = new HashMap<>();
        if (!studyNodeUuids.isEmpty()) {
            referencesByType.put(STUDY_NODE, toReferenceAttributes(studyNodeUuids, STUDY_NODE));
        }
        if (!networkModificationUuids.isEmpty()) {
            referencesByType.put(NETWORK_MODIFICATION, toReferenceAttributes(networkModificationUuids, NETWORK_MODIFICATION));
        }
        return MessageBuilder.withPayload(referencesByType).build();
    }

    private static List<ReferenceAttributes> toReferenceAttributes(List<UUID> uuids, ReferenceAttributes.ReferenceType type) {
        return uuids.stream().map(uuid -> new ReferenceAttributes(uuid, type)).collect(Collectors.toList());
    }
}
