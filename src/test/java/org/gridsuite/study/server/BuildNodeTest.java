/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
class BuildNodeTest {

    String userId = "userId";
    UUID studyUuid = UUID.randomUUID();
    private StudyEntity studyEntity;

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private TestUtils studyTestUtils;
    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @MockitoSpyBean
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoSpyBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @MockitoBean
    NetworkModificationService networkModificationService;
    @MockitoBean
    UserAdminService userAdminService;
    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        StudyEntity study = TestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "caseFormat", UUID.randomUUID());
        studyEntity = studyRepository.save(study);
        studyUuid = studyEntity.getId();
    }

    @AfterEach
    void cleanUp() {
        rootNodeInfoRepository.deleteAll();
        networkModificationNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
    }

    @Test
    void testBuildInfos() {
        Map<String, NetworkModificationNode> allNodes = createNodeTree();

        testBuildInfos(allNodes.get("N5"), null, allNodes.values().stream().toList());
        testBuildInfos(allNodes.get("N4"), null, List.of(allNodes.get("N1"), allNodes.get("N2"), allNodes.get("N3"), allNodes.get("N4")));
        testBuildInfos(allNodes.get("N3"), null, List.of(allNodes.get("N1"), allNodes.get("N2"), allNodes.get("N3")));
        testBuildInfos(allNodes.get("N2"), null, List.of(allNodes.get("N1"), allNodes.get("N2")));
        testBuildInfos(allNodes.get("N1"), null, List.of(allNodes.get("N1")));

        // Mark the node 3 status as built
        networkModificationTreeService.updateNodeBuildStatus(allNodes.get("N3").getId(), studyEntity.getFirstRootNetwork().getId(), NodeBuildStatus.from(BuildStatus.BUILT));

        testBuildInfos(allNodes.get("N4"), allNodes.get("N3").getVariantId(), List.of(allNodes.get("N4")));
    }

    private void testBuildInfos(NetworkModificationNode node, String originVariant, List<NetworkModificationNode> nodesToBuild) {
        reset(networkModificationService);

        networkModificationTreeService.buildNode(studyUuid, node.getId(), studyEntity.getFirstRootNetwork().getId(), userId, null);

        ArgumentCaptor<BuildInfos> infosCaptor = ArgumentCaptor.forClass(BuildInfos.class);
        verify(networkModificationService, times(1))
            .buildNode(any(UUID.class), any(UUID.class), infosCaptor.capture(), isNull());

        BuildInfos buildInfos = infosCaptor.getValue();
        assertNotNull(buildInfos);
        assertEquals(originVariant, buildInfos.getOriginVariantId());  // null if previous built node is root node
        assertEquals(getVariantId(node.getName()), buildInfos.getDestinationVariantId());
        assertEquals(nodesToBuild.stream().map(NetworkModificationNode::getModificationGroupUuid).toList(), buildInfos.getModificationGroupUuids());
    }

    private Map<String, NetworkModificationNode> createNodeTree() {
        /*
            root
            |
            N1
            |
            N2
            |
            N3
            |
            N4
            |
            N5
        */
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode node1 = createNode(rootNode.getIdNode(), "N1");
        NetworkModificationNode node2 = createNode(node1.getId(), "N2");
        NetworkModificationNode node3 = createNode(node2.getId(), "N3");
        NetworkModificationNode node4 = createNode(node3.getId(), "N4");
        NetworkModificationNode node5 = createNode(node4.getId(), "N5");

        return Stream.of(node1, node2, node3, node4, node5)
            .collect(Collectors.toMap(NetworkModificationNode::getName, Function.identity()));
    }

    private NetworkModificationNode createNode(UUID parentNodeUuid, String nodeName) {
        NetworkModificationNode modificationNode = networkModificationTreeService.createNode(studyEntity,
            parentNodeUuid,
            NetworkModificationNode.builder().name(nodeName).nodeType(NetworkModificationNodeType.CONSTRUCTION).build(),
            InsertMode.CHILD, userId);

        String variantId = getVariantId(nodeName);
        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());
        modificationNode.setVariantId(variantId);

        return modificationNode;
    }

    private String getVariantId(String nodeName) {
        return "variant" + nodeName;
    }
}
