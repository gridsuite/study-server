/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.BUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.COMPUTE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_TREE;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.REIMPORT_CASE;

/**
 * What NodeActivityRulesTest cannot cover: the tree walk the rules are fed with, and what the
 * service does to the database around them.
 *
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NodeActivityServiceTest {

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final long TIMEOUT = 1000;

    @Autowired
    private NodeActivityService nodeActivityService;
    @Autowired
    private NodeActivityRepository nodeActivityRepository;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private StudyService studyService;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private OutputDestination output;

    private UUID studyUuid;
    private UUID rootNetworkUuid;
    private UUID rootNodeUuid;
    private UUID nodeUuid;
    private UUID childUuid;
    private UUID grandChildUuid;

    /**
     * root - node - child - grandChild
     */
    @BeforeEach
    void setUp() {
        nodeActivityRepository.deleteAll();
        rootNetworkNodeInfoRepository.deleteAll();
        rootNodeInfoRepository.deleteAll();
        networkModificationNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        rootNetworkRepository.deleteAll();
        studyRepository.deleteAll();

        StudyEntity study = StudyEntity.builder()
            .id(UUID.randomUUID())
            .voltageInitParameters(new StudyVoltageInitParametersEntity())
            .build();
        RootNetworkEntity rootNetwork = RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName")
            .tag("rn1")
            .networkUuid(UUID.randomUUID())
            .networkId("networkId")
            .caseUuid(UUID.randomUUID())
            .caseFormat("UCTE")
            .caseName("caseName")
            .build();
        study.addRootNetwork(rootNetwork);
        studyRepository.save(study);
        studyUuid = study.getId();
        rootNetworkUuid = rootNetwork.getId();

        NodeEntity root = insertRootNode(study);
        NodeEntity node = insertNode(study, root, rootNetwork);
        NodeEntity child = insertNode(study, node, rootNetwork);
        NodeEntity grandChild = insertNode(study, child, rootNetwork);
        rootNetworkRepository.save(rootNetwork);

        rootNodeUuid = root.getIdNode();
        nodeUuid = node.getIdNode();
        childUuid = child.getIdNode();
        grandChildUuid = grandChild.getIdNode();

        output.clear(STUDY_UPDATE_DESTINATION);
    }

    @Test
    void everyNodeAboveThisOneIsAnAncestorIncludingTheRoot() {
        assertThat(nodeRepository.findAllAncestorsUuids(grandChildUuid))
            .containsExactlyInAnyOrder(childUuid, nodeUuid, rootNodeUuid);
        assertThat(nodeRepository.findAllAncestorsUuids(nodeUuid)).containsExactly(rootNodeUuid);
        assertThat(nodeRepository.findAllAncestorsUuids(rootNodeUuid)).isEmpty();
    }

    @Test
    void anActivityOnTheRootNodeReachesEveryNodeBelowIt() {
        nodeActivityService.setNodeActivity(REIMPORT_CASE, studyUuid, rootNetworkUuid, List.of(rootNodeUuid));

        assertThatThrownBy(() -> nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(grandChildUuid)))
            .isInstanceOf(StudyException.class)
            .hasMessageContaining("REIMPORT_CASE is running on node " + rootNodeUuid);
    }

    @Test
    void anActivityOnANodeLeavesItsAncestorsFree() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(grandChildUuid));

        assertThatCode(() -> nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid)))
            .doesNotThrowAnyException();
    }

    @Test
    void aRequestNamingANodeOfAnotherStudyIsNotFound() {
        assertThatThrownBy(() -> nodeActivityService.setNodeActivity(BUILD, UUID.randomUUID(), rootNetworkUuid, List.of(nodeUuid)))
            .isInstanceOf(StudyException.class)
            .hasMessageContaining("not all found in study");
    }

    @Test
    void theSameNodeTwiceInOneRequestIsOneActivity() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid, nodeUuid));

        assertThat(nodeActivityRepository.findAllByStudyId(studyUuid)).hasSize(1);
    }

    @Test
    void theProjectionReportsWhatIsRunning() {
        nodeActivityService.setNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(childUuid));
        nodeActivityService.setNodeActivity(EDIT_TREE, studyUuid, null, List.of(grandChildUuid));

        assertThat(nodeActivityService.getNodeActivities(studyUuid))
            .containsExactlyInAnyOrder(
                new NodeActivityInfos(childUuid, rootNetworkUuid, NodeActivityLabel.COMPUTING, false),
                new NodeActivityInfos(grandChildUuid, null, NodeActivityLabel.UPDATING, true));
    }

    @Test
    void aStudyWithNothingRunningReportsNoActivity() {
        assertThat(nodeActivityService.getNodeActivities(studyUuid)).isEmpty();
    }

    @Test
    void theCleanupLeavesActivitiesYoungerThanTheCutoff() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid));

        assertThat(nodeActivityService.removeAbandonedNodeActivities(Instant.now().minus(1, ChronoUnit.HOURS))).isEmpty();
        assertThat(nodeActivityRepository.findAllByStudyId(studyUuid)).hasSize(1);
    }

    @Test
    void theCleanupReleasesWhatStartedBeforeTheCutoff() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid));
        nodeActivityService.setNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(childUuid));

        List<NodeActivityEntity> released =
            nodeActivityService.removeAbandonedNodeActivities(Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(released).extracting(NodeActivityEntity::getNodeId).containsExactlyInAnyOrder(nodeUuid, childUuid);
        assertThat(nodeActivityRepository.findAllByStudyId(studyUuid)).isEmpty();
    }

    @Test
    void aReleasedNodeIsPutBackIntoAStateWeCanTrust() {
        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, rootNetworkUuid, NodeBuildStatus.from(BuildStatus.BUILT));
        assertThat(networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt()).isTrue();

        studyService.invalidateAbandonedNode(studyUuid, nodeUuid, rootNetworkUuid);

        assertThat(networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt()).isFalse();
    }

    /** Notifications tests */
    @Test
    void takingAndReleasingANodeIsNotified() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid));
        assertNodeActivitiesNotified();

        nodeActivityService.removeNodeActivity(studyUuid, rootNetworkUuid, List.of(nodeUuid));
        assertNodeActivitiesNotified();
    }

    @Test
    void theCleanupNotifiesEachStudyItReleasedANodeIn() {
        nodeActivityService.setNodeActivity(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid));
        nodeActivityService.setNodeActivity(COMPUTE, studyUuid, rootNetworkUuid, List.of(childUuid));
        output.clear(STUDY_UPDATE_DESTINATION);

        nodeActivityService.removeAbandonedNodeActivities(Instant.now().plus(1, ChronoUnit.HOURS));

        // two rows of the same study, so the study is notified once
        assertNodeActivitiesNotified();
        assertThat(output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION)).isNull();
    }

    private void assertNodeActivitiesNotified() {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(message).isNotNull();
        assertThat(message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE))
            .isEqualTo(NotificationService.UPDATE_NODE_ACTIVITIES);
        assertThat(message.getHeaders().get(NotificationService.HEADER_STUDY_UUID)).isEqualTo(studyUuid);
    }

    private NodeEntity insertRootNode(StudyEntity study) {
        NodeEntity node = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, study, false, null, new ArrayList<>()));
        RootNodeInfoEntity rootNodeInfo = new RootNodeInfoEntity();
        rootNodeInfo.setIdNode(node.getIdNode());
        rootNodeInfoRepository.save(rootNodeInfo);
        return node;
    }

    private NodeEntity insertNode(StudyEntity study, NodeEntity parent, RootNetworkEntity rootNetwork) {
        NodeEntity node = nodeRepository.save(
            new NodeEntity(null, parent, NodeType.NETWORK_MODIFICATION, study, false, null, new ArrayList<>()));
        NetworkModificationNodeInfoEntity nodeInfo = networkModificationNodeInfoRepository.save(
            NetworkModificationNodeInfoEntity.builder()
                .idNode(node.getIdNode())
                .modificationGroupUuid(UUID.randomUUID())
                .nodeType(NetworkModificationNodeType.CONSTRUCTION)
                .build());
        RootNetworkNodeInfoEntity link = RootNetworkNodeInfoEntity.builder()
            .variantId("variant")
            .modificationReports(Map.of(nodeInfo.getId(), UUID.randomUUID()))
            .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT).toEntity())
            .build();
        nodeInfo.addRootNetworkNodeInfo(link);
        rootNetwork.addRootNetworkNodeInfo(link);
        rootNetworkNodeInfoRepository.save(link);
        return node;
    }
}
