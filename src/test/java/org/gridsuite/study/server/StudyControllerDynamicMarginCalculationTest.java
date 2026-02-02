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
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
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
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
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
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_DEBUG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyControllerDynamicMarginCalculationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyControllerDynamicMarginCalculationTest.class);

    private static final String API_VERSION = StudyApi.API_VERSION;
    private static final String DELIMITER = "/";
    private static final String STUDY_END_POINT = "studies";

    private static final String STUDY_BASE_URL = UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT);
    private static final String STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN = "{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-margin-calculation/run";
    private static final String STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETERS = "{studyUuid}/dynamic-margin-calculation/parameters";
    private static final String STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_PROVIDER = "{studyUuid}/dynamic-margin-calculation/provider";
    private static final String STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_STATUS = "{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/dynamic-margin-calculation/status";

    private static final String HEADER_USER_ID_NAME = "userId";
    private static final String HEADER_USER_ID_VALUE = "userId";

    private static final String VARIANT_ID = "variant_1";
    private static final String PARAMETERS_JSON = "parametersJson";

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

    @MockitoSpyBean
    StudyService spyStudyService;

    @MockitoBean
    private LoadFlowService mockLoadFlowService;

    @MockitoSpyBean
    private DynamicMarginCalculationService spyDynamicMarginCalculationService;

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

    @MockitoSpyBean
    private StudyService studyService;

    @MockitoSpyBean
    private RootNetworkNodeInfoRepository spyRootNetworkNodeInfoRepository;

    //output destinations
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String DMC_DEBUG_DESTINATION = "dmc.debug";
    private static final String DMC_RESULT_DESTINATION = "dmc.result";
    private static final String DMC_STOPPED_DESTINATION = "dmc.stopped";
    private static final String DMC_FAILED_DESTINATION = "dmc.run.dlx";

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, DMC_FAILED_DESTINATION, DMC_RESULT_DESTINATION, DMC_STOPPED_DESTINATION);
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

        reset(studyService);
        doNothing().when(studyService).createNodePostAction(eq(studyUuid), eq(parentNodeUuid), any(NetworkModificationNode.class), eq("userId"));

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

        verify(studyService, times(1)).createNodePostAction(eq(studyUuid), eq(parentNodeUuid), any(NetworkModificationNode.class), eq("userId"));

        return modificationNode;
    }

    @Test
    void testRunDynamicMarginCalculationGivenSecurityNodeAndFailed() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);

        // setup DynamicMarginCalculationService spy
        doAnswer(invocation -> RESULT_UUID)
            .when(spyDynamicMarginCalculationService).runDynamicMarginCalculation(
                any(), eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID),
                any(), any(), any(), any(), any(), eq(true));

        // --- call endpoint to be tested --- //
        // run in debug mode on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .param(QUERY_PARAM_DEBUG, "true")
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicMarginCalculation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_MARGIN_CALCULATION);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-security-analysis-server in case of failed
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DMC_FAILED_DESTINATION
        );

        // --- check async messages emitted by consumeDsFailed of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_FAILED from channel : studyUpdateDestination
        dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_FAILED);

        // mock the notification from dynamic-security-analysis-server to send a debug status notif
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DMC_DEBUG_DESTINATION);

        // must have message COMPUTATION_DEBUG_FILE_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationResultMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicSimulationResultMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.COMPUTATION_DEBUG_FILE_STATUS);

        // resultUuid must always be present in database at this moment
        assertThat(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_MARGIN_CALCULATION)).isEqualTo(RESULT_UUID);
    }

    @Test
    void testRunDynamicMarginCalculationGivenRootNode() throws Exception {
        // create a root node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN,
                studyUuid, firstRootNetworkUuid, rootNodeUuid)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRunDynamicMarginCalculationGivenConstructionNode() throws Exception {
        // create a root node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        // create a construction node
        NetworkModificationNode modificationNode1 = createNetworkModificationConstructionNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        doAnswer(invocation -> DYNAWO_PROVIDER).when(spyDynamicMarginCalculationService).getProvider(any());

        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN,
                studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRunDynamicMarginCalculationGivenSecurityNode() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);

        // setup DynamicMarginCalculationService mock
        doAnswer(invocation -> RESULT_UUID).when(spyDynamicMarginCalculationService).runDynamicMarginCalculation(any(),
            eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID), any(), any(), any(), any(), any(), eq(false));

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicMarginCalculation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_MARGIN_CALCULATION);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of having the result
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DMC_RESULT_DESTINATION
        );

        // --- check async messages emitted by consumeDsResult of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS from channel : studyUpdateDestination
        dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);

        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_RESULT from channel : studyUpdateDestination
        Message<byte[]> dynamicMarginCalculationResultMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationResultMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_RESULT);

        //Test result count
        doAnswer(invocation -> 1).when(spyDynamicMarginCalculationService).getResultsCount();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.DYNAMIC_MARGIN_CALCULATION.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");

        //Delete Dynamic result init results
        doNothing().when(spyDynamicMarginCalculationService).deleteAllResults();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.DYNAMIC_MARGIN_CALCULATION.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");
    }

    @Test
    void testRunDynamicMarginCalculationGivenSecurityNodeAndStopped() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationSecurityNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(mockLoadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED);

        // setup DynamicMarginCalculationService mock
        doAnswer(invocation -> RESULT_UUID).when(spyDynamicMarginCalculationService).runDynamicMarginCalculation(any(),
            eq(modificationNode1Uuid), eq(firstRootNetworkUuid), eq(NETWORK_UUID), eq(VARIANT_ID), any(), any(), any(), any(), any(), eq(false));

        // --- call endpoint to be tested --- //
        // run on a security node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN,
                        studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicMarginCalculation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNode1Uuid, firstRootNetworkUuid, ComputationType.DYNAMIC_MARGIN_CALCULATION);
        LOGGER.info("Actual result uuid in the database = {}", actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of stop
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), DMC_STOPPED_DESTINATION
        );

        // --- check async messages emitted by consumeDsStopped of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS from channel : studyUpdateDestination
        dynamicMarginCalculationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(dynamicMarginCalculationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
    }

    @Test
    void testGetDynamicMarginCalculationStatusResultGivenNodeNotRun() throws Exception {
        // setup DynamicMarginCalculationService mock
        doAnswer(invocation -> null).when(spyDynamicMarginCalculationService).getStatus(RESULT_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node not yet run
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_STATUS,
                        STUDY_UUID, ROOT_NETWORK_UUID, NODE_NOT_RUN_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    void testGetDynamicMarginCalculationStatus() throws Exception {
        // setup DynamicMarginCalculationService mock
        Mockito.doReturn(Optional.of(RootNetworkNodeInfoEntity.builder().id(UUID.randomUUID()).dynamicMarginCalculationResultUuid(RESULT_UUID).build()))
            .when(spyRootNetworkNodeInfoRepository).findByNodeInfoIdAndRootNetworkId(NODE_UUID, ROOT_NETWORK_UUID);
        doAnswer(invocation -> DynamicMarginCalculationStatus.FAILED).when(spyDynamicMarginCalculationService).getStatus(RESULT_UUID);

        // --- call endpoint to be tested --- //
        // get status from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_STATUS,
                        STUDY_UUID, ROOT_NETWORK_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        DynamicMarginCalculationStatus statusResult = DynamicMarginCalculationStatus.valueOf(result.getResponse().getContentAsString());

        // --- check result --- //
        DynamicMarginCalculationStatus statusExpected = DynamicMarginCalculationStatus.FAILED;
        LOGGER.info("Status expected = {}", statusExpected);
        LOGGER.info("Status result = {}", statusResult);
        assertThat(statusResult).isEqualTo(statusExpected);
    }

    // @Disabled("fix later")
    @Test
    void testSetAndGetDynamicMarginCalculationParameters() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // prepare request body
        String jsonParameters = PARAMETERS_JSON;

        // setup DynamicMarginCalculationService mock
        doAnswer(invocation -> PARAMETERS_UUID)
                .when(spyDynamicMarginCalculationService).createParameters(any());
        doAnswer(invocation -> jsonParameters)
                .when(spyDynamicMarginCalculationService).getParameters(PARAMETERS_UUID, "userId");

        MvcResult result;

        // --- call endpoint to be tested --- //
        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonParameters))
                    .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        // check notifications
        checkNotificationsAfterModifyingDynamicMarginCalculationParameters(studyUuid);

        // get parameters
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

        String resultJson = result.getResponse().getContentAsString();

        // result parameters must be identical to persisted parameters
        LOGGER.info("Parameters expected in Json = {}", jsonParameters);
        LOGGER.info("Parameters result in Json = {}", resultJson);
        assertThat(resultJson).isEqualTo(jsonParameters);

    }

    @Test
    void testSetDynamicMarginCalculationProvider() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // setup DynamicMarginCalculationService mock
        doAnswer(invocation -> null)
                .when(spyDynamicMarginCalculationService).updateProvider(any(), any());

        // --- call endpoint to be tested --- //
        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_MARGIN_CALCULATION_END_POINT_PROVIDER, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DYNAWO_PROVIDER)))
                .andExpect(status().isOk());

        // check notifications
        checkNotificationsAfterModifyingDynamicMarginCalculationParameters(studyUuid);
    }

    private void checkNotificationsAfterModifyingDynamicMarginCalculationParameters(UUID studyUuid) {
        // must have message UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS and UPDATE_TYPE_COMPUTATION_PARAMETERS from channel : studyUpdateDestination
        Message<byte[]> studyUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertThat(studyUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_MARGIN_CALCULATION_STATUS);
        studyUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS, studyUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // must have message HEADER_USER_ID_VALUE from channel : elementUpdateDestination
        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertThat(elementUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_ELEMENT_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_MODIFIED_BY, HEADER_USER_ID_VALUE);
    }

}
