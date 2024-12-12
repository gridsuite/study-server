/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.sensitivity.SensitivityFunctionType;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityFactorsIdsByGroup;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.gridsuite.study.server.utils.TestUtils.getBinaryAsBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SensitivityAnalysisTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisTest.class);

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SENSITIVITY_ANALYSIS_UUID = "a3a80c9b-9594-4e55-8ec7-07ea965d24eb";

    private static final String FAKE_RESULT_JSON = "fake result json";
    private static final String SENSITIVITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);
    private static final List<UUID> MONITORED_BRANCHES_FILTERS_UUID = List.of(UUID.randomUUID());
    private static final List<UUID> INJECTIONS_FILTERS_UUID = List.of(UUID.randomUUID());
    private static final List<UUID> CONTINGENCIES_FILTERS_UUID = List.of(UUID.randomUUID());
    private static final SensitivityFactorsIdsByGroup IDS = SensitivityFactorsIdsByGroup.builder().ids(Map.of("0", MONITORED_BRANCHES_FILTERS_UUID, "1", INJECTIONS_FILTERS_UUID, "2", CONTINGENCIES_FILTERS_UUID)).build();

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMock;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String SENSITIVITY_ANALYSIS_RESULT_DESTINATION = "sensitivityanalysis.result";
    private static final String SENSITIVITY_ANALYSIS_STOPPED_DESTINATION = "sensitivityanalysis.stopped";
    private static final String SENSITIVITY_ANALYSIS_FAILED_DESTINATION = "sensitivityanalysis.failed";

    public static final String SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":0.0,\"angleFlowSensitivityValueThreshold\":0.0,\"flowVoltageSensitivityValueThreshold\":0.0," +
            "\"sensitivityInjectionsSet\":[],\"sensitivityInjection\":[],\"sensitivityHVDC\":[],\"sensitivityPST\":[],\"sensitivityNodes\":[]}";
    public static final String SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":90.0,\"angleFlowSensitivityValueThreshold\":0.6,\"flowVoltageSensitivityValueThreshold\":0.1,\"sensitivityInjectionsSet\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"distributionType\":\"PROPORTIONAL\",\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityInjection\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityHVDC\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"sensitivityType\":\"DELTA_MW\",\"hvdcs\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityPST\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"sensitivityType\":\"DELTA_MW\",\"psts\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityNodes\":[{\"monitoredVoltageLevels\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"equipmentsInVoltageRegulation\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}]}";

    private static final byte[] SENSITIVITY_RESULTS_AS_CSV = {0x00, 0x01};

    private static final UUID SENSITIVITY_ANALYSIS_PARAMETERS_UUID = UUID.randomUUID();
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private StudyService studyService;

    @BeforeEach
    void setup(final MockWebServer server) {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        wireMock = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));

        // Start the server.
        wireMock.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SENSITIVITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader(HEADER_USER_ID, "testUserId")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                        .build(), SENSITIVITY_ANALYSIS_RESULT_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + resultUuid + "\"");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                        .setHeader(HEADER_USER_ID, "testUserId")
                        .build(), SENSITIVITY_ANALYSIS_FAILED_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader(HEADER_USER_ID, "testUserId")
                        .build(), SENSITIVITY_ANALYSIS_FAILED_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/stop.*")
                    || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SENSITIVITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                        .build(), SENSITIVITY_ANALYSIS_STOPPED_DESTINATION);
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), FAKE_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/status")
                           || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SENSITIVITY_ANALYSIS_STATUS_JSON);
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "\\?.*")
                    || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "\\?.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), FAKE_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/filter-options" + "\\?.*")
                        || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/filter-options" + "\\?.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), FAKE_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/csv")
                        || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/csv") && request.getMethod().equals("POST")) {
                    return new MockResponse.Builder().code(200).body(getBinaryAsBuffer(SENSITIVITY_RESULTS_AS_CSV))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID) && request.getMethod().equals("DELETE")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SENSITIVITY_ANALYSIS_STATUS_JSON);
                } else if (path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_RESULT_UUID)
                           || path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/results")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/networks/" + ".*" + "/factors-count\\?isInjectionsSet" + "\\&ids.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "4");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    private void testSensitivityAnalysisWithNodeUuid(final MockWebServer server, UUID studyUuid, UUID nodeUuid, UUID resultUuid) throws Exception {
        // sensitivity analysis not found
        mockMvc.perform(get("/v1/sensitivity-analysis/results/{resultUuid}", NOT_FOUND_SENSITIVITY_ANALYSIS_UUID)).andExpect(status().isNotFound());

        // run sensitivity analysis
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid, nodeUuid)
            .header("userId", "userId")
            .header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk());

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));

        // get sensitivity analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, nodeUuid, "fakeJsonSelector"))
            .andExpectAll(status().isOk(), content().string(FAKE_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.contains("/v1/results/" + resultUuid)));

        // get sensitivity analysis result filter options
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, nodeUuid, "fakeJsonSelector"))
                .andExpectAll(status().isOk(), content().string(FAKE_RESULT_JSON));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.contains("/v1/results/" + resultUuid + "/filter-options")));

        // get sensitivity analysis status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/status", studyUuid, nodeUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/results/%s/status", resultUuid)));

        // export sensitivity analysis result as csv
        String content = objectMapper.writeValueAsString(SensitivityAnalysisCsvFileInfos.builder()
                .sensitivityFunctionType(SensitivityFunctionType.BRANCH_CURRENT_1)
                .resultTab("N")
                .csvHeaders(List.of("h1", "h2", "h3"))
                .build());

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv", studyUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("userId", "userId")
                        .content(content))
                .andExpectAll(status().isNotFound(), content().string("\"SENSITIVITY_ANALYSIS_NOT_FOUND\""));

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv", studyUuid, nodeUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("userId", "userId")
                        .content(content))
                .andExpectAll(status().isOk(), content().bytes(SENSITIVITY_RESULTS_AS_CSV));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.contains("/v1/results/" + resultUuid + "/csv")));
        // stop sensitivity analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop", studyUuid, nodeUuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS) || updateType.equals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    void testSensitivityAnalysis(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run sensitivity analysis on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyNameUserIdUuid, rootNodeUuid)
            .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isForbidden());

        testSensitivityAnalysisWithNodeUuid(server, studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        testSensitivityAnalysisWithNodeUuid(server, studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, UUID.randomUUID(), "fakeJsonSelector"))
            .andExpectAll(status().isNoContent());

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}",
                        studyNameUserIdUuid, UUID.randomUUID(), "fakeJsonSelector"))
                .andExpectAll(status().isNoContent());

        // run additional sensitivity analysis for deletion test
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyNameUserIdUuid, modificationNode2Uuid)
                .header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk())
            .andReturn();

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));

        //Test result count
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SENSITIVITY_ANALYSIS.toString())
                .queryParam("dryRun", "true"))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        //Delete Sensitivity analysis results
        assertEquals(1, rootNetworkNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SENSITIVITY_ANALYSIS.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        assertEquals(0, rootNetworkNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());

        String baseUrlWireMock = wireMock.baseUrl();
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID))
            .willReturn(WireMock.notFound().withBody("Oups did I ever let think suc a thing existed ?")));
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, modificationNode1Uuid, "fakeJsonSelector"))
            .andExpectAll(status().isNoContent());

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID))
            .willReturn(WireMock.serverError().withBody("{ \"message\": \"Oups\" }")));
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, modificationNode1Uuid, "fakeJsonSelector"))
            .andExpectAll(status().isNoContent());

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID))
            .willReturn(WireMock.serverError().withBody("flat message")));
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, modificationNode1Uuid, "fakeJsonSelector"))
            .andExpectAll(status().isNoContent());
    }

    @Test
    void testGetSensitivityResultWithWrongId() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        UUID notFoundSensitivityUuid = UUID.randomUUID();
        UUID studyUuid = studyEntity.getId();
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, UUID.randomUUID(), FAKE_RESULT_JSON))
                .andExpect(status().isNoContent()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, UUID.randomUUID(), FAKE_RESULT_JSON))
                .andExpect(status().isNoContent()).andReturn();

        String baseUrlWireMock = wireMock.baseUrl();
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNodeUuid, rootNetworkUuid, notFoundSensitivityUuid, SENSITIVITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS));
        assertEquals(notFoundSensitivityUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNodeUuid, rootNetworkUuid, SENSITIVITY_ANALYSIS));

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid))
                .willReturn(WireMock.notFound()));

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid + "/filter-options" + ".*"))
                .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, modificationNodeUuid, FAKE_RESULT_JSON))
                .andExpect(status().isNotFound()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, modificationNodeUuid, FAKE_RESULT_JSON))
                .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    void testResetUuidResultWhenSAFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, SENSITIVITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SENSITIVITY_ANALYSIS));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SENSITIVITY_ANALYSIS));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), SENSITIVITY_ANALYSIS_FAILED_DESTINATION);
            return resultUuid;
        }).when(studyService).runSensitivityAnalysis(any(), any(), any(), any());
        studyService.runSensitivityAnalysis(studyEntity.getId(), modificationNode.getId(), rootNetworkUuid, "testUserId");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SENSITIVITY_ANALYSIS));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);
    }

    // test sensitivity analysis on network 2 will fail
    @Test
    void testSensitivityAnalysisFailedForNotification(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        //run failing sensitivity analysis (because in network 2)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid, modificationNode1Uuid)
            .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // failed sensitivity analysis
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);

        // message sent by run and save controller to notify frontend sensitivity analysis is running and should update status
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));

        /*
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID);
        UUID studyUuid2 = studyEntity.getId();
        UUID rootNodeUuid2 = getRootNodeUuid(studyUuid2);
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid2, modificationNode1Uuid2)
            .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // failed sensitivity analysis without receiver -> no failure message sent to frontend

        // message sent by run and save controller to notify frontend sensitivity analysis is running and should update status
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, UUID.randomUUID(), null, null, SENSITIVITY_ANALYSIS_PARAMETERS_UUID,
                                                             defaultNonEvacuatedEnergyParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyService.getStudyFirstRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, SENSITIVITY_ANALYSIS_FAILED_DESTINATION, SENSITIVITY_ANALYSIS_RESULT_DESTINATION, SENSITIVITY_ANALYSIS_STOPPED_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    @Test
    void testSensitivityAnalysisParameters() throws Exception {
        // Mocking with WireMock
        String baseUrlWireMock = wireMock.baseUrl();
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON)));
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID))
            .willReturn(WireMock.ok()));

        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(SENSITIVITY_ANALYSIS_PARAMETERS_UUID))));

        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters/default"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(SENSITIVITY_ANALYSIS_PARAMETERS_UUID))));
        // end mocking

        UUID studyNameUserIdUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID()).getId();
        assertNotNull(studyNameUserIdUuid);

        // Get sensitivity analysis parameters (on existing)
        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMock.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));

        // Set sensitivity analysis parameters (will update)
        setParametersAndDoChecks(studyNameUserIdUuid, SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON);

        wireMock.verify(WireMock.putRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));

        // Get sensitivity analysis (not existing, so it will create default)
        StudyEntity studyEntityToUpdate = studyRepository.findById(studyNameUserIdUuid).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        studyEntityToUpdate.setSensitivityAnalysisParametersUuid(null);
        studyRepository.save(studyEntityToUpdate);

        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMock.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/default")));
        wireMock.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));
        assertEquals(SENSITIVITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSensitivityAnalysisParametersUuid());

        // Set sensitivity analysis parameters (will create)
        studyEntityToUpdate.setSensitivityAnalysisParametersUuid(null);
        studyRepository.save(studyEntityToUpdate);

        setParametersAndDoChecks(studyNameUserIdUuid, SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON);

        wireMock.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/parameters")));
        assertEquals(SENSITIVITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSensitivityAnalysisParametersUuid());
    }

    @Test
    void testGetSensitivityAnalysisFactorsCount(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        MockHttpServletRequestBuilder requestBuilder = get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/factors-count", studyNameUserIdUuid, rootNodeUuid);
        IDS.getIds().forEach((key, list) -> requestBuilder.queryParam(String.format("ids[%s]", key), list.stream().map(UUID::toString).toArray(String[]::new)));

        String resultAsString = mockMvc.perform(requestBuilder.header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertEquals("4", resultAsString);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + ".*" + "/factors-count.*")));
    }

    private void setParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters) throws Exception {
        mockMvc.perform(
            post("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)
                .header("userId", "userId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(parameters)).andExpect(
            status().isOk());

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, elementUpdateMessage.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }
}
