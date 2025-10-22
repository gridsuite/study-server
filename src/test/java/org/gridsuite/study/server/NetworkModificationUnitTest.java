/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.controller.StudyController;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
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
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_WORKFLOW_INFOS;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_WORKFLOW_TYPE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

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
    @SpyBean
    private RootNetworkService rootNetworkService;
    @SpyBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);
    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");

    private static final String SHOULD_NOT_RETURN_NULL_MESSAGE = "Should not return null here";

    private static final long TIMEOUT = 1000;
    private static final String VARIANT_1 = "variant_1";
    private static final String VARIANT_2 = "variant_2";
    private static final String VARIANT_3 = "variant_3";
    private static final String VARIANT_4 = "variant_4";

    private static final UUID REPORT_UUID_1 = UUID.randomUUID();
    private static final UUID REPORT_UUID_2 = UUID.randomUUID();
    private static final UUID REPORT_UUID_3 = UUID.randomUUID();
    private static final UUID REPORT_UUID_4 = UUID.randomUUID();

    private UUID studyUuid;
    private UUID node1Uuid;
    private UUID node2Uuid;
    private UUID node3Uuid;
    private UUID node4Uuid;

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
    @Autowired
    private NetworkModificationService networkModificationService;
    @Autowired
    private ObjectMapper objectMapper;

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
            .indexationStatus(RootNetworkIndexationStatus.INDEXED)
            .build();

        study.addRootNetwork(firstRootNetworkEntity);
        studyRepository.save(study);
        studyUuid = study.getId();
        NodeEntity rootNode = insertRootNode(study, UUID.randomUUID());
        NodeEntity node1 = insertNode(study, node1Uuid, NetworkModificationNodeType.SECURITY, VARIANT_1, REPORT_UUID_1, rootNode, firstRootNetworkEntity, BuildStatus.BUILT);
        NodeEntity node2 = insertNode(study, node2Uuid, NetworkModificationNodeType.SECURITY, VARIANT_2, REPORT_UUID_2, node1, firstRootNetworkEntity, BuildStatus.BUILT);
        NodeEntity node3 = insertNode(study, node3Uuid, NetworkModificationNodeType.SECURITY, VARIANT_3, REPORT_UUID_3, node1, firstRootNetworkEntity, BuildStatus.NOT_BUILT);
        NodeEntity node4 = insertNode(study, node4Uuid, NetworkModificationNodeType.SECURITY, VARIANT_4, REPORT_UUID_4, node2, firstRootNetworkEntity, BuildStatus.BUILT);
        rootNetworkRepository.save(firstRootNetworkEntity);

        node1Uuid = node1.getIdNode();
        node2Uuid = node2.getIdNode();
        node3Uuid = node3.getIdNode();
        node4Uuid = node4.getIdNode();
    }

    @Test
    void unbuildNode() {
        /*       rootNode
         *          |
         *       node1(B)
         *     |         |
         *  node2(B)   node3
         *     |
         *  node4(B)
         */
        assertNodeBuildStatus(node1Uuid, BuildStatus.BUILT);
        assertNodeBuildStatus(node2Uuid, BuildStatus.BUILT);
        assertNodeBuildStatus(node3Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node4Uuid, BuildStatus.BUILT);

        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        // Unbuild security node without LF -> no children invalidation
        studyController.unbuildNode(studyUuid, firstRootNetworkUuid, node1Uuid);
        /*       rootNode
         *          |
         *        node1
         *     |         |
         *  node2(B)   node3
         *     |
         *  node4(B)
         */
        assertNodeBuildStatus(node1Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node2Uuid, BuildStatus.BUILT);
        assertNodeBuildStatus(node3Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node4Uuid, BuildStatus.BUILT);
        checkUpdateBuildStateMessageReceived(studyUuid, List.of(node1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyUuid, node1Uuid);
        Mockito.verify(networkService).deleteVariants(NETWORK_UUID, List.of(VARIANT_1));

        // Unbuild security node with LF -> children invalidation
        when(rootNetworkNodeInfoService.isLoadflowDone(node2Uuid, firstRootNetworkUuid)).thenReturn(true);
        studyController.unbuildNode(studyUuid, firstRootNetworkUuid, node2Uuid);
        /*       rootNode
         *          |
         *        node1
         *     |         |
         *  node2(B)   node3
         *     |
         *  node4(B)
         */
        assertNodeBuildStatus(node1Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node2Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node3Uuid, BuildStatus.NOT_BUILT);
        assertNodeBuildStatus(node4Uuid, BuildStatus.NOT_BUILT);
        checkUpdateBuildStateMessageReceived(studyUuid, List.of(node2Uuid, node4Uuid));
        checkUpdateModelsStatusMessagesReceived(studyUuid, node2Uuid);
        Mockito.verify(networkService).deleteVariants(NETWORK_UUID, List.of(VARIANT_4, VARIANT_2));
    }

    private void assertNodeBuildStatus(UUID nodeUuid, BuildStatus buildStatus) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findAllByNodeInfoId(nodeUuid).stream().findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        assertEquals(buildStatus, rootNetworkNodeInfoEntity.getNodeBuildStatus().getLocalBuildStatus());
    }

    @Test
    void activateNetworkModificationTest() {
        List<UUID> modificationToDeactivateUuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        updateNetworkModificationActivationStatus(modificationToDeactivateUuids, node1Uuid, List.of(node2Uuid, node4Uuid, node3Uuid), List.of(node1Uuid, node2Uuid, node4Uuid), false);
    }

    @Test
    void deactivateNetworkModificationTest() {
        List<UUID> modificationToDeactivateUuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        updateNetworkModificationActivationStatus(modificationToDeactivateUuids, node1Uuid, List.of(node2Uuid, node4Uuid, node3Uuid), List.of(node1Uuid, node2Uuid, node4Uuid), true);
    }

    @Test
    void buildNodeWithWorkflowInfos() throws JsonProcessingException {
        UUID nodeUuid = UUID.randomUUID();
        UUID rootNetworkUuid = UUID.randomUUID();
        UUID networkUuid = UUID.randomUUID();
        BuildInfos buildInfos = new BuildInfos();
        RerunLoadFlowInfos rerunLoadFlowInfos = RerunLoadFlowInfos.builder()
            .userId("userId")
            .withRatioTapChangers(true)
            .loadflowResultUuid(UUID.randomUUID())
            .build();

        Mockito.doReturn(networkUuid).when(rootNetworkService).getNetworkUuid(rootNetworkUuid);
        networkModificationService.buildNode(nodeUuid, rootNetworkUuid, buildInfos, rerunLoadFlowInfos);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(restTemplate, Mockito.times(1)).exchange(urlCaptor.capture(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));

        assertTrue(urlCaptor.getValue().contains(QUERY_PARAM_WORKFLOW_TYPE + "=" + rerunLoadFlowInfos.getType().name()));
        assertTrue(urlCaptor.getValue().contains(QUERY_PARAM_WORKFLOW_INFOS + "=" + URLEncoder.encode(objectMapper.writeValueAsString(rerunLoadFlowInfos), StandardCharsets.UTF_8)));
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
        assertEquals(new TreeSet<>(nodeUuids), new TreeSet<>((List) headersStatus.get(NotificationService.HEADER_NODES)));
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
    }

    private StudyEntity insertStudy() {
        return StudyEntity.builder()
            .id(UUID.randomUUID())
            .voltageInitParameters(new StudyVoltageInitParametersEntity())
            .build();
    }

    private NodeEntity insertNode(StudyEntity study, UUID nodeId, NetworkModificationNodeType nodeType, String variantId, UUID reportUuid, NodeEntity parentNode, RootNetworkEntity rootNetworkEntity, BuildStatus buildStatus) {
        NodeEntity nodeEntity = nodeRepository.save(new NodeEntity(nodeId, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        NetworkModificationNodeInfoEntity modificationNodeInfoEntity = networkModificationNodeInfoRepository.save(NetworkModificationNodeInfoEntity.builder().idNode(nodeEntity.getIdNode()).nodeType(nodeType).modificationGroupUuid(UUID.randomUUID()).build());
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
