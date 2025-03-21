/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.NetworkService;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NetworkModificationUnitTest {
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private StudyController studyController;
    @MockBean
    private NetworkService networkService;
    @MockBean
    private RestTemplate restTemplate;

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);
    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    private static final String SHOULD_NOT_RETURN_NULL_MESSAGE = "Should not return null here";

    private static final long TIMEOUT = 1000;
    private static final String VARIANT_1 = "variant_1";
    private static final String VARIANT_2 = "variant_2";
    private static final String VARIANT_3 = "variant_3";

    private static final UUID REPORT_UUID_1 = UUID.randomUUID();
    private static final UUID REPORT_UUID_2 = UUID.randomUUID();
    private static final UUID REPORT_UUID_3 = UUID.randomUUID();

    private UUID studyUuid;
    private UUID node1Uuid;
    private UUID node2Uuid;
    private UUID node3Uuid;

    //output destinations
    @Autowired
    private OutputDestination output;
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup() {
        rootNodeInfoRepository.deleteAll();
        networkModificationNodeInfoRepository.deleteAll();
        nodeRepository.deleteAll();
        rootNetworkRepository.deleteAll();
        studyRepository.deleteAll();

        StudyEntity study = insertStudy();

        RootNetworkEntity firstRootNetworkEntity = RootNetworkEntity.builder()
            .id(UUID.randomUUID())
            .name("rootNetworkName")
            .tag("rn1")
            .networkUuid(NETWORK_UUID)
            .networkId("netId")
            .caseUuid(CASE_LOADFLOW_UUID)
            .caseFormat("caseFormat")
            .caseName("caseName")
            .build();

        study.addRootNetwork(firstRootNetworkEntity);
        studyRepository.save(study);
        studyUuid = study.getId();
        NodeEntity rootNode = insertRootNode(study, UUID.randomUUID());
        NodeEntity node1 = insertNode(study, node1Uuid, VARIANT_1, REPORT_UUID_1, rootNode, firstRootNetworkEntity, BuildStatus.BUILT);
        NodeEntity node2 = insertNode(study, node2Uuid, VARIANT_2, REPORT_UUID_2, node1, firstRootNetworkEntity, BuildStatus.BUILT);
        NodeEntity node3 = insertNode(study, node3Uuid, VARIANT_3, REPORT_UUID_3, node1, firstRootNetworkEntity, BuildStatus.NOT_BUILT);
        rootNetworkRepository.save(firstRootNetworkEntity);

        node1Uuid = node1.getIdNode();
        node2Uuid = node2.getIdNode();
        node3Uuid = node3.getIdNode();
    }

    @Test
    void unbuildNode() {
        /*       rootNode
         *          |
         *       node1(B)
         *     |         |
         *  node2(B)   node3
         */
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node1Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node2Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity3 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node3Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        assertEquals(BuildStatus.BUILT, rootNetworkNodeInfoEntity1.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT, rootNetworkNodeInfoEntity2.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, rootNetworkNodeInfoEntity3.getNodeBuildStatus().getLocalBuildStatus());
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        studyController.unbuildNode(studyUuid, firstRootNetworkUuid, node1Uuid);

        /*       rootNode
         *          |
         *        node1
         *     |         |
         *  node2(B)   node3
         */
        rootNetworkNodeInfoEntity1 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node1Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity2 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node2Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        rootNetworkNodeInfoEntity3 = rootNetworkNodeInfoRepository.findAllByNodeInfoId(node3Uuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        assertEquals(BuildStatus.NOT_BUILT, rootNetworkNodeInfoEntity1.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT, rootNetworkNodeInfoEntity2.getNodeBuildStatus().getLocalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, rootNetworkNodeInfoEntity3.getNodeBuildStatus().getLocalBuildStatus());

        checkUpdateBuildStateMessageReceived(studyUuid, List.of(node1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyUuid, node1Uuid);

        Mockito.verify(networkService).deleteVariants(NETWORK_UUID, List.of(VARIANT_1));
    }

    @Test
    void activateNetworkModificationTest() {
        List<UUID> modificationToDeactivateUuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        updateNetworkModificationActivationStatus(modificationToDeactivateUuids, node1Uuid, List.of(node2Uuid, node3Uuid), List.of(node1Uuid, node2Uuid), false);
    }

    @Test
    void deactivateNetworkModificationTest() {
        List<UUID> modificationToDeactivateUuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        updateNetworkModificationActivationStatus(modificationToDeactivateUuids, node1Uuid, List.of(node2Uuid, node3Uuid), List.of(node1Uuid, node2Uuid), true);
    }

    private void updateNetworkModificationActivationStatus(List<UUID> networkModificationUuids, UUID nodeWithModification, List<UUID> childrenNodes, List<UUID> nodesToUnbuild, boolean activated) {
        studyController.updateNetworkModificationsActivation(studyUuid, node1Uuid, networkModificationUuids, activated, "userId");

        checkModificationUpdatedMessageReceived(studyUuid, nodeWithModification, childrenNodes, NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS);
        checkUpdateBuildStateMessageReceived(studyUuid, nodesToUnbuild);
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeWithModification);
        checkModificationUpdatedMessageReceived(studyUuid, nodeWithModification, childrenNodes, NotificationService.MODIFICATIONS_UPDATING_FINISHED);

        NetworkModificationNodeInfoEntity node1Infos = networkModificationNodeInfoRepository.findById(node1Uuid).orElseThrow(() -> new UnsupportedOperationException(SHOULD_NOT_RETURN_NULL_MESSAGE));
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(
            matches(".*network-modifications\\?" + networkModificationUuids.stream().map(uuid -> "uuids=" + uuid.toString() + "&").collect(Collectors.joining()) +
                "groupUuid=" + node1Infos.getModificationGroupUuid().toString() + "&" +
                "activated=" + activated), eq(HttpMethod.PUT), any(HttpEntity.class), eq(Void.class));
    }

    private void checkModificationUpdatedMessageReceived(UUID studyUuid, UUID nodeUuid, List<UUID> childrenNodeUuids, String notificationType) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals("", new String(messageStatus.getPayload()));

        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(childrenNodeUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(notificationType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateBuildStateMessageReceived(UUID studyUuid, List<UUID> nodeUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals("", new String(messageStatus.getPayload()));

        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    private StudyEntity insertStudy() {
        return StudyEntity.builder()
            .id(UUID.randomUUID())
            .indexationStatus(StudyIndexationStatus.INDEXED)
            .voltageInitParameters(new StudyVoltageInitParametersEntity())
            .build();
    }

    private NodeEntity insertNode(StudyEntity study, UUID nodeId, String variantId, UUID reportUuid, NodeEntity parentNode, RootNetworkEntity rootNetworkEntity, BuildStatus buildStatus) {
        NodeEntity nodeEntity = nodeRepository.save(new NodeEntity(nodeId, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        NetworkModificationNodeInfoEntity modificationNodeInfoEntity = networkModificationNodeInfoRepository.save(NetworkModificationNodeInfoEntity.builder().idNode(nodeEntity.getIdNode()).modificationGroupUuid(UUID.randomUUID()).build());
        createNodeLinks(rootNetworkEntity, modificationNodeInfoEntity, variantId, reportUuid, buildStatus);
        return nodeEntity;
    }

    // We can't use the method RootNetworkNodeInfoService::createNodeLinks because there is no transaction in a session
    private void createNodeLinks(RootNetworkEntity rootNetworkEntity, NetworkModificationNodeInfoEntity modificationNodeInfoEntity,
                                 String variantId, UUID reportUuid, BuildStatus buildStatus) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = RootNetworkNodeInfoEntity.builder().variantId(variantId).modificationReports(Map.of(modificationNodeInfoEntity.getId(), reportUuid)).nodeBuildStatus(NodeBuildStatus.from(buildStatus).toEntity()).build();
        modificationNodeInfoEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }

    private NodeEntity insertRootNode(StudyEntity study, UUID nodeId) {
        NodeEntity node = nodeRepository.save(new NodeEntity(nodeId, null, NodeType.ROOT, study, false, null));
        RootNodeInfoEntity rootNodeInfo = new RootNodeInfoEntity();
        rootNodeInfo.setIdNode(node.getIdNode());
        rootNodeInfoRepository.save(rootNodeInfo);
        return node;
    }

    @AfterEach
    void tearDown() {
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION);
        try {
            destinations.forEach(destination -> assertNull(output.receive(100, destination), "Should not be any messages in queue " + destination + " : "));
        } catch (NullPointerException e) {
            // Ignoring
        } finally {
            output.clear(); // purge in order to not fail the other tests
        }
    }

}
