/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import org.gridsuite.study.server.dto.ModificationsToCopyInfos;
import org.gridsuite.study.server.service.RebuildNodeService;
import org.gridsuite.study.server.StudyConstants;
import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.modification.NetworkModificationMetadata;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class StudyControllerRebuildNodeTest {
    @MockitoSpyBean
    private RebuildNodeService rebuildNodeService;

    // this test is only making sure all those endpoint are actually calling rebuildPreviouslyBuiltNodeHandler
    // we mock studyService since we don't cover all the assertions
    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyController studyController;

    private final UUID studyUuid = UUID.randomUUID();
    private final UUID rootNetworkUuid = UUID.randomUUID();
    private final UUID nodeUuid = UUID.randomUUID();
    private final UUID modificationUuid = UUID.randomUUID();

    private final String userId = "userId";

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> Map.of(rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT))).when(studyService).getNodeBuildStatusByRootNetwork(any(), any());

        doAnswer(invocation -> List.of(nodeUuid)).when(networkModificationTreeService).getHighestNodeUuids(any(), any());
        doAnswer(invocation -> false).when(networkModificationTreeService).isRootOrConstructionNode(any());
    }

    @Test
    void testCreateNetworkModification() {
        studyController.createNetworkModification(studyUuid, nodeUuid, "modificationBody", userId);

        verify(rebuildNodeService, times(1)).createNetworkModification(studyUuid, nodeUuid, "modificationBody", userId);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void testMoveNetworkModification() {
        studyController.moveModification(studyUuid, nodeUuid, modificationUuid, null, userId);

        verify(rebuildNodeService, times(1)).moveNetworkModification(eq(studyUuid), eq(nodeUuid), eq(modificationUuid), isNull(), eq(userId));
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void testMoveNetworkModifications() {
        List<ModificationsToCopyInfos> modifications = List.of(ModificationsToCopyInfos.builder().uuid(UUID.randomUUID()).build());
        UUID originNodeUuid = UUID.randomUUID();
        studyController.moveOrCopyModifications(studyUuid, nodeUuid, StudyConstants.ModificationsActionType.MOVE, studyUuid, originNodeUuid, modifications, userId);

        List<UUID> modificationUuids = modifications.stream().map(ModificationsToCopyInfos::getUuid).toList();
        verify(rebuildNodeService, times(1)).moveNetworkModifications(studyUuid, nodeUuid, originNodeUuid, modificationUuids, userId);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void updateNetworkModification() {
        studyController.updateNetworkModification(studyUuid, nodeUuid, modificationUuid, "modificationAttributes", userId);

        verify(rebuildNodeService, times(1)).updateNetworkModification(studyUuid, "modificationAttributes", nodeUuid, modificationUuid, userId);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void stashNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        studyController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, true, userId);

        verify(rebuildNodeService, times(1)).stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, userId);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void restoreNetworkModification() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        studyController.stashNetworkModifications(studyUuid, nodeUuid, modificationUuids, false, userId);

        verify(rebuildNodeService, times(1)).restoreNetworkModifications(studyUuid, nodeUuid, modificationUuids, userId);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    // when a modification is enabled/disabled, this method is called
    @Test
    void updateNetworkModificationMetadata() {
        List<UUID> modificationUuids = List.of(UUID.randomUUID());
        NetworkModificationMetadata networkModificationMetadata = new NetworkModificationMetadata(true, "description", "type");
        studyController.updateNetworkModificationsMetadata(studyUuid, nodeUuid, modificationUuids, networkModificationMetadata, userId);

        verify(rebuildNodeService, times(1)).updateNetworkModificationsMetadata(studyUuid, nodeUuid, modificationUuids, userId, networkModificationMetadata);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }

    @Test
    void testUpdateNetworkModificationActivationByRootNetwork() {
        Set<UUID> modificationUuids = Set.of(UUID.randomUUID());
        studyController.updateNetworkModificationsActivation(studyUuid, rootNetworkUuid, nodeUuid, modificationUuids, true, userId);

        verify(rebuildNodeService, times(1)).updateNetworkModificationsActivation(studyUuid, nodeUuid, rootNetworkUuid, modificationUuids, userId, true);
        verify(studyService, times(1)).buildNode(eq(studyUuid), eq(nodeUuid), any(), eq(userId));
    }
}
