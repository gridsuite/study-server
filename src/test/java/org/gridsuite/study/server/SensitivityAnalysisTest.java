/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

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
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.service.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class SensitivityAnalysisTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisTest.class);

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SENSITIVITY_ANALYSIS_UUID = "a3a80c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"factors\":[],\"contingencies\":[],\"values\":[]}";
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

    //output destinations
    private String studyUpdateDestination = "study.update";
    private String sensitivityAnalysisResultDestination = "sensitivityanalysis.result";
    private String sensitivityAnalysisStoppedDestination = "sensitivityanalysis.stopped";
    private String sensitivityAnalysisFailedDestination = "sensitivityanalysis.failed";

    @Before
    public void setup() throws IOException {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);

        SensitivityAnalysisInputData sensitivityAnalysisInputData = SensitivityAnalysisInputData.builder()
            .resultsThreshold(0.20)
            .sensitivityInjectionsSets(List.of(SensitivityAnalysisInputData.SensitivityInjectionsSet.builder()
                .monitoredBranches(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name1")))
                .injections(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name2"), new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name3")))
                .distributionType(SensitivityAnalysisInputData.DistributionType.REGULAR)
                .contingencies(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name4"))).build()))
            .sensitivityInjections(List.of(SensitivityAnalysisInputData.SensitivityInjection.builder()
                .monitoredBranches(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name5")))
                .injections(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name6")))
                .contingencies(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name7"), new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name8"))).build()))
            .sensitivityHVDCs(List.of(SensitivityAnalysisInputData.SensitivityHVDC.builder()
                .monitoredBranches(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name9")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_MW)
                .hvdcs(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name10")))
                .contingencies(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name11"))).build()))
            .sensitivityPSTs(List.of(SensitivityAnalysisInputData.SensitivityPST.builder()
                .monitoredBranches(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name12")))
                .sensitivityType(SensitivityAnalysisInputData.SensitivityType.DELTA_A)
                .psts(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name13"), new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name14")))
                .contingencies(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name15"))).build()))
            .sensitivityNodes(List.of(SensitivityAnalysisInputData.SensitivityNodes.builder()
                .monitoredVoltageLevels(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name16")))
                .equipmentsInVoltageRegulation(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name17")))
                .contingencies(List.of(new SensitivityAnalysisInputData.Ident(UUID.randomUUID(), "name18"))).build()))
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
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/status")
                           || path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_RESULT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_RESULT_UUID)
                           || path.matches("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
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
            .contentType(MediaType.APPLICATION_JSON)
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
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/result", studyUuid, nodeUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/results/%s", resultUuid)));

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
            .contentType(MediaType.APPLICATION_JSON)
            .content(SENSITIVITY_INPUT))
            .andExpect(status().isForbidden());

        testSensitivityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        testSensitivityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID));
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
            .contentType(MediaType.APPLICATION_JSON)
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

        /**
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID);
        UUID studyUuid2 = studyEntity.getId();
        UUID rootNodeUuid2 = getRootNodeUuid(studyUuid2);
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid2, modificationNode1Uuid2)
            .contentType(MediaType.APPLICATION_JSON)
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
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
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
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
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
}
