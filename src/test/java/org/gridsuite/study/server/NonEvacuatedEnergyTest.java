/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.iidm.network.EnergySource;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.dto.nonevacuatedenergy.*;
import org.gridsuite.study.server.dto.sensianalysis.EquipmentsContainer;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class NonEvacuatedEnergyTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonEvacuatedEnergyTest.class);

    private static final String NON_EVACUATED_ENERGY_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";
    private static final String NON_EVACUATED_ENERGY_ERROR_NODE_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_NON_EVACUATED_ENERGY_UUID = "a3a80c9b-9594-4e55-8ec7-07ea965d24eb";

    private static final String FAKE_NON_EVACUATED_ENERGY_RESULT_JSON = "fake non evacuated energy result json";
    private static final String NON_EVACUATED_ENERGY_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);

    private static final String USER_ID = "userId";
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final long TIMEOUT = 1000;

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
    private NonEvacuatedEnergyService nonEvacuatedEnergyService;

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

    @MockBean
    private LoadFlowService loadFlowService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String nonEvacuatedEnergyResultDestination = "nonEvacuatedEnergy.result";
    private final String nonEvacuatedEnergyStoppedDestination = "nonEvacuatedEnergy.stopped";
    private final String nonEvacuatedEnergyFailedDestination = "nonEvacuatedEnergy.failed";

    @Before
    public void setup() throws IOException {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();
        wireMock = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();
        wireMock.start();

        when(loadFlowService.getLoadFlowParametersOrDefaultsUuid(any()))
            .thenReturn(UUID.randomUUID());

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        nonEvacuatedEnergyService.setSensitivityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/non-evacuated-energy.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID : NON_EVACUATED_ENERGY_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader(HEADER_USER_ID, USER_ID)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), nonEvacuatedEnergyResultDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + resultUuid + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/non-evacuated-energy.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader(HEADER_USER_ID, USER_ID)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), nonEvacuatedEnergyFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + NON_EVACUATED_ENERGY_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/non-evacuated-energy.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .build(), nonEvacuatedEnergyFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + NON_EVACUATED_ENERGY_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_RESULT_UUID + "/stop.*")
                    || path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID : NON_EVACUATED_ENERGY_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), nonEvacuatedEnergyStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).setBody(FAKE_NON_EVACUATED_ENERGY_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_RESULT_UUID + "/status")
                           || path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(NON_EVACUATED_ENERGY_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_RESULT_UUID + ".*")
                    || path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID + ".*")) {
                    return new MockResponse().setResponseCode(200).setBody(FAKE_NON_EVACUATED_ENERGY_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_RESULT_UUID) && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200).setBody(NON_EVACUATED_ENERGY_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results/invalidate-status?resultUuid=" + NON_EVACUATED_ENERGY_RESULT_UUID)
                           || path.matches("/v1/non-evacuated-energy/results/invalidate-status?resultUuid=" + NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (path.matches("/v1/non-evacuated-energy/results")) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (path.matches("/v1/treereports")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/non-evacuated-energy/results-count")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("1");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    private void testNonEvacuatedEnergyWithNodeUuid(UUID studyUuid, UUID nodeUuid, UUID resultUuid) throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        // sensitivity analysis non evacuated energy not found
        mockMvc.perform(get("/v1/non-evacuated-energy/results/{resultUuid}", NOT_FOUND_NON_EVACUATED_ENERGY_UUID)).andExpect(status().isNotFound());

        // run sensitivity analysis non evacuated energy
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run", studyUuid, nodeUuid)
            .contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID)).andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = mapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, resultUuid);

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/non-evacuated-energy.*?receiver=.*nodeUuid.*")));

        // get result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result", studyUuid, nodeUuid))
            .andExpectAll(status().isOk(), content().string(FAKE_NON_EVACUATED_ENERGY_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.contains("/v1/non-evacuated-energy/results/" + resultUuid)));

        // get status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/status", studyUuid, nodeUuid)).andExpectAll(
            status().isOk(),
            content().string(NON_EVACUATED_ENERGY_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/non-evacuated-energy/results/%s/status", resultUuid)));

        // stop sensitivity analysis non evacuated energy
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/stop", studyUuid, nodeUuid)).andExpect(status().isOk());

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS) || updateType.equals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_RESULT));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/non-evacuated-energy/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    public void testNonEvacuatedEnergy() throws Exception {
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

        // run sensitivity analysis non evacuated energy on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run", studyNameUserIdUuid, rootNodeUuid)
            .contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isForbidden());

        testNonEvacuatedEnergyWithNodeUuid(studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(NON_EVACUATED_ENERGY_RESULT_UUID));
        testNonEvacuatedEnergyWithNodeUuid(studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(NON_EVACUATED_ENERGY_OTHER_NODE_RESULT_UUID));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result",
                studyNameUserIdUuid, UUID.randomUUID()))
            .andExpectAll(status().isNoContent());

        // run additional sensitivity analysis non evacuated energy for deletion test
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run", studyNameUserIdUuid, modificationNode2Uuid)
                .contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID)).andExpect(status().isOk())
            .andReturn();

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_RESULT, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/non-evacuated-energy.*?receiver=.*nodeUuid.*")));

        //Test result count
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(true)))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/non-evacuated-energy/results-count")));

        //Delete sensitivity analysis results
        assertEquals(1, networkModificationNodeInfoRepository.findAllByNonEvacuatedEnergyResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(false)))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/non-evacuated-energy/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/treereports")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllByNonEvacuatedEnergyResultUuidNotNull().size());

        String baseUrlWireMock = wireMock.baseUrl();
        nonEvacuatedEnergyService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/non-evacuated-energy/results/" + NON_EVACUATED_ENERGY_RESULT_UUID))
            .willReturn(WireMock.notFound().withBody("Oups did I ever let think such a thing existed ?")));
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result",
                studyNameUserIdUuid, modificationNode1Uuid))
            .andExpectAll(status().isNoContent());
    }

    @Test
    @SneakyThrows
    public void testGetSensitivityNonEvacuatedEnergyResultWithWrongId() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID notFoundSensitivityUuid = UUID.randomUUID();
        UUID studyUuid = studyEntity.getId();
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result", studyUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()).andReturn();

        String baseUrlWireMock = wireMock.baseUrl();
        nonEvacuatedEnergyService.setSensitivityAnalysisServerBaseUri(baseUrlWireMock);

        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();
        networkModificationTreeService.updateComputationResultUuid(modificationNodeUuid, notFoundSensitivityUuid, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS);
        assertTrue(networkModificationTreeService.getComputationResultUuid(modificationNodeUuid, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS).isPresent());
        assertEquals(notFoundSensitivityUuid, networkModificationTreeService.getComputationResultUuid(modificationNodeUuid, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS).get());

        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/non-evacuated-energy/results/" + notFoundSensitivityUuid))
                .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/result", studyUuid, modificationNodeUuid, FAKE_NON_EVACUATED_ENERGY_RESULT_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    @SneakyThrows
    public void testResetUuidResultWhenNonEvacuatedEnergyFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = mapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateComputationResultUuid(modificationNode.getId(), resultUuid, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS);
        assertTrue(networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), ComputationType.NON_EVACUATED_ENERGY_ANALYSIS).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), ComputationType.NON_EVACUATED_ENERGY_ANALYSIS).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), nonEvacuatedEnergyFailedDestination);
            return resultUuid;
        }).when(studyService).runNonEvacuatedEnergy(any(), any(), any());
        studyService.runNonEvacuatedEnergy(studyEntity.getId(), modificationNode.getId(), USER_ID);

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_FAILED, updateType);
        assertTrue(networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), ComputationType.NON_EVACUATED_ENERGY_ANALYSIS).isEmpty());
    }

    @Test
    public void testNonEvacuatedEnergyFailedForNotification() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // run failing sensitivity analysis non evacuated energy (because in network 2)
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run", studyUuid, modificationNode1Uuid)
            .contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        String uuidResponse = mapper.readValue(resultAsString, String.class);

        assertEquals(NON_EVACUATED_ENERGY_ERROR_NODE_RESULT_UUID, uuidResponse);

        // failed sensitivity analysis non evacuated energy
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_FAILED, updateType);

        // message sent by run controller to notify frontend sensitivity analysis non evacuated energy is running and should update status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/non-evacuated-energy.*?receiver=.*nodeUuid.*")));

        /*
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        studyEntity = insertDummyStudyWithSpecificParams(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID);
        UUID studyUuid2 = studyEntity.getId();
        UUID rootNodeUuid2 = getRootNodeUuid(studyUuid2);
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/non-evacuated-energy/run", studyUuid2, modificationNode1Uuid2)
            .contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isOk());

        // failed sensitivity analysis non evacuated energy without receiver -> no failure message sent to frontend

        // message sent by run controller to notify frontend sensitivity analysis non evacuated energy is running and should update status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/non-evacuated-energy.*?receiver=.*nodeUuid.*")));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        NonEvacuatedEnergyParametersEntity nonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", UUID.randomUUID(), defaultShortCircuitParametersEntity, null, null,
                                                             nonEvacuatedEnergyParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private StudyEntity insertDummyStudyWithSpecificParams(UUID networkUuid, UUID caseUuid) {

        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        NonEvacuatedEnergyParametersEntity nonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", UUID.randomUUID(), defaultShortCircuitParametersEntity, null, null,
                                                             nonEvacuatedEnergyParametersEntity);
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

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header(HEADER_USER_ID, USER_ID))
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
        List<String> destinations = List.of(studyUpdateDestination, nonEvacuatedEnergyFailedDestination, nonEvacuatedEnergyResultDestination, nonEvacuatedEnergyStoppedDestination);

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
    public void testNonEvacuatedEnergyParameters() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        UUID studyNameUserIdUuid = studyEntity.getId();

        // get non evacuated energy analysis parameters
        NonEvacuatedEnergyParametersInfos defaultNonEvacuatedEnergyParametersInfos = NonEvacuatedEnergyParametersInfos.builder()
            .stagesDefinition(List.of())
            .stagesSelection(List.of())
            .generatorsCappings(new NonEvacuatedEnergyGeneratorsCappings(0.0, List.of()))
            .monitoredBranches(List.of())
            .contingencies(List.of())
            .build();
        String defaultJson = mapper.writeValueAsString(defaultNonEvacuatedEnergyParametersInfos);

        mockMvc.perform(get("/v1/studies/{studyUuid}/non-evacuated-energy/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(defaultJson));

        // create non evacuated energy parameters
        EquipmentsContainer generators1 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da321"), "identifiable1");
        EquipmentsContainer generators2 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da322"), "identifiable2");
        EquipmentsContainer generators3 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da323"), "identifiable3");
        EquipmentsContainer generators4 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da324"), "identifiable4");
        EquipmentsContainer generators5 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da325"), "identifiable5");

        NonEvacuatedEnergyStageDefinition stageDefinition1 = new NonEvacuatedEnergyStageDefinition(List.of(generators1, generators2), EnergySource.HYDRO, List.of(100F, 70F, 30F));
        NonEvacuatedEnergyStageDefinition stageDefinition2 = new NonEvacuatedEnergyStageDefinition(List.of(generators3), EnergySource.WIND, List.of(90F, 60F, 40F));
        NonEvacuatedEnergyStageDefinition stageDefinition3 = new NonEvacuatedEnergyStageDefinition(List.of(generators4, generators5), EnergySource.SOLAR, List.of(75F, 65F, 20F));

        NonEvacuatedEnergyStagesSelection stagesSelection1 = new NonEvacuatedEnergyStagesSelection("stage1", List.of(0, 1, 2), List.of(0, 0, 0), true);
        NonEvacuatedEnergyStagesSelection stagesSelection2 = new NonEvacuatedEnergyStagesSelection("stage2", List.of(0, 1, 2), List.of(0, 1, 1), true);
        NonEvacuatedEnergyStagesSelection stagesSelection3 = new NonEvacuatedEnergyStagesSelection("stage3", List.of(0, 1, 2), List.of(1, 1, 2), true);
        NonEvacuatedEnergyStagesSelection stagesSelection4 = new NonEvacuatedEnergyStagesSelection("stage4", List.of(0, 1, 2), List.of(1, 2, 1), true);

        EquipmentsContainer generators6 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da326"), "identifiable6");
        EquipmentsContainer generators7 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da327"), "identifiable7");
        EquipmentsContainer generators8 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da328"), "identifiable8");
        EquipmentsContainer generators9 = new EquipmentsContainer(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da329"), "identifiable9");

        NonEvacuatedEnergyGeneratorsCappings generatorsCappings = new NonEvacuatedEnergyGeneratorsCappings(0.03,
            List.of(new NonEvacuatedEnergyGeneratorCappingsByType(List.of(generators6), EnergySource.SOLAR, true),
                    new NonEvacuatedEnergyGeneratorCappingsByType(List.of(generators7, generators8), EnergySource.WIND, false),
                    new NonEvacuatedEnergyGeneratorCappingsByType(List.of(generators9), EnergySource.HYDRO, true)));

        EquipmentsContainer branches1 = new EquipmentsContainer(UUID.fromString("af399ef3-7f14-4884-8c82-1c90300da321"), "identifiable1");
        EquipmentsContainer branches2 = new EquipmentsContainer(UUID.fromString("af399ef3-7f14-4884-8c82-1c90300da322"), "identifiable2");
        EquipmentsContainer branches3 = new EquipmentsContainer(UUID.fromString("af399ef3-7f14-4884-8c82-1c90300da323"), "identifiable3");
        EquipmentsContainer branches4 = new EquipmentsContainer(UUID.fromString("af399ef3-7f14-4884-8c82-1c90300da324"), "identifiable4");

        NonEvacuatedEnergyMonitoredBranches monitoredBranches1 = new NonEvacuatedEnergyMonitoredBranches(List.of(branches1), true, false, "IT10", 80, true, null, 90);
        NonEvacuatedEnergyMonitoredBranches monitoredBranches2 = new NonEvacuatedEnergyMonitoredBranches(List.of(branches2), true, true, null, 60, true, null, 50);
        NonEvacuatedEnergyMonitoredBranches monitoredBranches3 = new NonEvacuatedEnergyMonitoredBranches(List.of(branches3), true, true, null, 90, false, "IT20", 70);
        NonEvacuatedEnergyMonitoredBranches monitoredBranches4 = new NonEvacuatedEnergyMonitoredBranches(List.of(branches4), true, false, "IT5", 75, false, "IT1", 80);

        EquipmentsContainer contingenciesContainer1 = new EquipmentsContainer(UUID.fromString("ef399ef3-7f14-4884-8c82-1c90300da321"), "identifiable1");
        EquipmentsContainer contingenciesContainer2 = new EquipmentsContainer(UUID.fromString("ef399ef3-7f14-4884-8c82-1c90300da322"), "identifiable2");
        EquipmentsContainer contingenciesContainer3 = new EquipmentsContainer(UUID.fromString("ef399ef3-7f14-4884-8c82-1c90300da323"), "identifiable3");

        NonEvacuatedEnergyContingencies contingencies1 = new NonEvacuatedEnergyContingencies(List.of(contingenciesContainer1), false);
        NonEvacuatedEnergyContingencies contingencies2 = new NonEvacuatedEnergyContingencies(List.of(contingenciesContainer2), true);
        NonEvacuatedEnergyContingencies contingencies3 = new NonEvacuatedEnergyContingencies(List.of(contingenciesContainer3), true);

        NonEvacuatedEnergyParametersInfos nonEvacuatedEnergyParametersInfos = NonEvacuatedEnergyParametersInfos.builder()
            .stagesDefinition(List.of(stageDefinition1, stageDefinition2, stageDefinition3))
            .stagesSelection(List.of(stagesSelection1, stagesSelection2, stagesSelection3, stagesSelection4))
            .generatorsCappings(generatorsCappings)
            .monitoredBranches(List.of(monitoredBranches1, monitoredBranches2, monitoredBranches3, monitoredBranches4))
            .contingencies(List.of(contingencies1, contingencies2, contingencies3))
            .build();
        String myBodyJson = mapper.writeValueAsString(nonEvacuatedEnergyParametersInfos);

        mockMvc.perform(
            post("/v1/studies/{studyUuid}/non-evacuated-energy/parameters", studyNameUserIdUuid)
                .header(HEADER_USER_ID, USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(myBodyJson)).andExpect(
            status().isOk());

        mockMvc.perform(get("/v1/studies/{studyUuid}/non-evacuated-energy/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(myBodyJson));
    }
}
