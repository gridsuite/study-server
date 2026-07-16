/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceTest {
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private StudyService studyService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoBean
    private NetworkModificationService networkModificationService;

    @Test
    void testBuildFirstLevelChildren() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota not reached, all first level children of N1 will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(0L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);
        mockNodeBuild(node3.getIdNode(), rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verifyNodeBuild(node3.getIdNode(), rootNetworkUuid);
        // check n4 has actually not been built
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(3)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(3)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaAlreadyReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota already reached, nothing will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(10L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verify(networkModificationService, times(0)).buildNode(eq(node2.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        verify(userAdminService, times(1)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(1)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    @Test
    void testBuildFirstLevelChildrenWithQuotaReached() {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        String userId = "userId";

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, null, false, null, List.of()));
        NodeEntity node1 = nodeRepository.save(new NodeEntity(null, rootNode, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node2 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node3 = nodeRepository.save(new NodeEntity(null, node1, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        NodeEntity node4 = nodeRepository.save(new NodeEntity(null, node3, NodeType.NETWORK_MODIFICATION, null, false, null, List.of()));
        /*
                    root
                     |
                     N1
                   ------
                   |    |
                   N2   N3
                        |
                        N4
         */

        // quota will be reached, only one child will be built
        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
        doReturn(9L).when(networkModificationTreeService).countBuiltNodes(studyUuid, rootNetworkUuid);

        mockNodeBuild(node2.getIdNode(), rootNetworkUuid);

        studyService.buildFirstLevelChildren(studyUuid, node1.getIdNode(), rootNetworkUuid, userId);

        verifyNodeBuild(node2.getIdNode(), rootNetworkUuid);
        verify(networkModificationService, times(0)).buildNode(eq(node3.getIdNode()), eq(rootNetworkUuid), any(), eq(null));
        verify(networkModificationService, times(0)).buildNode(eq(node4.getIdNode()), eq(rootNetworkUuid), any(), eq(null));

        // 1 to check how many children will be built, then 1 for each built children
        verify(userAdminService, times(2)).getUserMaxAllowedBuilds(userId);
        verify(networkModificationTreeService, times(2)).countBuiltNodes(studyUuid, rootNetworkUuid);
    }

    private void mockNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        doReturn(new BuildInfos()).when(networkModificationTreeService).getBuildInfos(nodeUuid, rootNetworkUuid);
        doNothing().when(networkModificationTreeService).setModificationReports(eq(nodeUuid), eq(rootNetworkUuid), any());
        doNothing().when(networkModificationTreeService).updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        doReturn(NodeBuildStatus.from(BuildStatus.NOT_BUILT)).when(networkModificationTreeService).getNodeBuildStatus(nodeUuid, rootNetworkUuid);
    }

    private void verifyNodeBuild(UUID nodeUuid, UUID rootNetworkUuid) {
        verify(networkModificationTreeService, times(1)).updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILDING));
        verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), any(), eq(null));
    }
}
