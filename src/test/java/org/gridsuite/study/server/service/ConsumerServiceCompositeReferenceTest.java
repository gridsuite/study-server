/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.NodeInfos;
import org.gridsuite.study.server.dto.ReferenceAction;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * @author Souissi Maissa <souissi.maissa at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class ConsumerServiceCompositeReferenceTest {

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

    private Consumer<Message<List<ModificationReference>>> consumeCompositeReference;

    private static final String USER_ID = "userId";
    private final UUID groupUuid = UUID.randomUUID();
    private final UUID nodeUuid = UUID.randomUUID();
    private final UUID studyUuid = UUID.randomUUID();
    private final List<ModificationReference> references = List.of(
            new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null),
            new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

    @BeforeEach
    void setup() {
        consumeCompositeReference = consumerService.consumeCompositeReference();
    }

    @Test
    void testCreateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(Optional.of(new NodeInfos(nodeUuid, "node", studyUuid)));

        consumeCompositeReference.accept(compositeReferenceMessage(ReferenceAction.CREATE));

        verify(directoryService).createElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testUpdateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(Optional.of(new NodeInfos(nodeUuid, "node", studyUuid)));

        consumeCompositeReference.accept(compositeReferenceMessage(ReferenceAction.UPDATE));

        verify(directoryService).updateElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testDeleteReferences() {
        consumeCompositeReference.accept(compositeReferenceMessage(ReferenceAction.DELETE));

        // the node is not needed to delete a reference: it may well be deleted already
        verify(directoryService).removeElementsReferences(references, USER_ID);
        verifyNoMoreInteractions(directoryService, networkModificationTreeService);
    }

    @Test
    void testCreateReferencesWithUnknownGroup() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid)).thenReturn(Optional.empty());

        Message<List<ModificationReference>> message = compositeReferenceMessage(ReferenceAction.CREATE);
        // failing lets the message be retried, for the node to be committed
        assertThrows(IllegalStateException.class, () -> consumeCompositeReference.accept(message));
        verifyNoInteractions(directoryService);
    }

    @Test
    void testRecreateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(Optional.of(new NodeInfos(nodeUuid, "node", studyUuid)));

        consumerService.consumeCompositeReferenceRecreation().accept(compositeReferenceRecreationMessage());

        verify(directoryService).createElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testRecreateReferencesWithUnknownGroup() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid)).thenReturn(Optional.empty());

        Message<List<ModificationReference>> message = compositeReferenceRecreationMessage();
        Consumer<Message<List<ModificationReference>>> consumeRecreation = consumerService.consumeCompositeReferenceRecreation();
        assertThrows(IllegalStateException.class, () -> consumeRecreation.accept(message));
        verifyNoInteractions(directoryService);
    }

    private Message<List<ModificationReference>> compositeReferenceRecreationMessage() {
        return MessageBuilder.withPayload(references)
                .setHeader(ConsumerService.HEADER_GROUP_UUID, groupUuid.toString())
                .setHeader("userId", USER_ID)
                .build();
    }

    private Message<List<ModificationReference>> compositeReferenceMessage(ReferenceAction action) {
        // headers are received as strings from the broker
        return MessageBuilder.withPayload(references)
                .setHeader(ConsumerService.HEADER_ACTION, action.name())
                .setHeader(ConsumerService.HEADER_GROUP_UUID, groupUuid.toString())
                .setHeader("userId", USER_ID)
                .build();
    }
}
