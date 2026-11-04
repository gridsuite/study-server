/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.AllArgsConstructor;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.logicalcontrols.LogicalControlsRestService;
import org.gridsuite.study.server.service.stateestimation.StateEstimationRestService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.ComputationServerStubs;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class LogicalControlsTest {

    private static final String LOGICAL_CONTROLS_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/logical-controls/";
    private static final String COMPUTATION_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computations/";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String LOGICAL_CONTROLS_RESULT_UUID = "b3f1f5a2-0d6c-4c1e-9b0a-2f7a3e1d4c55";
    private static final String LOGICAL_CONTROLS_ERROR_RESULT_UUID = "7d4c1e2b-5a3f-4b8e-9c0d-1e2f3a4b5c6d";
    private static final String LOGICAL_CONTROLS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String ALL_COMPUTATION_STATUS_JSON = "{\"LOAD_FLOW\":null,\"SECURITY_ANALYSIS\":null," +
            "\"SENSITIVITY_ANALYSIS\":null,\"SHORT_CIRCUIT\":null,\"SHORT_CIRCUIT_ONE_BUS\":null," +
            "\"VOLTAGE_INITIALIZATION\":null,\"DYNAMIC_SIMULATION\":null,\"DYNAMIC_SECURITY_ANALYSIS\":null," +
            "\"DYNAMIC_MARGIN_CALCULATION\":null,\"STATE_ESTIMATION\":null,\"PCC_MIN\":null," +
            "\"ASYMMETRICAL_LOAD\":null,\"LOGICAL_CONTROLS\":\"{\\\"status\\\":\\\"COMPLETED\\\"}\"}";
    private static final String LOGICAL_CONTROLS_RESULT_JSON = "[{\"id\":\"control1\"}]";

    private static final UUID CASE_UUID = UUID.fromString("00000000-8cf0-11bd-b23e-10b96e4ef00d");
    private static final String VARIANT_ID = "variant_1";
    private static final UUID STATE_ESTIMATION_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f5");
    private static final String VARIANT_ID_2 = "variant_2";

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String LOGICAL_CONTROLS_RESULT_DESTINATION = "logicalcontrols.result";
    private static final String LOGICAL_CONTROLS_FAILED_DESTINATION = "logicalcontrols.run.dlx";

    private static final String LOGICAL_CONTROLS_PREFIX = "logical-controls/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoSpyBean
    private LogicalControlsRestService logicalControlsRestService;
    @Autowired
    private StateEstimationRestService stateEstimationRestService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private ReportService reportService;
    @Autowired
    private SupervisionService supervisionService;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private TestUtils studyTestUtils;
    @Autowired
    private ConsumerService consumerService;

    private WireMockServer wireMockServer;
    private ComputationServerStubs computationServerStubs;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        computationServerStubs = new ComputationServerStubs(wireMockServer);
        configureFor("localhost", wireMockServer.port());
        String baseUrl = wireMockServer.baseUrl();

        logicalControlsRestService.setBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        wireMockServer.stop();
        TestUtils.assertQueuesEmptyThenClear(
            List.of(STUDY_UPDATE_DESTINATION, LOGICAL_CONTROLS_RESULT_DESTINATION, LOGICAL_CONTROLS_FAILED_DESTINATION),
            output
        );
    }

    @AllArgsConstructor
    private static final class StudyNodeIds {
        UUID studyId;
        UUID rootNetworkUuid;
        UUID nodeId;
    }

    private StudyNodeIds createStudyAndNode(String variantId, String nodeName) throws Exception {
        StudyEntity studyEntity = TestUtils.CreateDummyStudyBuilder.builder()
                .setNetworkUuid(UUID.fromString(NETWORK_UUID_STRING)).setNetworkId("netId")
                .setCaseUuid(CASE_UUID).setCaseFormat("").setCaseName("")
                .setLoadFlowParametersUuid(UUID.randomUUID())
                .setStateEstimationParametersUuid(STATE_ESTIMATION_PARAMETERS_UUID)
                .build();
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);

        UUID studyUuid = studyEntity.getId();
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        NetworkModificationNode node = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), variantId, nodeName);
        return new StudyNodeIds(studyUuid, rootNetworkUuid, node.getId());
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(
            mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(),
            new TypeReference<>() {
            });
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder()
            .name(nodeName)
            .description("description")
            .modificationGroupUuid(modificationGroupUuid)
            .variantId(variantId)
            .children(Collections.emptyList())
            .build();

        String mnBodyJson = objectMapper.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid)
                .content(mnBodyJson)
                .contentType(MediaType.APPLICATION_JSON)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        Message<?> msg = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertNotNull(msg);
        modificationNode.setId(UUID.fromString(String.valueOf(msg.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), msg.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(),
            studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    private void checkLogicalControlsMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        Message<byte[]> message = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(updateTypeToCheck, message.getHeaders().get(HEADER_UPDATE_TYPE));
    }

    private void consumeLogicalControlsResult(StudyNodeIds ids, String resultUuid) throws JsonProcessingException {
        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        MessageHeaders headers = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, receiverJson));
        consumerService.consumeLogicalControlsResult().accept(MessageBuilder.createMessage("", headers));

        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_RESULT);
    }

    private void runLogicalControls(StudyNodeIds ids) throws Exception {
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, LOGICAL_CONTROLS_RESULT_UUID, LOGICAL_CONTROLS_PREFIX);

        mockMvc.perform(post(LOGICAL_CONTROLS_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumeLogicalControlsResult(ids, LOGICAL_CONTROLS_RESULT_UUID);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/logical-controls/networks/" + NETWORK_UUID_STRING + "/run-and-save",
            true, Map.of("variantId", WireMock.equalTo(VARIANT_ID), "parametersUuid", WireMock.equalTo(STATE_ESTIMATION_PARAMETERS_UUID.toString())), null, 1);
    }

    @Test
    void testNoQuota() {
        assertNull(QuotaType.mapFromComputationType(ComputationType.LOGICAL_CONTROLS));
    }

    @Test
    void consumeLogicalControlsRunning() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        Message<String> runningMessage = MessageBuilder.withPayload("")
                .setHeader(HEADER_RECEIVER, objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid)))
                .build();
        consumerService.consumeLogicalControlsRunning().accept(runningMessage);
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
    }

    @Test
    void testRunAndCheckStatus() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        runLogicalControls(ids);

        assertEquals(UUID.fromString(LOGICAL_CONTROLS_RESULT_UUID),
            rootNetworkNodeInfoService.getComputationResultUuid(ids.nodeId, ids.rootNetworkUuid, ComputationType.LOGICAL_CONTROLS));

        computationServerStubs.stubGetResultStatus(LOGICAL_CONTROLS_RESULT_UUID, LOGICAL_CONTROLS_STATUS_JSON, LOGICAL_CONTROLS_PREFIX);
        mockMvc.perform(get(LOGICAL_CONTROLS_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpectAll(status().isOk(), content().string(LOGICAL_CONTROLS_STATUS_JSON));
        computationServerStubs.verifyGetResultStatus(LOGICAL_CONTROLS_RESULT_UUID, 1, LOGICAL_CONTROLS_PREFIX);

        mockMvc.perform(get(COMPUTATION_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpectAll(status().isOk(), content().string(ALL_COMPUTATION_STATUS_JSON));
    }

    @Test
    void testStatusWithoutRun() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        mockMvc.perform(get(LOGICAL_CONTROLS_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(LOGICAL_CONTROLS_URL_BASE + "result", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isNoContent());
    }

    @Test
    void testParameterUpdateInvalidatesOnlyStudyResults() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runLogicalControls(ids);
        StudyNodeIds otherStudy = createStudyAndNode(VARIANT_ID, "other study node");
        UUID otherResultUuid = UUID.randomUUID();
        rootNetworkNodeInfoService.updateComputationResultUuid(otherStudy.nodeId, otherStudy.rootNetworkUuid, otherResultUuid, ComputationType.LOGICAL_CONTROLS);
        UUID stateEstimationResultUuid = UUID.randomUUID();
        rootNetworkNodeInfoService.updateComputationResultUuid(ids.nodeId, ids.rootNetworkUuid, stateEstimationResultUuid, ComputationType.STATE_ESTIMATION);
        wireMockServer.stubFor(WireMock.put(urlPathEqualTo("/v1/results/invalidate-status"))
            .withQueryParam("resultUuid", equalTo(stateEstimationResultUuid.toString())).willReturn(ok()));
        wireMockServer.stubFor(WireMock.put(urlPathEqualTo("/v1/logical-controls/results/invalidate-status"))
            .withQueryParam("resultUuid", equalTo(LOGICAL_CONTROLS_RESULT_UUID)).willReturn(ok()));

        updateStateEstimationParameters(ids);

        wireMockServer.verify(1, putRequestedFor(urlPathEqualTo("/v1/results/invalidate-status"))
            .withQueryParam("resultUuid", equalTo(stateEstimationResultUuid.toString())));
        wireMockServer.verify(1, putRequestedFor(urlPathEqualTo("/v1/logical-controls/results/invalidate-status"))
            .withQueryParam("resultUuid", equalTo(LOGICAL_CONTROLS_RESULT_UUID)));
        wireMockServer.verify(0, putRequestedFor(urlPathEqualTo("/v1/logical-controls/results/invalidate-status"))
            .withQueryParam("resultUuid", containing(otherResultUuid.toString())));
    }

    @Test
    void testParameterUpdateWithoutLogicalControlsResults() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");

        updateStateEstimationParameters(ids);

        wireMockServer.verify(0, putRequestedFor(urlPathEqualTo("/v1/logical-controls/results/invalidate-status")));
    }

    private void updateStateEstimationParameters(StudyNodeIds ids) throws Exception {
        stateEstimationRestService.setBaseUri(wireMockServer.baseUrl());
        wireMockServer.stubFor(WireMock.put(urlPathEqualTo("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID))
            .willReturn(ok()));

        mockMvc.perform(post("/v1/studies/{studyUuid}/state-estimation/parameters", ids.studyId)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(TestUtils.resourceToString("/estim-parameters.json")))
            .andExpect(status().isOk());

        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_COMPUTATION_PARAMETERS);
    }

    @Test
    void testStop() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        runLogicalControls(ids);

        wireMockServer.stubFor(WireMock.put(urlPathEqualTo("/v1/logical-controls/results/" + LOGICAL_CONTROLS_RESULT_UUID + "/stop"))
            .willReturn(ok()));

        mockMvc.perform(put(LOGICAL_CONTROLS_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk());

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        computationServerStubs.verifyComputationStop(LOGICAL_CONTROLS_RESULT_UUID,
            Map.of("receiver", WireMock.equalTo(URLEncoder.encode(receiverJson, StandardCharsets.UTF_8))), LOGICAL_CONTROLS_PREFIX);

        consumerService.consumeLogicalControlsStopped().accept(MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", LOGICAL_CONTROLS_RESULT_UUID)
            .build());

        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(ids.nodeId, ids.rootNetworkUuid, ComputationType.LOGICAL_CONTROLS));
        mockMvc.perform(get(LOGICAL_CONTROLS_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isNoContent());
    }

    @Test
    void testStopWithoutRun() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        mockMvc.perform(put(LOGICAL_CONTROLS_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk());
        wireMockServer.verify(0, putRequestedFor(urlPathMatching("/v1/logical-controls/results/.*/stop")));
    }

    @Test
    void testFailure() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2");
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, LOGICAL_CONTROLS_ERROR_RESULT_UUID, LOGICAL_CONTROLS_PREFIX);

        mockMvc.perform(post(LOGICAL_CONTROLS_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());
        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_STATUS);

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", LOGICAL_CONTROLS_ERROR_RESULT_UUID)
            .setHeader("userId", "userId")
            .build();
        consumerService.consumeLogicalControlsFailed().accept(failedMessage);

        checkLogicalControlsMessagesReceived(ids.studyId, UPDATE_TYPE_LOGICAL_CONTROLS_FAILED);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/logical-controls/networks/" + NETWORK_UUID_STRING + "/run-and-save",
            true, Map.of("variantId", WireMock.equalTo(VARIANT_ID_2), "parametersUuid", WireMock.equalTo(STATE_ESTIMATION_PARAMETERS_UUID.toString())), null, 1);
    }

    @Test
    void testResultsDeletion() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runLogicalControls(ids);

        assertEquals(1, rootNetworkNodeInfoRepository.findAllByLogicalControlsResultUuidNotNull().size());

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/supervision/logical-controls/results-count"))
            .willReturn(okJson("1")));
        wireMockServer.stubFor(delete(urlPathEqualTo("/v1/logical-controls/results"))
            .willReturn(ok()));
        wireMockServer.stubFor(delete(urlPathMatching("/v1/reports.*"))
            .willReturn(ok()));

        Integer dryRunCount = supervisionService.deleteComputationResults(ComputationType.LOGICAL_CONTROLS, true);
        assertEquals(1, dryRunCount);
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v1/supervision/logical-controls/results-count")));

        Integer deletedCount = supervisionService.deleteComputationResults(ComputationType.LOGICAL_CONTROLS, false);
        assertEquals(1, deletedCount);
        wireMockServer.verify(1, deleteRequestedFor(urlPathEqualTo("/v1/logical-controls/results")));

        assertEquals(0, rootNetworkNodeInfoRepository.findAllByLogicalControlsResultUuidNotNull().size());
    }

    @Test
    void testGetLogicalControlsResults() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runLogicalControls(ids);

        UUID stubId = wireMockServer.stubFor(WireMock.get(urlPathEqualTo("/v1/logical-controls/results/" + LOGICAL_CONTROLS_RESULT_UUID))
            .willReturn(okJson(LOGICAL_CONTROLS_RESULT_JSON))).getId();

        mockMvc.perform(get(LOGICAL_CONTROLS_URL_BASE + "result", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk())
            .andExpect(content().string(LOGICAL_CONTROLS_RESULT_JSON));
        assertEquals(1, wireMockServer.getAllServeEvents().stream().filter(e -> e.getStubMapping().getId().equals(stubId)).count());

        // results NOT FOUND
        UUID resultUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.get(urlPathEqualTo("/v1/logical-controls/results/" + resultUuid))
            .willReturn(WireMock.notFound()));
        assertThrows(HttpClientErrorException.NotFound.class, () ->
            logicalControlsRestService.getLogicalControlsResult(resultUuid)
        );
    }
}
