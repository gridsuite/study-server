/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.dto.workflow.RerunLoadFlowInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.repository.voltageinit.StudyVoltageInitParametersEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class LoadFLowIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFLowIntegrationTest.class);

    UUID studyUuid;
    UUID nodeUuid;
    UUID rootNetworkUuid;
    UUID reportUuid = UUID.randomUUID();
    UUID networkUuid = UUID.randomUUID();
    String networkId = "networkId";
    UUID caseUuid = UUID.randomUUID();
    String caseFormat = "caseFormat";
    String caseName = "caseName";
    String variantId = "variantId";
    UUID parametersUuid = UUID.randomUUID();
    UUID loadflowResultUuid = UUID.randomUUID();

    String userId = "userId";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private TestUtils testUtils;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNodeInfoRepository rootNodeInfoRepository;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    ConsumerService consumerService;
    @MockBean
    private ReportService reportService;
    @MockBean
    private NetworkModificationService networkModificationService;
    @MockBean
    private NetworkService networkService;
    @MockBean
    private UserAdminService userAdminService;

    @SpyBean
    LoadFlowService loadFlowService;

    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        loadFlowService.setLoadFlowServerBaseUri(baseUrlWireMock);
        wireMockUtils = new WireMockUtils(wireMockServer);

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
            .networkUuid(networkUuid)
            .networkId(networkId)
            .caseUuid(caseUuid)
            .caseFormat(caseFormat)
            .caseName(caseName)
            .indexationStatus(RootNetworkIndexationStatus.INDEXED)
            .build();

        study.addRootNetwork(firstRootNetworkEntity);
        studyRepository.save(study);
        studyUuid = study.getId();
        NodeEntity rootNode = insertRootNode(study, UUID.randomUUID());
        NodeEntity node1 = insertNode(study, nodeUuid, variantId, reportUuid, rootNode, firstRootNetworkEntity, BuildStatus.BUILT);
        rootNetworkRepository.save(firstRootNetworkEntity);
        rootNetworkUuid = testUtils.getOneRootNetworkUuid(studyUuid);
        nodeUuid = node1.getIdNode();
    }

    @Test
    void testRerunLoadFlowWithoutRatioTapChangers() throws Exception {
        runLoadFlow(false);
        rerunLoadFlow(false);
    }

    @Test
    void testRerunLoadFlowWithRatioTapChangers() throws Exception {
        runLoadFlow(true);
        rerunLoadFlow(true);
        Mockito.verify(networkModificationService, times(2)).deleteIndexedModifications(any(), any(UUID.class));
    }

    @Test
    void testRerunLoadFlowNotAllowed() throws Exception {
        LoadFlowParametersInfos loadFlowParametersInfos = createLoadFlowParametersInfos("DynaFlow");
        UUID loadFlowParametersStubUuid = wireMockUtils.stubLoadFlowParameters(parametersUuid, objectMapper.writeValueAsString(loadFlowParametersInfos));
        Mockito.doReturn(parametersUuid).when(loadFlowService).createDefaultLoadFlowParameters();
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyUuid, rootNetworkUuid, nodeUuid, userId)
                        .param(QUERY_WITH_TAP_CHANGER, "false")
                        .header("userId", userId))
                .andExpect(status().isForbidden());
        wireMockUtils.verifyLoadFlowParametersGet(loadFlowParametersStubUuid, parametersUuid);
    }

    private void runLoadFlow(boolean withRatioTapChangers) throws Exception {
        LoadFlowParametersInfos loadFlowParametersInfos = createLoadFlowParametersInfos("test_provider");
        UUID runLoadflowStubUuid = wireMockUtils.stubRunLoadFlow(networkUuid, withRatioTapChangers, null, objectMapper.writeValueAsString(loadflowResultUuid));
        UUID loadFlowParametersStubUuid = wireMockUtils.stubLoadFlowParameters(parametersUuid, objectMapper.writeValueAsString(loadFlowParametersInfos));
        Mockito.doReturn(parametersUuid).when(loadFlowService).createDefaultLoadFlowParameters();
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyUuid, rootNetworkUuid, nodeUuid, userId)
                .param(QUERY_WITH_TAP_CHANGER, withRatioTapChangers ? "true" : "false")
                .header("userId", userId))
            .andExpect(status().isOk());
        wireMockUtils.verifyRunLoadflow(runLoadflowStubUuid, networkUuid, withRatioTapChangers, null);
        wireMockUtils.verifyLoadFlowParametersGet(loadFlowParametersStubUuid, parametersUuid);
    }

    private void rerunLoadFlow(boolean withRatioTapChangers) throws Exception {
        UUID newLoadflowResultUuid = UUID.randomUUID();
        LoadFlowParametersInfos loadFlowParametersInfos = createLoadFlowParametersInfos("test_provider");
        UUID stubDeleteLoadflowResultUuid = wireMockUtils.stubDeleteLoadFlowResults(List.of(loadflowResultUuid));
        UUID stubCreateRunningLoadflowStatusUuid = wireMockUtils.stubCreateRunningLoadflowStatus(objectMapper.writeValueAsString(newLoadflowResultUuid));
        UUID loadFlowParametersStubUuid = wireMockUtils.stubLoadFlowParameters(parametersUuid, objectMapper.writeValueAsString(loadFlowParametersInfos));

        Mockito.doReturn(parametersUuid).when(loadFlowService).createDefaultLoadFlowParameters();

        assertNodeBlocked(nodeUuid, rootNetworkUuid, false);

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyUuid, rootNetworkUuid, nodeUuid, userId)
                .param(QUERY_WITH_TAP_CHANGER, withRatioTapChangers ? "true" : "false")
                .header("userId", userId))
            .andExpect(status().isOk());

        Mockito.verify(loadFlowService, times(2)).createDefaultLoadFlowParameters();
        wireMockUtils.verifyDeleteLoadFlowResults(stubDeleteLoadflowResultUuid, List.of(loadflowResultUuid));
        wireMockUtils.verifyCreateRunningLoadflowStatus(stubCreateRunningLoadflowStatusUuid);
        ArgumentCaptor<RerunLoadFlowInfos> rerunLoadFlowWorkflowInfosArgumentCaptor = ArgumentCaptor.forClass(RerunLoadFlowInfos.class);
        Mockito.verify(networkModificationService, times(1)).buildNode(eq(nodeUuid), eq(rootNetworkUuid), any(BuildInfos.class), rerunLoadFlowWorkflowInfosArgumentCaptor.capture());
        assertEquals(withRatioTapChangers, rerunLoadFlowWorkflowInfosArgumentCaptor.getValue().isWithRatioTapChangers());
        wireMockUtils.verifyLoadFlowParametersGet(loadFlowParametersStubUuid, parametersUuid);

        // verify that the node is blocked
        // build is forbidden, for example
        assertNodeBlocked(nodeUuid, rootNetworkUuid, true);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/build", studyUuid, rootNetworkUuid, nodeUuid).header(HEADER_USER_ID, userId))
            .andExpect(status().isForbidden());

        // consume loadflow result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", loadflowResultUuid.toString(), "withRatioTapChangers", withRatioTapChangers, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeLoadFlowResult().accept(MessageBuilder.createMessage("", messageHeaders));

        assertNodeBlocked(nodeUuid, rootNetworkUuid, false);
    }

    private void assertNodeBlocked(UUID nodeUuid, UUID rootNetworkUuid, boolean isNodeBlocked) {
        Optional<RootNetworkNodeInfoEntity> networkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid);
        assertTrue(networkNodeInfoEntity.isPresent());
        assertEquals(isNodeBlocked, networkNodeInfoEntity.get().getBlockedBuild());
    }

    private LoadFlowParametersInfos createLoadFlowParametersInfos(String provider) {
        return LoadFlowParametersInfos.builder()
            .provider(provider)
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();
    }

    private StudyEntity insertStudy() {
        return StudyEntity.builder()
            .id(UUID.randomUUID())
            .voltageInitParameters(new StudyVoltageInitParametersEntity())
            .build();
    }

    private NodeEntity insertNode(StudyEntity study, UUID nodeId, String variantId, UUID reportUuid, NodeEntity parentNode, RootNetworkEntity rootNetworkEntity, BuildStatus buildStatus) {
        NodeEntity nodeEntity = nodeRepository.save(new NodeEntity(nodeId, parentNode, NodeType.NETWORK_MODIFICATION, study, false, null));
        NetworkModificationNodeInfoEntity modificationNodeInfoEntity = networkModificationNodeInfoRepository.save(NetworkModificationNodeInfoEntity.builder().idNode(nodeEntity.getIdNode()).modificationGroupUuid(UUID.randomUUID()).nodeType(NetworkModificationNodeType.CONSTRUCTION).build());
        createNodeLinks(rootNetworkEntity, modificationNodeInfoEntity, variantId, reportUuid, buildStatus);
        return nodeEntity;
    }

    // We can't use the method RootNetworkNodeInfoService::createNodeLinks because there is no transaction in a session
    private void createNodeLinks(RootNetworkEntity rootNetworkEntity, NetworkModificationNodeInfoEntity modificationNodeInfoEntity,
                                 String variantId, UUID reportUuid, BuildStatus buildStatus) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = RootNetworkNodeInfoEntity.builder().variantId(variantId).modificationReports(Map.of(modificationNodeInfoEntity.getId(), reportUuid)).nodeBuildStatus(NodeBuildStatus.from(buildStatus).toEntity()).blockedBuild(false).build();
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
        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
