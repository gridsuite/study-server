/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
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
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class LoadFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowTest.class);

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String LOADFLOW_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String LOADFLOW_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String LOADFLOW_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final String LOADFLOW_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
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

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String loadflowResultDestination = "loadflow.result";
    private final String loadflowStoppedDestination = "loadflow.stopped";
    private final String loadflowFailedDestination = "loadflow.failed";

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        String loadFlowResultUuidStr = objectMapper.writeValueAsString(LOADFLOW_RESULT_UUID);

        String loadFlowErrorResultUuidStr = objectMapper.writeValueAsString(LOADFLOW_ERROR_RESULT_UUID);
        String loadflowResult = TestUtils.resourceToString("/loadflow-result.json");

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", LOADFLOW_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), loadflowResultDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(loadFlowResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), loadflowFailedDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(loadFlowErrorResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).setBody(loadflowResult)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(LOADFLOW_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + LOADFLOW_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? LOADFLOW_OTHER_NODE_RESULT_UUID : LOADFLOW_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), loadflowStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/all")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/loadflow\\?reportsList=.*")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }

        };

        server.setDispatcher(dispatcher);
    }

    @Test
    public void testLoadFlow() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID);
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

        // run a loadflow on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run a loadflow
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = objectMapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, UUID.fromString(LOADFLOW_RESULT_UUID));

        // get loadflow result
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/result", studyNameUserIdUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk()).andReturn();

        assertEquals(TestUtils.resourceToString("/loadflow-result.json"), mvcResult.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID)));

        // get loadflow status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/status", studyNameUserIdUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(LOADFLOW_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/status")));

        // stop loadflow analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/stop", studyNameUserIdUuid, modificationNode3Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        // loadflow failed
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode2Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        uuidResponse = objectMapper.readValue(resultAsString, UUID.class);

        assertEquals(LOADFLOW_ERROR_RESULT_UUID, uuidResponse.toString());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        //Test delete all results
        assertEquals(1, networkModificationNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/loadflow/results"))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results/all"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/supervision/loadflow\\?reportsList=.*")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
    }

    @Test
    @SneakyThrows
    public void testResetUuidResultWhenLFFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateLoadFlowResultUuid(modificationNode.getId(), resultUuid);
        assertTrue(networkModificationTreeService.getLoadFlowResultUuid(modificationNode.getId()).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getLoadFlowResultUuid(modificationNode.getId()).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), loadflowFailedDestination);
            return resultUuid;
        }).when(studyService).runLoadFlow(any(), any(), any());
        studyService.runLoadFlow(studyEntity.getId(), modificationNode.getId(), "");

        // Test reset uuid result in the database
        assertTrue(networkModificationTreeService.getLoadFlowResultUuid(modificationNode.getId()).isEmpty());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> loadFlowStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, loadFlowStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) loadFlowStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    @Test
    public void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No loadflow result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/result", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No loadflow status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/status", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // stop non existing loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, modificationNode1Uuid)).andExpect(status().isOk());
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
                .dcPowerFactor(1.0)
                .useReactiveLimits(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        SensitivityAnalysisParametersEntity defaultSensitivityAnalysisParametersEntity = SensitivityAnalysisService.toEntity(SensitivityAnalysisService.getDefaultSensitivityAnalysisParametersValues());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider,
                defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, defaultSensitivityAnalysisParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() {
                });
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

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination, loadflowResultDestination, loadflowStoppedDestination, loadflowFailedDestination);

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
