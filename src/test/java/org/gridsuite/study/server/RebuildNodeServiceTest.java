/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.modification.ModificationMoveInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.gridsuite.study.server.utils.TestUtils;
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

import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
class RebuildNodeServiceTest {
    @Autowired
    private StudyController studyController;

    @MockitoBean
    private NetworkModificationService networkModificationService;

    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private StudyService studyService;

    @MockitoBean
    private NetworkModificationRestService networkModificationRestService;

    @MockitoSpyBean
    private NodeActivityRunnerService nodeActivityService;

    UUID studyUuid = UUID.randomUUID();
    UUID node1Uuid = UUID.randomUUID();
    UUID node2Uuid = UUID.randomUUID();
    String userId = "userId";

    UUID rootNetworkUuid = UUID.randomUUID();
    UUID rootNetwork2Uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        doReturn(false).when(networkModificationTreeService).isRootOrConstructionNode(any());
        doReturn(Map.of()).when(networkModificationRestService).findParentComposites(any());
        TestUtils.bypassNodeActivities(nodeActivityService);
    }

    @Test
    void testRebuildSingleNode() {
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node1Uuid, rootNetworkUuid);

        doReturn(
            Map.of(node1Uuid, Set.of(rootNetworkUuid))
        ).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, node1Uuid, node1Uuid);

        UUID modificationUuid = UUID.randomUUID();
        ModificationMoveInfos modificationMoveInfos = new ModificationMoveInfos(modificationUuid, null, null, null);
        studyController.moveModifications(studyUuid, node1Uuid, node1Uuid, List.of(modificationMoveInfos), userId);

        verify(networkModificationService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleNodes() {
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node1Uuid, rootNetworkUuid);
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node2Uuid, rootNetworkUuid);

        doReturn(
            Map.of(node1Uuid, Set.of(rootNetworkUuid), node2Uuid, Set.of(rootNetworkUuid))
        ).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, node1Uuid, node2Uuid);

        studyController.moveModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(networkModificationService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(networkModificationService, times(1)).buildNode(studyUuid, node2Uuid, rootNetworkUuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworks() {
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node1Uuid, rootNetworkUuid);
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node1Uuid, rootNetwork2Uuid);

        doReturn(
            Map.of(node1Uuid, Set.of(rootNetworkUuid, rootNetwork2Uuid))
        ).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, node1Uuid, node2Uuid);

        studyController.moveModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(networkModificationService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(networkModificationService, times(1)).buildNode(studyUuid, node1Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildMultipleRootNetworksAndNodes() {
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node1Uuid, rootNetworkUuid);
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationService).getNodeBuildStatus(node2Uuid, rootNetwork2Uuid);

        doReturn(
            Map.of(node1Uuid, Set.of(rootNetworkUuid), node2Uuid, Set.of(rootNetwork2Uuid))
        ).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, node1Uuid, node2Uuid);

        studyController.moveModifications(studyUuid, node1Uuid, node2Uuid, List.of(), userId);

        verify(networkModificationService, times(1)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
        verify(networkModificationService, times(1)).buildNode(studyUuid, node2Uuid, rootNetwork2Uuid, userId);
    }

    @Test
    void testRebuildConstructionNode() {
        doReturn(Map.of()).when(networkModificationService).getSecurityNodesToRebuild(studyUuid, node1Uuid, node1Uuid);

        UUID modificationUuid = UUID.randomUUID();
        ModificationMoveInfos modificationMoveInfos = new ModificationMoveInfos(modificationUuid, null, null, null);
        studyController.moveModifications(studyUuid, node1Uuid, modificationUuid, List.of(modificationMoveInfos), userId);

        verify(networkModificationService, times(0)).buildNode(studyUuid, node1Uuid, rootNetworkUuid, userId);
    }
}
