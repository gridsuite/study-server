/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.ElementReferenceAction;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.NodeInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.nodeactivity.NodeActivityService;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.loadflow.LoadFlowRestService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
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
    private NetworkModificationRestService networkModificationRestService;
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

    private Consumer<Message<List<ModificationReference>>> consumeCompositeReferenceChanges;

    private static final String USER_ID = "userId";
    private final UUID groupUuid = UUID.randomUUID();
    private final UUID nodeUuid = UUID.randomUUID();
    private final UUID studyUuid = UUID.randomUUID();
    private final List<ModificationReference> references = List.of(
            new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), null),
            new ModificationReference(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

    @BeforeEach
    void setup() {
        consumeCompositeReferenceChanges = consumerService.consumeCompositeReferenceChanges();
    }

    @Test
    void testCreateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(new NodeInfos(nodeUuid, "node", studyUuid));

        consumeCompositeReferenceChanges.accept(compositeReferenceMessage(ElementReferenceAction.CREATE));

        verify(directoryService).createElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testUpdateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(new NodeInfos(nodeUuid, "node", studyUuid));

        consumeCompositeReferenceChanges.accept(compositeReferenceMessage(ElementReferenceAction.UPDATE));

        verify(directoryService).updateElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testDeleteReferences() {
        consumeCompositeReferenceChanges.accept(compositeReferenceMessage(ElementReferenceAction.DELETE));

        verify(directoryService).removeElementsReferences(references, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testCreateReferencesWithUnknownGroup() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenThrow(new StudyException(NOT_FOUND, "Node infos not found"));

        Message<List<ModificationReference>> message = compositeReferenceMessage(ElementReferenceAction.CREATE);
        assertThrows(StudyException.class, () -> consumeCompositeReferenceChanges.accept(message));
        verifyNoInteractions(directoryService);
    }

    @Test
    void testRecreateReferences() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenReturn(new NodeInfos(nodeUuid, "node", studyUuid));

        consumeCompositeReferenceChanges.accept(compositeReferenceMessage(ElementReferenceAction.RECREATE));

        verify(directoryService).createElementsReferences(references, studyUuid, nodeUuid, USER_ID);
        verifyNoMoreInteractions(directoryService);
    }

    @Test
    void testRecreateReferencesWithUnknownGroup() {
        when(networkModificationTreeService.findNodeInfosByModificationGroupUuid(groupUuid))
                .thenThrow(new StudyException(NOT_FOUND, "Node infos not found"));

        Message<List<ModificationReference>> message = compositeReferenceMessage(ElementReferenceAction.RECREATE);
        assertThrows(StudyException.class, () -> consumeCompositeReferenceChanges.accept(message));
        verifyNoInteractions(directoryService);
    }

    private Message<List<ModificationReference>> compositeReferenceMessage(ElementReferenceAction action) {
        // headers are received as strings from the broker
        return MessageBuilder.withPayload(references)
                .setHeader(ConsumerService.HEADER_ACTION, action.name())
                .setHeader(ConsumerService.HEADER_GROUP_UUID, groupUuid.toString())
                .setHeader("userId", USER_ID)
                .build();
    }
}
