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
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class StateEstimationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateEstimationTest.class);

    private static final String STATE_ESTIMATION_URL_BASE = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/state-estimation/";

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String STATE_ESTIMATION_RESULT_UUID = "cf203721-6150-4203-8960-d61d815a9d16";
    private static final String STATE_ESTIMATION_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f4");

    private static final String ESTIM_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";

    private static final long TIMEOUT = 1000;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String estimResultJsonDestination = "stateestimation.result";
    private final String estimStoppedDestination = "stateestimation.stopped";
    private final String estimFailedDestination = "stateestimation.failed";

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

    private class StudyNodeIds {
        StudyNodeIds(UUID sId, UUID nId) {
            studyId = sId;
            nodeId = nId;
        }

        UUID studyId;
        UUID nodeId;
    }

    @Before
    public void setup() throws IOException {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();
        server.start();
        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        String estimResultUuidStr = objectMapper.writeValueAsString(STATE_ESTIMATION_RESULT_UUID);
        String estimErrorResultUuidStr = objectMapper.writeValueAsString(STATE_ESTIMATION_ERROR_RESULT_UUID);
        String estimResultJson = TestUtils.resourceToString("/estim-result.json");

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID + "&receiver=.*")) {
                    // estim with success
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", STATE_ESTIMATION_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), estimResultJsonDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(estimResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID_2 + "&receiver=.*")) {
                    // estim with failure
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), estimFailedDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(estimErrorResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).setBody(estimResultJson)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(ESTIM_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/stop.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", STATE_ESTIMATION_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), estimStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("1");
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results")) {
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
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

    private StudyNodeIds createStudyAndNode(String variantId, String nodeName) throws Exception {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        // create a study
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.fromString(NETWORK_UUID_STRING), "netId", CASE_LOADFLOW_UUID, "", "", null,
                LOADFLOW_PARAMETERS_UUID, null, null, null,
                defaultNonEvacuatedEnergyParametersEntity);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        // with a node
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode node = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), variantId, nodeName);
        return new StudyNodeIds(studyUuid, node.getId());
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private void runEstim(StudyNodeIds ids) throws Exception {
        mockMvc.perform(post(STATE_ESTIMATION_URL_BASE + "run", ids.studyId, ids.nodeId)
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
        List<String> destinations = List.of(studyUpdateDestination, estimResultJsonDestination, estimStoppedDestination, estimFailedDestination);
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
    @SneakyThrows
    public void testComputation() {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runEstim(ids);

        // get estim result
        MvcResult mvcResult = mockMvc.perform(get(STATE_ESTIMATION_URL_BASE + "result", ids.studyId, ids.nodeId)).andExpectAll(
                status().isOk()).andReturn();
        assertEquals(TestUtils.resourceToString("/estim-result.json"), mvcResult.getResponse().getContentAsString());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID)));

        // get estim status
        mockMvc.perform(get(STATE_ESTIMATION_URL_BASE + "status", ids.studyId, ids.nodeId)).andExpectAll(
                status().isOk(),
                content().string(ESTIM_STATUS_JSON));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/status")));
    }

    @Test
    @SneakyThrows
    public void testResultsDeletion() {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runEstim(ids);

        // we have one Estim result
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByStateEstimationResultUuidNotNull().size());

        // supervision deletion result, with dry-mode (only count)
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", String.valueOf(STATE_ESTIMATION))
                        .queryParam("dryRun", String.valueOf(true)))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        // supervision deletion result, without dry-mode
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", String.valueOf(STATE_ESTIMATION))
                        .queryParam("dryRun", String.valueOf(false)))
                .andExpect(status().isOk());
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        // no more result
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
    }

    @Test
    @SneakyThrows
    public void testStop() {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1");
        runEstim(ids);

        // stop running estim
        mockMvc.perform(put(STATE_ESTIMATION_URL_BASE + "stop", ids.studyId, ids.nodeId)).andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_RESULT);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    @SneakyThrows
    public void testFailure() {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2");

        mockMvc.perform(post(STATE_ESTIMATION_URL_BASE + "run", ids.studyId, ids.nodeId)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_FAILED);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?reportUuid=.*&reporterId=.*&reportType=StateEstimation&variantId=" + VARIANT_ID_2 + "&receiver=.*")));
    }
}
