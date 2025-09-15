/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.DYNAWO_PROVIDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyControllerDynamicSecurityAnalysisTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyControllerDynamicSecurityAnalysisTest.class);

    private static final String API_VERSION = StudyApi.API_VERSION;
    private static final String DELIMITER = "/";
    private static final String STUDY_END_POINT = "studies";

    private static final String STUDY_BASE_URL = UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT);
    private static final String STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN = "{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-security-analysis/run";
    private static final String STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETERS = "{studyUuid}/dynamic-security-analysis/parameters";
    private static final String STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_PROVIDER = "{studyUuid}/dynamic-security-analysis/provider";
    private static final String STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_STATUS = "{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-security-analysis/status";

    private static final String HEADER_USER_ID_NAME = "userId";
    private static final String HEADER_USER_ID_VALUE = "userId";

    private static final String VARIANT_ID = "variant_1";

    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final UUID STUDY_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOT_NETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_NOT_RUN_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID PARAMETERS_UUID = UUID.randomUUID();

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc studyClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @SpyBean
    StudyService spyStudyService;

    @MockBean
    private LoadFlowService mockLoadFlowService;

    @MockBean
    private DynamicSimulationService mockDynamicSimulationService;

    @SpyBean
    private DynamicSecurityAnalysisService spyDynamicSecurityAnalysisService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @Autowired
    private TestUtils studyTestUtils;

    @SpyBean
    private RootNetworkNodeInfoRepository spyRootNetworkNodeInfoRepository;

    //output destinations
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String DSA_DEBUG_DESTINATION = "dsa.debug";
    private static final String DSA_RESULT_DESTINATION = "dsa.result";
    private static final String DSA_STOPPED_DESTINATION = "dsa.stopped";
    private static final String DSA_FAILED_DESTINATION = "dsa.run.dlx";

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, DSA_FAILED_DESTINATION, DSA_RESULT_DESTINATION, DSA_STOPPED_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(studyClient.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", UUID.randomUUID(), UUID.randomUUID(), null, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private NetworkModificationNode createNetworkModificationConstructionNode(UUID studyUuid, UUID parentNodeUuid,
                                                                              UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.CONSTRUCTION);
    }

    private NetworkModificationNode createNetworkModificationSecurityNode(UUID studyUuid, UUID parentNodeUuid,
                                                                          UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.SECURITY);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, NetworkModificationNodeType nodeType) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, nodeType, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, NetworkModificationNodeType nodeType, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName).nodeType(nodeType)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
                .children(Collections.emptyList()).build();

        studyClient.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid)
                        .content(objectMapper.writeValueAsString(modificationNode))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(mess).isNotNull();
        UUID newNodeId = UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE)));
        modificationNode.setId(newNodeId);
        assertThat(mess.getHeaders()).containsEntry(NotificationService.HEADER_INSERT_MODE, InsertMode.CHILD.name());

        rootNetworkNodeInfoService.updateRootNetworkNode(newNodeId, studyTestUtils.getOneRootNetworkUuid(studyUuid), RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    @Test
    void testRunDynamicSecurityAnalysisGivenSecurityNodeAndFailed() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);
        when(mockDynamicSimulationService.getStatus(any())).thenReturn(DynamicSimulationStatus.CONVERGED);

        // setup DynamicSecurityAnalysisService spy
        doAnswer(invocation -> RESULT_UUID)
            .when(spyDynamicSecurityAnalysisService).runDynamicSecurityAnalysis(
                any(), eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID),
                any(), any(), any(), any(), eq(false));

        // --- call endpoint to be tested --- //
        // run on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSecurityAnalysis of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_SECURITY_ANALYSIS);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-security-analysis-server in case of failed
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DSA_FAILED_DESTINATION
        );

        // --- check async messages emitted by consumeDsFailed of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_FAILED from channel : studyUpdateDestination
        dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_FAILED);

        // mock the notification from dynamic-security-analysis-server to send a debug status notif
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DSA_DEBUG_DESTINATION);

        // must have message COMPUTATION_DEBUG_FILE_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationResultMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSimulationResultMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.COMPUTATION_DEBUG_FILE_STATUS);

        // resultUuid must be empty in database at this moment
        assertThat(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_SECURITY_ANALYSIS)).isNull();
    }

    @Test
    void testRunDynamicSecurityAnalysisGivenRootNode() throws Exception {
        // create a root node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN,
                studyUuid, firstRootNetworkUuid, rootNodeUuid)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRunDynamicSecurityAnalysisGivenConstructionNode() throws Exception {
        // create a root node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        // create a construction node
        NetworkModificationNode modificationNode1 = createNetworkModificationConstructionNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        doAnswer(invocation -> DYNAWO_PROVIDER).when(spyDynamicSecurityAnalysisService).getProvider(any());

        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN,
                studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRunDynamicSecurityAnalysisGivenSecurityNode() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);
        when(mockDynamicSimulationService.getStatus(any())).thenReturn(DynamicSimulationStatus.CONVERGED);

        // setup DynamicSecurityAnalysisService mock
        doAnswer(invocation -> RESULT_UUID).when(spyDynamicSecurityAnalysisService).runDynamicSecurityAnalysis(any(),
            eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID), any(), any(), any(), any(), eq(false));

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSecurityAnalysis of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_SECURITY_ANALYSIS);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of having the result
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DSA_RESULT_DESTINATION
        );

        // --- check async messages emitted by consumeDsResult of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS from channel : studyUpdateDestination
        dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);

        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_RESULT from channel : studyUpdateDestination
        Message<byte[]> dynamicSecurityAnalysisResultMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisResultMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_RESULT);

        //Test result count
        doAnswer(invocation -> 1).when(spyDynamicSecurityAnalysisService).getResultsCount();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.DYNAMIC_SECURITY_ANALYSIS.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");

        //Delete Dynamic result init results
        Mockito.doNothing().when(spyDynamicSecurityAnalysisService).deleteAllResults();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.DYNAMIC_SECURITY_ANALYSIS.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");
    }

    @Test
    void testRunDynamicSecurityAnalysisGivenSecurityNodeAndStopped() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);
        when(mockDynamicSimulationService.getStatus(any())).thenReturn(DynamicSimulationStatus.CONVERGED);

        // setup DynamicSecurityAnalysisService mock
        doAnswer(invocation -> RESULT_UUID).when(spyDynamicSecurityAnalysisService).runDynamicSecurityAnalysis(any(),
            eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID), any(), any(), any(), any(), eq(false));

        // --- call endpoint to be tested --- //
        // run on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSecurityAnalysis of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_SECURITY_ANALYSIS);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of stop
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DSA_STOPPED_DESTINATION
        );

        // --- check async messages emitted by consumeDsStopped of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS from channel : studyUpdateDestination
        dynamicSecurityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSecurityAnalysisStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
    }

    @Test
    void testGetDynamicSecurityAnalysisStatusResultGivenNodeNotRun() throws Exception {
        // setup DynamicSecurityAnalysisService mock
        doAnswer(invocation -> null).when(spyDynamicSecurityAnalysisService).getStatus(RESULT_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node not yet run
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_STATUS,
                        STUDY_UUID, ROOT_NETWORK_UUID, NODE_NOT_RUN_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetDynamicSecurityAnalysisStatus() throws Exception {
        // setup DynamicSecurityAnalysisService mock
        Mockito.doReturn(Optional.of(RootNetworkNodeInfoEntity.builder().id(UUID.randomUUID()).dynamicSecurityAnalysisResultUuid(RESULT_UUID).build()))
            .when(spyRootNetworkNodeInfoRepository).findByNodeInfoIdAndRootNetworkId(NODE_UUID, ROOT_NETWORK_UUID);
        doAnswer(invocation -> DynamicSecurityAnalysisStatus.FAILED).when(spyDynamicSecurityAnalysisService).getStatus(RESULT_UUID);

        // --- call endpoint to be tested --- //
        // get status from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_STATUS,
                        STUDY_UUID, ROOT_NETWORK_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        DynamicSecurityAnalysisStatus statusResult = objectMapper.readValue(result.getResponse().getContentAsString(), DynamicSecurityAnalysisStatus.class);

        // --- check result --- //
        DynamicSecurityAnalysisStatus statusExpected = DynamicSecurityAnalysisStatus.FAILED;
        LOGGER.info("Status expected = {}", statusExpected);
        LOGGER.info("Status result = {}", statusResult);
        assertThat(statusResult).isEqualTo(statusExpected);
    }

    // @Disabled("fix later")
    @Test
    void testSetAndGetDynamicSecurityAnalysisParameters() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // prepare request body
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider(DYNAWO_PROVIDER)
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();
        String jsonParameters = objectMapper.writeValueAsString(parameters);

        // setup DynamicSecurityAnalysisService mock
        doAnswer(invocation -> PARAMETERS_UUID)
                .when(spyDynamicSecurityAnalysisService).createParameters(any());
        doAnswer(invocation -> jsonParameters)
                .when(spyDynamicSecurityAnalysisService).getParameters(PARAMETERS_UUID);

        MvcResult result;

        // --- call endpoint to be tested --- //
        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                    .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        // check notifications
        checkNotificationsAfterModifyingDynamicSecurityAnalysisParameters(studyUuid);

        // get parameters
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

        String resultJson = result.getResponse().getContentAsString();

        // result parameters must be identical to persisted parameters
        LOGGER.info("Parameters expected in Json = {}", jsonParameters);
        LOGGER.info("Parameters result in Json = {}", resultJson);
        assertThat(objectMapper.readTree(resultJson)).isEqualTo(objectMapper.readTree(jsonParameters));

    }

    @Test
    void testSetDynamicSecurityAnalysisProvider() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // setup DynamicSecurityAnalysisService mock
        doAnswer(invocation -> null)
                .when(spyDynamicSecurityAnalysisService).updateProvider(any(), any());

        // --- call endpoint to be tested --- //
        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SECURITY_ANALYSIS_END_POINT_PROVIDER, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DYNAWO_PROVIDER)))
                .andExpect(status().isOk());

        // check notifications
        checkNotificationsAfterModifyingDynamicSecurityAnalysisParameters(studyUuid);
    }

    private void checkNotificationsAfterModifyingDynamicSecurityAnalysisParameters(UUID studyUuid) {
        // must have message UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS and UPDATE_TYPE_COMPUTATION_PARAMETERS from channel : studyUpdateDestination
        Message<byte[]> studyUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(studyUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        studyUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS, studyUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // must have message HEADER_USER_ID_VALUE from channel : elementUpdateDestination
        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertThat(elementUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_ELEMENT_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_MODIFIED_BY, HEADER_USER_ID_VALUE);
    }

}
