/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.NonEvacuatedEnergyService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */

@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NetworkModificationUnitTest {
    @Autowired
    NodeRepository nodeRepository;
    @Autowired
    NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    StudyRepository studyRepository;
    @Autowired
    StudyController studyController;
    @MockBean
    ReportService reportService;
    @MockBean
    NetworkStoreService networkStoreService;
    @MockBean
    NetworkService networkService;

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);
    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    private static final String SHOULD_NOT_RETUTN_NULL_MESSAGE = "Should not return null here";

    private static final long TIMEOUT = 1000;
    private static final String VARIANT_1 = "variant_1";

    private UUID studyUuid;
    private UUID node1Uuid;
    private UUID node2Uuid;
    private UUID node3Uuid;

    //output destinations
    @Autowired
    private OutputDestination output;
    private final String studyUpdateDestination = "study.update";

    @BeforeEach
    public void setup() {
        StudyEntity study = insertStudy(NETWORK_UUID, CASE_LOADFLOW_UUID);
        studyUuid = study.getId();

        NodeEntity rootNode = nodeRepository.save(new NodeEntity(null, null, NodeType.ROOT, study, false, null));

        NodeEntity node1 = insertNode(study, node1Uuid, rootNode, BuildStatus.BUILT);
        NodeEntity node2 = insertNode(study, node2Uuid, node1, BuildStatus.BUILT);
        NodeEntity node3 = insertNode(study, node3Uuid, node1, BuildStatus.NOT_BUILT);

        node1Uuid = node1.getIdNode();
        node2Uuid = node2.getIdNode();
        node3Uuid = node3.getIdNode();
    }

    @Test
    void unbuildNode() {
        /**
         *       rootNode
         *          |
         *       node1(B)
         *     |         |
         *  node2(B)   node3
         */
        List<NetworkModificationNodeInfoEntity> nodesInfos = networkModificationNodeInfoRepository.findAll();
        NetworkModificationNodeInfoEntity node1Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node1Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));
        NetworkModificationNodeInfoEntity node2Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node2Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));
        NetworkModificationNodeInfoEntity node3Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node3Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));

        assertEquals(BuildStatus.BUILT, node1Infos.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT, node2Infos.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, node3Infos.getNodeBuildStatus().getLocalBuildStatus());

        studyController.unbuildNode(studyUuid, node1Uuid);

        /**
         *       rootNode
         *          |
         *        node1
         *     |         |
         *  node2(B)   node3
         */
        nodesInfos = networkModificationNodeInfoRepository.findAll();
        node1Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node1Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));
        node2Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node2Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));
        node3Infos = nodesInfos.stream().filter(n -> n.getIdNode().equals(node3Uuid)).findAny().orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETUTN_NULL_MESSAGE));

        assertEquals(BuildStatus.NOT_BUILT, node1Infos.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT, node2Infos.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, node3Infos.getNodeBuildStatus().getLocalBuildStatus());

        checkUpdateBuildStateMessageReceived(studyUuid, node1Uuid);
        checkUpdateModelsStatusMessagesReceived(studyUuid, node1Uuid);

        Mockito.verify(reportService).deleteReport(null);
        Mockito.verify(networkService).deleteVariants(null, List.of(VARIANT_1));
    }

    private void checkUpdateBuildStateMessageReceived(UUID studyUuid, UUID nodeUuid) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));

        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(List.of(nodeUuid), headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    private StudyEntity insertStudy(UUID networkUuid, UUID caseUuid) {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "",
            UUID.randomUUID(), null, null, null, defaultNonEvacuatedEnergyParametersEntity);
        return studyRepository.save(studyEntity);
    }

    private NodeEntity insertNode(StudyEntity study, UUID nodeId, NodeEntity parentNode, BuildStatus buildStatus) {
        NodeEntity node = nodeRepository.save(new NodeEntity(nodeId, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        NetworkModificationNodeInfoEntity nodeInfos = new NetworkModificationNodeInfoEntity(UUID.randomUUID(), VARIANT_1, new HashSet<>(), null, null, null, null, null, null, null, null, NodeBuildStatus.from(buildStatus).toEntity());
        nodeInfos.setIdNode(node.getIdNode());
        networkModificationNodeInfoRepository.save(nodeInfos);

        return node;
    }

    @AfterEach
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);
        assertQueuesEmptyThenClear(destinations);
    }

    private void assertQueuesEmptyThenClear(List<String> destinations) {
        try {
            destinations.forEach(destination -> assertNull("Should not be any messages in queue " + destination + " : ", output.receive(100, destination)));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }
}
