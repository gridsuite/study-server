/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NonEvacuatedEnergyService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StateEstimationService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StateEstimationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateEstimationTest.class);

    private static final String STATE_ESTIMATION_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/";

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String STATE_ESTIMATION_RESULT_UUID = "cf203721-6150-4203-8960-d61d815a9d16";
    private static final String STATE_ESTIMATION_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f4");

    private static final String STATE_ESTIMATION_PARAMETERS_UUID_STRING = "0c4de6e2-3851-4858-a60e-edba3db2da46";
    private static final UUID STATE_ESTIMATION_PARAMETERS_UUID = UUID.fromString(STATE_ESTIMATION_PARAMETERS_UUID_STRING);
    private static final String WRONG_STATE_ESTIMATION_PARAMETERS_UUID_STRING = "72c9d1ef-d8ab-4388-a6fe-855604c2dc10";

    private static final String ESTIM_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";

    private static final long TIMEOUT = 1000;

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ESTIM_RESULT_JSON_DESTINATION = "stateestimation.result";
    private static final String ESTIM_STOPPED_DESTINATION = "stateestimation.stopped";
    private static final String ESTIM_FAILED_DESTINATION = "stateestimation.run.dlx";
    private static final String ESTIM_PARAMETERS_FILE = "/estim-parameters.json";

    @Autowired
    private MockMvc mockMvc;
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
    private StateEstimationService stateEstimationService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private StudyService studyService;
    @Autowired
    private TestUtils studyTestUtils;

    @AllArgsConstructor
    private static class StudyNodeIds {
        UUID studyId;
        UUID rootNetworkUuid;
        UUID nodeId;
    }

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        String estimResultUuidStr = objectMapper.writeValueAsString(STATE_ESTIMATION_RESULT_UUID);
        String estimErrorResultUuidStr = objectMapper.writeValueAsString(STATE_ESTIMATION_ERROR_RESULT_UUID);
        String estimResultJson = TestUtils.resourceToString("/estim-result.json");

        String estimParametersJson = TestUtils.resourceToString(ESTIM_PARAMETERS_FILE);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = Objects.requireNonNull(request.getMethod());

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID + "&receiver=.*")) {
                    // estim with success
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", STATE_ESTIMATION_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), ESTIM_RESULT_JSON_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), estimResultUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID_2 + "&receiver=.*")) {
                    // estim with failure
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), ESTIM_FAILED_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), estimErrorResultUuidStr);
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), estimResultJson);
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), ESTIM_STATUS_JSON);
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/stop.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", STATE_ESTIMATION_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), ESTIM_STOPPED_DESTINATION);
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/results")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters/" + WRONG_STATE_ESTIMATION_PARAMETERS_UUID_STRING)) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(STATE_ESTIMATION_ERROR_RESULT_UUID));
                } else if (path.matches("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID)) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), estimParametersJson);
                    } else {
                        //Method PUT
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(STATE_ESTIMATION_PARAMETERS_UUID));
                    }
                } else if (path.matches("/v1/parameters") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(STATE_ESTIMATION_PARAMETERS_UUID));
                } else if (path.matches("/v1/parameters/default") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(STATE_ESTIMATION_PARAMETERS_UUID));
                } else {
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    private StudyNodeIds createStudyAndNode(String variantId, String nodeName, UUID stateEstimationParametersUuid) throws Exception {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        // create a study
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.fromString(NETWORK_UUID_STRING), "netId", CASE_LOADFLOW_UUID, "", "", null,
                LOADFLOW_PARAMETERS_UUID, null, null, null,
                defaultNonEvacuatedEnergyParametersEntity, stateEstimationParametersUuid);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        // with a node
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode node = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), variantId, nodeName);
        return new StudyNodeIds(studyUuid, firstRootNetworkUuid, node.getId());
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private void runEstim(final MockWebServer server, StudyNodeIds ids) throws Exception {
        mockMvc.perform(post(STATE_ESTIMATION_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_RESULT);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID + "&receiver=.*")));
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

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, ESTIM_RESULT_JSON_DESTINATION, ESTIM_STOPPED_DESTINATION, ESTIM_FAILED_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);
        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    @Test
    void testComputation(final MockWebServer server) throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", null);
        runEstim(server, ids);

        // get estim result
        MvcResult mvcResult = mockMvc.perform(get(STATE_ESTIMATION_URL_BASE + "result", ids.studyId, ids.rootNetworkUuid, ids.nodeId)).andExpectAll(
                status().isOk()).andReturn();
        assertEquals(TestUtils.resourceToString("/estim-result.json"), mvcResult.getResponse().getContentAsString());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID)));

        // get estim status
        mockMvc.perform(get(STATE_ESTIMATION_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId)).andExpectAll(
                status().isOk(),
                content().string(ESTIM_STATUS_JSON));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/status")));
    }

    @Test
    void testResultsDeletion(final MockWebServer server) throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", null);
        runEstim(server, ids);

        // we have one Estim result
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByStateEstimationResultUuidNotNull().size());

        // supervision deletion result, with dry-mode (only count)
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", STATE_ESTIMATION.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        // supervision deletion result, without dry-mode
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", STATE_ESTIMATION.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk());
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        // no more result
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
    }

    @Test
    void testStop(final MockWebServer server) throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", null);
        runEstim(server, ids);

        // stop running estim
        mockMvc.perform(put(STATE_ESTIMATION_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId)).andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_RESULT);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    void testFailure(final MockWebServer server) throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2", null);

        mockMvc.perform(post(STATE_ESTIMATION_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_FAILED);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID_2 + "&receiver=.*")));
    }

    @Test
    void testStateEstimationParameters(final MockWebServer server) throws Exception {
        String estimParametersJson = TestUtils.resourceToString(ESTIM_PARAMETERS_FILE);

        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", null);
        createOrUpdateParametersAndDoChecks(ids.studyId, estimParametersJson, "userId", HttpStatus.OK);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters")));

        //insert a study
        StudyNodeIds ids2 = createStudyAndNode(VARIANT_ID, "node 2", null);

        //get initial state estim parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/state-estimation/parameters", ids2.studyId)).andExpectAll(
            status().isOk()).andReturn();
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/default")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID_STRING)));

        assertEquals(estimParametersJson, mvcResult.getResponse().getContentAsString());

        createOrUpdateParametersAndDoChecks(ids2.studyId, estimParametersJson, "userId", HttpStatus.OK);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID_STRING)));

        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/state-estimation/parameters", ids2.studyId)).andExpectAll(
            status().isOk()).andReturn();

        assertEquals(estimParametersJson, mvcResult.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID_STRING)));

        //update voltage init parameters
        createOrUpdateParametersAndDoChecks(ids2.studyId, estimParametersJson, "userId", HttpStatus.OK);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + STATE_ESTIMATION_PARAMETERS_UUID_STRING)));

        // insert a study with a wrong voltage init parameters uuid
        StudyNodeIds ids3 = createStudyAndNode(VARIANT_ID, "node 3", UUID.fromString(WRONG_STATE_ESTIMATION_PARAMETERS_UUID_STRING));

        // get voltage init parameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/state-estimation/parameters", ids3.studyId)).andExpect(
            status().isNotFound());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + WRONG_STATE_ESTIMATION_PARAMETERS_UUID_STRING)));
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/state-estimation/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        Message<byte[]> stateEstimationStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, stateEstimationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS, stateEstimationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }
}
