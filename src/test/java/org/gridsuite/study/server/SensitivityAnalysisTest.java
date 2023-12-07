/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/*
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisInputData;
import org.gridsuite.study.server.dto.sensianalysis.*;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.sensianalysis.SensitivityAnalysisParametersEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class SensitivityAnalysisTest {
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

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";
    private static String SENSITIVITY_INPUT = null;

    private static final long TIMEOUT = 1000;

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    private WireMockServer wireMock;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private FilterService filterService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String sensitivityAnalysisResultDestination = "sensitivityanalysis.result";
    private final String sensitivityAnalysisStoppedDestination = "sensitivityanalysis.stopped";
    private final String sensitivityAnalysisFailedDestination = "sensitivityanalysis.failed";

    public static final String SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":0.0,\"angleFlowSensitivityValueThreshold\":0.0,\"flowVoltageSensitivityValueThreshold\":0.0," +
            "\"sensitivityInjectionsSet\":[],\"sensitivityInjection\":[],\"sensitivityHVDC\":[],\"sensitivityPST\":[],\"sensitivityNodes\":[]}";
    public static final String SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":90.0,\"angleFlowSensitivityValueThreshold\":0.6,\"flowVoltageSensitivityValueThreshold\":0.1,\"sensitivityInjectionsSet\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"distributionType\":\"PROPORTIONAL\",\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityInjection\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityHVDC\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"sensitivityType\":\"DELTA_MW\",\"hvdcs\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityPST\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"sensitivityType\":\"DELTA_MW\",\"psts\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityNodes\":[{\"monitoredVoltageLevels\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"equipmentsInVoltageRegulation\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}]}";

    @Before
    public void setup() throws IOException {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();
        wireMock = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();
        wireMock.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        filterService.setFilterServerBaseUri(baseUrl);

        SensitivityAnalysisInputData sensitivityAnalysisInputData = SensitivityAnalysisInputData.builder()
            .sensitivityInjectionsSets(List.of(SensitivityAnalysisInputData.SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "name1")))
                .injections(List.of(new EquipmentsContainer(UUID.randomUUID(), "name2")))
                .distributionType(SensitivityAnalysisInputData.DistributionType.PROPORTIONAL)
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "name3"))).build()))
            .sensitivityInjections(List.of(SensitivityAnalysisInputData.SensitivityInjection.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "name4")))
                .injections(List.of(new EquipmentsContainer(UUID.randomUUID(), "name5")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "name6"))).build()))
            .sensitivityHVDCs(List.of(SensitivityAnalysisInputData.SensitivityHVDC.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "name7")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(new EquipmentsContainer(UUID.randomUUID(), "name8")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "name9"))).build()))
            .sensitivityPSTs(List.of(SensitivityAnalysisInputData.SensitivityPST.builder()
                .monitoredBranches(List.of(new EquipmentsContainer(UUID.randomUUID(), "name10")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(new EquipmentsContainer(UUID.randomUUID(), "name11")))
                    .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "name12"))).build()))
            .sensitivityNodes(List.of(SensitivityAnalysisInputData.SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(new EquipmentsContainer(UUID.randomUUID(), "name13")))
                .equipmentsInVoltageRegulation(List.of(new EquipmentsContainer(UUID.randomUUID(), "name14")))
                .contingencies(List.of(new EquipmentsContainer(UUID.randomUUID(), "name15"))).build()))
            .build();
        SENSITIVITY_INPUT = objectWriter.writeValueAsString(sensitivityAnalysisInputData);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SENSITIVITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), sensitivityAnalysisResultDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + resultUuid + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), sensitivityAnalysisFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .build(), sensitivityAnalysisFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/stop.*")
                    || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SENSITIVITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), sensitivityAnalysisStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).setBody(FAKE_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/status")
                           || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "\\?.*")
                    || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "\\?.*")) {
                    return new MockResponse().setResponseCode(200).setBody(FAKE_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/filter-options" + "\\?.*")
                        || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/filter-options" + "\\?.*")) {
                    return new MockResponse().setResponseCode(200).setBody(FAKE_RESULT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID) && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_RESULT_UUID)
                           || path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (path.matches("/v1/results")) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (path.matches("/v1/treereports")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("1");
                } else if (path.matches("/v1/filters/complexity\\?networkUuid=.*")) {
                    return new MockResponse().setResponseCode(200).setBody("4")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    private void testSensitivityAnalysisWithNodeUuid(UUID studyUuid, UUID nodeUuid, UUID resultUuid) throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        // sensitivity analysis not found
        mockMvc.perform(get("/v1/sensitivity-analysis/results/{resultUuid}", NOT_FOUND_SENSITIVITY_ANALYSIS_UUID)).andExpect(status().isNotFound());

        // run sensitivity analysis
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid, nodeUuid)
            .contentType(MediaType.APPLICATION_JSON).header("userId", "userId")
            .content(SENSITIVITY_INPUT)).andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = mapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, resultUuid);

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
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

        // stop sensitivity analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop", studyUuid, nodeUuid)).andExpect(status().isOk());

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS) || updateType.equals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    public void testSensitivityAnalysis() throws Exception {
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
            .contentType(MediaType.APPLICATION_JSON).header("userId", "userId")
            .content(SENSITIVITY_INPUT))
            .andExpect(status().isForbidden());

        testSensitivityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        testSensitivityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, UUID.randomUUID(), "fakeJsonSelector"))
            .andExpectAll(status().isNoContent());

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}",
                        studyNameUserIdUuid, UUID.randomUUID(), "fakeJsonSelector"))
                .andExpectAll(status().isNoContent());

        // run additional sensitivity analysis for deletion test
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyNameUserIdUuid, modificationNode2Uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(SENSITIVITY_INPUT)).andExpect(status().isOk())
            .andReturn();

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));

        //Test result count
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SENSITIVITY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(true)))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        //Delete Security analysis results
        assertEquals(1, networkModificationNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SENSITIVITY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(false)))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/treereports")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());

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
    @SneakyThrows
    public void testGetSensitivityResultWithWrongId() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID notFoundSensitivityUuid = UUID.randomUUID();
        UUID studyUuid = studyEntity.getId();
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, UUID.randomUUID(), FAKE_RESULT_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSITIVITY_INPUT))
                .andExpect(status().isNoContent()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, UUID.randomUUID(), FAKE_RESULT_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSITIVITY_INPUT))
                .andExpect(status().isNoContent()).andReturn();

        String baseUrlWireMock = wireMock.baseUrl();
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(modificationNodeUuid, notFoundSensitivityUuid);
        assertTrue(networkModificationTreeService.getSensitivityAnalysisResultUuid(modificationNodeUuid).isPresent());
        assertEquals(notFoundSensitivityUuid, networkModificationTreeService.getSensitivityAnalysisResultUuid(modificationNodeUuid).get());

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid))
                .willReturn(WireMock.notFound()));

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid + "/filter-options" + ".*"))
                .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, modificationNodeUuid, FAKE_RESULT_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSITIVITY_INPUT))
                .andExpect(status().isNotFound()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, modificationNodeUuid, FAKE_RESULT_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SENSITIVITY_INPUT))
                .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    @SneakyThrows
    public void testResetUuidResultWhenSAFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = mapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateSensitivityAnalysisResultUuid(modificationNode.getId(), resultUuid);
        assertTrue(networkModificationTreeService.getSensitivityAnalysisResultUuid(modificationNode.getId()).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getSensitivityAnalysisResultUuid(modificationNode.getId()).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), sensitivityAnalysisFailedDestination);
            return resultUuid;
        }).when(studyService).runSensitivityAnalysis(any(), any());
        studyService.runSensitivityAnalysis(studyEntity.getId(), modificationNode.getId());

        // Test reset uuid result in the database
        assertTrue(networkModificationTreeService.getSensitivityAnalysisResultUuid(modificationNode.getId()).isEmpty());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);
    }

    // test sensitivity analysis on network 2 will fail
    @Test
    public void testSensitivityAnalysisFailedForNotification() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        //run failing sensitivity analysis (because in network 2)
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid, modificationNode1Uuid)
            .contentType(MediaType.APPLICATION_JSON).header("userId", "userId")
            .content(SENSITIVITY_INPUT))
            .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        String uuidResponse = mapper.readValue(resultAsString, String.class);

        assertEquals(SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID, uuidResponse);

        // failed sensitivity analysis
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);

        // message sent by run and save controller to notify frontend sensitivity analysis is running and should update status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));

        /*
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        studyEntity = insertDummyStudyWithSpecificParams(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID);
        UUID studyUuid2 = studyEntity.getId();
        UUID rootNodeUuid2 = getRootNodeUuid(studyUuid2);
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid2, modificationNode1Uuid2)
            .contentType(MediaType.APPLICATION_JSON).header("userId", "userId")
            .content(SENSITIVITY_INPUT))
            .andExpect(status().isOk());

        // failed sensitivity analysis without receiver -> no failure message sent to frontend

        // message sent by run and save controller to notify frontend sensitivity analysis is running and should update status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*?receiver=.*nodeUuid.*")));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
            .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
            .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
            .dcPowerFactor(1.0)
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersValues = SensitivityAnalysisParametersInfos.builder()
                .flowFlowSensitivityValueThreshold(0.0)
                .angleFlowSensitivityValueThreshold(0.0)
                .flowVoltageSensitivityValueThreshold(0.0)
                .sensitivityInjectionsSet(new ArrayList<>())
                .sensitivityInjection(new ArrayList<>())
                .sensitivityHVDC(new ArrayList<>())
                .sensitivityPST(new ArrayList<>())
                .sensitivityNodes(new ArrayList<>())
                .build();
        SensitivityAnalysisParametersEntity sensitivityParametersEntity = SensitivityAnalysisService.toEntity(sensitivityAnalysisParametersValues);
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, sensitivityParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private StudyEntity insertDummyStudyWithSpecificParams(UUID networkUuid, UUID caseUuid) {
        List<LoadFlowSpecificParameterInfos> specificParams = List.of(LoadFlowSpecificParameterInfos.builder()
                .provider("OpenLoadFlow")
                .value("FULL_VOLTAGE")
                .name("voltageInitModeOverride")
                .build()
        );
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .dcPowerFactor(1.0)
                .specificParameters(LoadFlowSpecificParameterEntity.toLoadFlowSpecificParameters(specificParams))
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersValues = SensitivityAnalysisParametersInfos.builder()
                .flowFlowSensitivityValueThreshold(0.0)
                .angleFlowSensitivityValueThreshold(0.0)
                .flowVoltageSensitivityValueThreshold(0.0)
                .sensitivityInjectionsSet(new ArrayList<>())
                .sensitivityInjection(new ArrayList<>())
                .sensitivityHVDC(new ArrayList<>())
                .sensitivityPST(new ArrayList<>())
                .sensitivityNodes(new ArrayList<>())
                .build();
        SensitivityAnalysisParametersEntity sensitivityParametersEntity = SensitivityAnalysisService.toEntity(sensitivityAnalysisParametersValues);
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, sensitivityParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
            modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
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
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination, sensitivityAnalysisFailedDestination, sensitivityAnalysisResultDestination, sensitivityAnalysisStoppedDestination);

        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }

    @Test
    public void testSensitivityAnalysisParameters() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        UUID studyNameUserIdUuid = studyEntity.getId();
        //get sensitivity analysis parameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        //create sensitivity analysis Parameters
        EquipmentsContainer equipments1 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da321"), "identifiable1");
        EquipmentsContainer equipments2 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da322"), "identifiable2");
        EquipmentsContainer equipments3 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da323"), "identifiable3");
        SensitivityAnalysisInputData.SensitivityInjectionsSet injectionsSet = new SensitivityAnalysisInputData.SensitivityInjectionsSet(List.of(equipments2), List.of(equipments1), SensitivityAnalysisInputData.DistributionType.PROPORTIONAL, List.of(equipments3), true);
        SensitivityAnalysisInputData.SensitivityInjection injections = new SensitivityAnalysisInputData.SensitivityInjection(List.of(equipments1), List.of(equipments2), List.of(equipments3), true);
        SensitivityAnalysisInputData.SensitivityHVDC hvdc = new SensitivityAnalysisInputData.SensitivityHVDC(List.of(equipments1), SensitivityAnalysisInputData.SensitivityType.DELTA_MW, List.of(equipments2), List.of(equipments3), true);
        SensitivityAnalysisInputData.SensitivityPST pst = new SensitivityAnalysisInputData.SensitivityPST(List.of(equipments2), SensitivityAnalysisInputData.SensitivityType.DELTA_MW, List.of(equipments1), List.of(equipments3), true);
        SensitivityAnalysisInputData.SensitivityNodes nodes = new SensitivityAnalysisInputData.SensitivityNodes(List.of(equipments1), List.of(equipments2), List.of(equipments3), true);

        //create sensitivity analysis Parameters
        SensitivityAnalysisParametersInfos sensitivityAnalysisParametersValues = SensitivityAnalysisParametersInfos.builder()
                .flowFlowSensitivityValueThreshold(90)
                .angleFlowSensitivityValueThreshold(0.6)
                .flowVoltageSensitivityValueThreshold(0.1)
                .sensitivityInjectionsSet(List.of(injectionsSet))
                .sensitivityInjection(List.of(injections))
                .sensitivityHVDC(List.of(hvdc))
                .sensitivityPST(List.of(pst))
                .sensitivityNodes(List.of(nodes))
                .build();
        String mnBodyJson = objectWriter.writeValueAsString(sensitivityAnalysisParametersValues);

        mvcResult = mockMvc.perform(
                post("/v1/studies/{studyUuid}/sensitivity-analysis/complexity", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson)).andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        String count = mapper.readValue(resultAsString, String.class);

        assertEquals("4", count);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/filters/complexity.*")));

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson)).andExpect(
                status().isOk());

        //getting set values
        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON));
    }
}
