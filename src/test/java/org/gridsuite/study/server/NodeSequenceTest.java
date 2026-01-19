/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.dto.sequence.SecuritySequence;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
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
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * @author Kevin Le Saulnier <kevin.le-saulnier at rte-france.com
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NodeSequenceTest {
    UUID studyUuid;
    UUID networkUuid = UUID.randomUUID();
    UUID caseUuid = UUID.randomUUID();
    String caseName = "caseName";
    String caseFormat = "caseFormat";
    UUID reportUuid = UUID.randomUUID();
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
    @Autowired
    private StudyService studyService;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private UserAdminService userAdminService;
    @MockitoBean
    private NetworkModificationService networkModificationService;

    @BeforeEach
    void setUp() {
        StudyEntity study = TestUtils.createDummyStudy(networkUuid, caseUuid, caseName, caseFormat, reportUuid);
        studyEntity = studyRepository.save(study);
        studyUuid = studyEntity.getId();

        doReturn(Optional.of(10)).when(userAdminService).getUserMaxAllowedBuilds(userId);
    }

    @Test
    void testCreateSecuritySequence() {
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode constructionNode = createNode(rootNode.getIdNode(), "N1", NetworkModificationNodeType.CONSTRUCTION);

        studyService.createSequence(studyUuid, constructionNode.getId(), NodeSequenceType.SECURITY_SEQUENCE, userId);

        AbstractNode parentOfSubtree = networkModificationTreeService.getStudySubtree(studyUuid, constructionNode.getId(), null);
        AbstractNode nNode = parentOfSubtree.getChildren().getFirst();
        checkSecuritySequence(nNode, "");

       // verify notifications
        verify(notificationService, times(1)).emitSubtreeInserted(studyUuid, nNode.getId(), parentOfSubtree.getId());
        verify(notificationService, times(1)).emitElementUpdated(studyUuid, userId);

    }

    @Test
    void testCreateSecuritySequenceOnRootNode() {
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);

        studyService.createSequence(studyUuid, rootNode.getIdNode(), NodeSequenceType.SECURITY_SEQUENCE, userId);

        AbstractNode parentOfSubtree = networkModificationTreeService.getStudySubtree(studyUuid, rootNode.getIdNode(), null);
        AbstractNode nNode = parentOfSubtree.getChildren().getFirst();
        checkSecuritySequence(nNode, "");

        // verify notifications
        verify(notificationService, times(1)).emitSubtreeInserted(studyUuid, nNode.getId(), parentOfSubtree.getId());
        verify(notificationService, times(1)).emitElementUpdated(studyUuid, userId);
    }

    @Test
    void testCreateTwoSecuritySequence() {
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode constructionNode = createNode(rootNode.getIdNode(), "N1", NetworkModificationNodeType.CONSTRUCTION);
        NetworkModificationNode constructionNode2 = createNode(rootNode.getIdNode(), "N2", NetworkModificationNodeType.CONSTRUCTION);

        studyService.createSequence(studyUuid, constructionNode.getId(), NodeSequenceType.SECURITY_SEQUENCE, userId);
        studyService.createSequence(studyUuid, constructionNode2.getId(), NodeSequenceType.SECURITY_SEQUENCE, userId);

        AbstractNode parentOfSubtree = networkModificationTreeService.getStudySubtree(studyUuid, constructionNode.getId(), null);
        AbstractNode nNode = parentOfSubtree.getChildren().getFirst();
        checkSecuritySequence(nNode, "");

        AbstractNode parentOfSubtree2 = networkModificationTreeService.getStudySubtree(studyUuid, constructionNode2.getId(), null);
        AbstractNode nNode2 = parentOfSubtree2.getChildren().getFirst();
        checkSecuritySequence(nNode2, " (1)");

        //verify notifications
        verify(notificationService, times(1)).emitSubtreeInserted(studyUuid, nNode.getId(), constructionNode.getId());
        verify(notificationService, times(1)).emitSubtreeInserted(studyUuid, nNode2.getId(), constructionNode2.getId());
        verify(notificationService, times(2)).emitElementUpdated(studyUuid, userId);
    }

    @Test
    void testCreateSecuritySequenceOnSecurityNode() {
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode constructionNode = createNode(rootNode.getIdNode(), "N1", NetworkModificationNodeType.SECURITY);
        UUID constructionNodeId = constructionNode.getId();

        assertThrows(StudyException.class, () -> studyService.createSequence(studyUuid, constructionNodeId, NodeSequenceType.SECURITY_SEQUENCE, userId));
    }

    NetworkModificationNode createNode(UUID parentNodeUuid, String name, NetworkModificationNodeType networkModificationNodeType) {
        return networkModificationTreeService.createNode(studyEntity, parentNodeUuid, NetworkModificationNode.builder().name(name).nodeType(networkModificationNodeType).build(), InsertMode.CHILD, userId);
    }

    void checkSecuritySequence(AbstractNode nNode, String nameSuffix) {
        NetworkModificationNodeInfoEntity nNodeEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nNode.getId());
        assertEquals(SecuritySequence.N_NODE_NAME + nameSuffix, nNodeEntity.getName());
        assertEquals(NetworkModificationNodeType.SECURITY, nNodeEntity.getNodeType());

        AbstractNode nmKNode = nNode.getChildren().getFirst();
        NetworkModificationNodeInfoEntity nmKNodeEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(nmKNode.getId());
        assertEquals(SecuritySequence.NMK_NODE_NAME + nameSuffix, nmKNodeEntity.getName());
        assertEquals(NetworkModificationNodeType.SECURITY, nmKNodeEntity.getNodeType());

        AbstractNode curNode = nmKNode.getChildren().getFirst();
        NetworkModificationNodeInfoEntity curNodeEntity = networkModificationTreeService.getNetworkModificationNodeInfoEntity(curNode.getId());
        assertEquals(SecuritySequence.CURATIF_NODE_NAME + nameSuffix, curNodeEntity.getName());
        assertEquals(NetworkModificationNodeType.SECURITY, curNodeEntity.getNodeType());
    }

    @AfterEach
    void cleanUp() {
        rootNodeInfoRepository.deleteAll();
        networkModificationNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        studyRepository.deleteAll();
    }
}
