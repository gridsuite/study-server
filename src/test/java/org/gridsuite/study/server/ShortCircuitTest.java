/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitParametersInfos;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.sensianalysis.SensitivityAnalysisParametersEntity;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.SensitivityAnalysisService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.dto.ComputationType;
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

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
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
public class ShortCircuitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitTest.class);

    private static final String CASE_SHORT_CIRCUIT_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_SHORT_CIRCUIT_UUID = UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String SHORT_CIRCUIT_ANALYSIS_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final String SHORT_CIRCUIT_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"faults\":[]";

    private static final String SHORT_CIRCUIT_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String VARIANT_ID = "variant_1";

    private static final String VARIANT_ID_2 = "variant_2";

    private static final String VARIANT_ID_3 = "variant_3";

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

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
    private ShortCircuitService shortCircuitService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String shortCircuitAnalysisResultDestination = "shortcircuitanalysis.result";
    private final String shortCircuitAnalysisStoppedDestination = "shortcircuitanalysis.stopped";
    private final String shortCircuitAnalysisFailedDestination = "shortcircuitanalysis.failed";

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        String shortCircuitAnalysisResultUuidStr = objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);

        String shortCircuitAnalysisErrorResultUuidStr = objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&busId=BUS_TEST_ID&variantId=" + VARIANT_ID_2)) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .setHeader("busId", "BUS_TEST_ID")
                        .build(), shortCircuitAnalysisResultDestination);
                    return new MockResponse().setResponseCode(200)
                        .setBody(shortCircuitAnalysisResultUuidStr)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), shortCircuitAnalysisResultDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(shortCircuitAnalysisResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), shortCircuitAnalysisFailedDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(shortCircuitAnalysisErrorResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")) {
                    return new MockResponse().setResponseCode(200).setBody(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault_results/paged" + "\\?mode=FULL&page=0&size=20&sort=id,DESC")) {
                    return new MockResponse().setResponseCode(200).setBody(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/feeder_results/paged" + "\\?mode=FULL&filters=fakeFilters&page=0&size=20&sort=id,DESC")) {
                    return new MockResponse().setResponseCode(200).setBody(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/invalidate-status\\?resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID : SHORT_CIRCUIT_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), shortCircuitAnalysisStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/treereports")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/results-count")) {
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

    @Test
    public void testShortCircuitAnalysisParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING));
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get default ShortCircuitParameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(TestUtils.resourceToString("/short-circuit-parameters.json")));

        // change some short circuit parameters
        ShortCircuitParameters shortCircuitParameters = ShortCircuitService.newShortCircuitParameters(StudyType.TRANSIENT, 20, true, true, false, false, true, true, true, true, InitialVoltageProfileMode.NOMINAL, null);
        ShortCircuitParametersInfos shortCircuitParametersInfos = new ShortCircuitParametersInfos();
        shortCircuitParametersInfos.setParameters(shortCircuitParameters);
        shortCircuitParametersInfos.setPredefinedParameters(ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_CEI909);

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)
                        .header(HEADER_USER_ID, "testUserId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectWriter.writeValueAsString(shortCircuitParametersInfos))).andExpect(
                status().isOk());

        //getting set values
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(TestUtils.resourceToString("/short-circuit-updated-parameters.json")));
    }

    @Test
    public void testShortCircuit() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");
        UUID modificationNode4Uuid = modificationNode4.getId();

        UUID unknownModificationNodeUuid = UUID.randomUUID();

        // run a short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run a short circuit analysis
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = objectMapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, UUID.fromString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID));

        // get short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, modificationNode3Uuid))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")));

        // get short circuit result but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        assertTrue(TestUtils.getRequestsDone(0, server).isEmpty());

        // get short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));

        // stop short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, modificationNode3Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        // short circuit analysis failed
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode2Uuid)
                .header(HEADER_USER_ID, "testUserId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        uuidResponse = objectMapper.readValue(resultAsString, UUID.class);

        assertEquals(SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID, uuidResponse.toString());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        //Test result count
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SHORT_CIRCUIT))
                .queryParam("dryRun", String.valueOf(true)))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        //Delete Shortcircuit results
        assertEquals(1, networkModificationNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SHORT_CIRCUIT))
                .queryParam("dryRun", String.valueOf(false)))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/treereports")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
    }

    @Test
    public void testPagedShortCircuit() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID_2, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        UUID unknownModificationNodeUuid = UUID.randomUUID();

        //run a short circuit analysis
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = objectMapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, UUID.fromString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID));

        // get short circuit result with pagination
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault_results/paged\\?mode=FULL&page=0&size=20&sort=id,DESC")));

        // get short circuit result with pagination but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20", studyNameUserIdUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        assertTrue(TestUtils.getRequestsDone(0, server).isEmpty());

        // get short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));

        // stop short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, modificationNode1Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    public void testOneBusShortCircuit() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
            UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
            modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
            modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
            modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");

        // run a one bus short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, rootNodeUuid)
                .param("busId", "BUS_TEST_ID")
                .header("userId", "userId"))
            .andExpect(status().isForbidden());

        //run a one bus short circuit analysis
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode3Uuid)
                .param("busId", "BUS_TEST_ID")
                .header("userId", "userId"))
            .andExpect(status().isOk())
            .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = objectMapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, UUID.fromString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID));

        // get one bus short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
            );

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")));

        // get short circuit result with pagination
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC&filters=fakeFilters", studyNameUserIdUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name())
        ).andExpectAll(
            status().isOk(),
            content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/feeder_results/paged\\?mode=FULL&filters=fakeFilters&page=0&size=20&sort=id,DESC")));

        // get one bus short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON)
            );

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));
    }

    @Test
    @SneakyThrows
    public void testResetUuidResultWhenSCFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateShortCircuitAnalysisResultUuid(modificationNode.getId(), resultUuid);
        assertTrue(networkModificationTreeService.getShortCircuitAnalysisResultUuid(modificationNode.getId(), ShortcircuitAnalysisType.ALL_BUSES).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getShortCircuitAnalysisResultUuid(modificationNode.getId(), ShortcircuitAnalysisType.ALL_BUSES).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), shortCircuitAnalysisFailedDestination);
            return resultUuid;
        }).when(studyService).runShortCircuit(any(), any(), any());
        studyService.runShortCircuit(studyEntity.getId(), modificationNode.getId(), "");

        // Test reset uuid result in the database
        assertTrue(networkModificationTreeService.getShortCircuitAnalysisResultUuid(modificationNode.getId(), ShortcircuitAnalysisType.ALL_BUSES).isEmpty());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> shortCircuitAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, shortCircuitAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) shortCircuitAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    @Test
    public void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No one bus short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, modificationNode1Uuid)
                    .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
                .andExpectAll(status().isNoContent());

        // No short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // stop non existing short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, modificationNode1Uuid)).andExpect(status().isOk());
    }

    @Test
    public void testInvalidateShortCircuitStatus() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        //run a short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        //run a one bus short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .param("busId", "BUS_TEST_ID")
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // invalidate status
        mockMvc.perform(put("/v1/studies/{studyUuid}/short-circuit/invalidate-status", studyNameUserIdUuid)
                .header("userId", "userId")).andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/invalidate-status\\?resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)));

    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        SensitivityAnalysisParametersEntity defaultSensitivityParametersEntity = SensitivityAnalysisService.toEntity(SensitivityAnalysisService.getDefaultSensitivityAnalysisParametersValues());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", "defaultLoadflowProvider",
                defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, defaultSensitivityParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
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
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
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

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination, shortCircuitAnalysisResultDestination, shortCircuitAnalysisStoppedDestination, shortCircuitAnalysisFailedDestination);

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
}
