/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NodePositionTest {
    UUID studyUuid;
    String userId = "userId";

    StudyEntity studyEntity;

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setUp() {
        StudyEntity study = studyTestUtils.createDummyStudy(UUID.randomUUID(), UUID.randomUUID(), "caseName", "caseFormat", UUID.randomUUID());
        studyEntity = studyRepository.save(study);
        studyUuid = studyEntity.getId();
    }

    @Test
    void testDeleteNode() {
        List<AbstractNode> children = createNodeTree().getChildren();

        networkModificationTreeService.doStashNode(getNode("N2", children).getId(), false);

        children = networkModificationTreeService.getStudyTree(studyUuid, studyTestUtils.getOneRootNetworkUuid(studyUuid)).getChildren();
        //               root
        //       /      /   \       \
        //      n1     n21 n22      n3
        assertEquals(0, getNode("N1", children).getColumnPosition());
        assertEquals(1, getNode("N21", children).getColumnPosition());
        assertEquals(2, getNode("N22", children).getColumnPosition());
        assertEquals(3, getNode("N3", children).getColumnPosition());
    }

    private AbstractNode getNode(String name, List<AbstractNode> nodes) {
        return nodes.stream().filter(node -> node.getName().equals(name)).findFirst().orElse(null);
    }

    private RootNode createNodeTree() {
        //            root
        //       /      |       \
        //      n1      n2      n3
        //             /  \
        //            n21  n22
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode node1 = createNode(rootNode.getIdNode(), "N1");
        NetworkModificationNode node2 = createNode(rootNode.getIdNode(), "N2");
        NetworkModificationNode node3 = createNode(rootNode.getIdNode(), "N3");

        NetworkModificationNode node21 = createNode(node2.getId(), "N21");
        NetworkModificationNode node22 = createNode(node2.getId(), "N22");

        assertEquals(0, node1.getColumnPosition());
        assertEquals(1, node2.getColumnPosition());
        assertEquals(2, node3.getColumnPosition());
        assertEquals(0, node21.getColumnPosition());
        assertEquals(1, node22.getColumnPosition());

        return networkModificationTreeService.getStudyTree(studyUuid, studyTestUtils.getOneRootNetworkUuid(studyUuid));
    }

    private NetworkModificationNode createNode(UUID parentNodeUuid, String name) {
        return networkModificationTreeService.createNode(studyEntity,
            parentNodeUuid,
            NetworkModificationNode.builder().name(name).nodeType(NetworkModificationNodeType.CONSTRUCTION).build(),
            InsertMode.CHILD, userId);
    }

    @AfterEach
    void cleanUp() {
        rootNodeInfoRepository.deleteAll();
        networkModificationNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
    }
}
