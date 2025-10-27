/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.client.WireMock;
import lombok.AllArgsConstructor;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class PccMinTest {

    // ==================== Constants ====================
    private static final String PCC_MIN_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString("11a91c11-2c2d-83bb-b45f-20b83e4ef00c");
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String PCC_MIN_RESULT_UUID = "cf203721-6150-4203-8960-d61d815a9d16";
    private static final String PCC_MIN_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final UUID SHORTCIRCUIT_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f4");
    private static final String PCC_MIN_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final long TIMEOUT = 1000;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String PCC_MIN_RESULT_JSON_DESTINATION = "pccmin.result";
    private static final String PCC_MIN_STOPPED_DESTINATION = "pccmin.stopped";
    private static final String PCC_MIN_FAILED_DESTINATION = "pccmin.run.dlx";

    // ==================== Autowired Beans ====================
    @Autowired private MockMvc mockMvc;
    @Autowired private OutputDestination output;
    @Autowired private InputDestination input;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NetworkModificationTreeService networkModificationTreeService;
    @SpyBean private PccMinService pccMinService;
    @Autowired private StudyRepository studyRepository;
    @Autowired private UserAdminService userAdminService;
    @Autowired private ReportService reportService;
    @Autowired private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired private TestUtils studyTestUtils;
    @Autowired private ConsumerService consumerService;
    @Autowired private Consumer<Message<String>> consumePccMinFailed;

    private ObjectWriter objectWriter;
    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;

    @BeforeEach
    void setup() throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        wireMockUtils = new WireMockUtils(wireMockServer);
        configureFor("localhost", wireMockServer.port());
        String baseUrl = wireMockServer.baseUrl();

        pccMinService.setPccMinServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        stubCommon();
    }

    private void stubCommon() {
        wireMockServer.stubFor(get(urlPathMatching("/v1/results/" + PCC_MIN_RESULT_UUID + "/status"))
            .willReturn(okJson(PCC_MIN_STATUS_JSON)));
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/reports")).willReturn(ok()));
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/supervision/results-count")).willReturn(okJson("1")));
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        wireMockServer.stop();
        TestUtils.assertQueuesEmptyThenClear(
            List.of(STUDY_UPDATE_DESTINATION, PCC_MIN_RESULT_JSON_DESTINATION, PCC_MIN_STOPPED_DESTINATION, PCC_MIN_FAILED_DESTINATION),
            output
        );
    }

    // ==================== Helper Classes ====================
    @AllArgsConstructor
    private static final class StudyNodeIds {
        UUID studyId;
        UUID rootNetworkUuid;
        UUID nodeId;
    }

    // ==================== Helper Methods ====================
    private StudyNodeIds createStudyAndNode(String variantId, String nodeName) throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.fromString(NETWORK_UUID_STRING),
            "netId", CASE_LOADFLOW_UUID, "", "", null, null, SHORTCIRCUIT_PARAMETERS_UUID, null, null, null);
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

        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid)
                .content(mnBodyJson)
                .contentType(MediaType.APPLICATION_JSON)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        Message<?> msg = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(msg);
        modificationNode.setId(UUID.fromString(String.valueOf(msg.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), msg.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(),
            studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
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

    private void consumePccMinResult(StudyNodeIds ids, String resultUuid) throws JsonProcessingException {
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        MessageHeaders headers = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumePccMinResult().accept(MessageBuilder.createMessage("", headers));

        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_RESULT);

        wireMockServer.verify(postRequestedFor(urlPathMatching(
            "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .withQueryParam("variantId", equalTo(VARIANT_ID)));
    }

    private void runPccMinWithSuccess(StudyNodeIds ids) throws Exception {
        UUID stubId = wireMockServer.stubFor(post(urlPathMatching("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
                .willReturn(okJson(objectMapper.writeValueAsString(PCC_MIN_RESULT_UUID))))
            .getId();

        mockMvc.perform(post(PCC_MIN_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumePccMinResult(ids, PCC_MIN_RESULT_UUID);

        wireMockUtils.verifyPostRequest(
            stubId,
            "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save",
            true,
            Map.of("variantId", WireMock.equalTo(VARIANT_ID)),
            null,
            1
        );
    }

    @Test
    void testRunAndCheckStatus() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1");
        runPccMinWithSuccess(ids);
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByPccMinResultUuidNotNull().size());

        mockMvc.perform(get(PCC_MIN_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk())
            .andExpect(content().string(PCC_MIN_STATUS_JSON));
    }

    @Test
    void testStop() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 2");
        runPccMinWithSuccess(ids);

        UUID stubId = wireMockServer.stubFor(
            put(urlPathMatching("/v1/results/" + PCC_MIN_RESULT_UUID + "/stop.*"))
                .willReturn(ok())
        ).getId();

        mockMvc.perform(put(PCC_MIN_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk());

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> stoppedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", PCC_MIN_RESULT_UUID)
            .build();
        consumerService.consumePccMinStopped().accept(stoppedMessage);

        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);

        wireMockUtils.verifyPutRequest(
            stubId,
            "/v1/results/" + PCC_MIN_RESULT_UUID + "/stop",
            true,
            Map.of(),
            null
        );
    }

    @Test
    void testFailure() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2");

        UUID stubId = wireMockServer.stubFor(post(urlPathMatching("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
                .withQueryParam("variantId", equalTo(VARIANT_ID_2))
                .willReturn(okJson(objectMapper.writeValueAsString(PCC_MIN_ERROR_RESULT_UUID))))
            .getId();

        mockMvc.perform(post(PCC_MIN_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, resultUuidJson)
            .setHeader("resultUuid", PCC_MIN_ERROR_RESULT_UUID)
            .build();
        consumerService.consumePccMinFailed().accept(failedMessage);

        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkUpdateModelStatusMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_FAILED);

        wireMockUtils.verifyPostRequest(
            stubId,
            "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save",
            true,
            Map.of("variantId", WireMock.equalTo(VARIANT_ID_2)),
            null,
            1
        );
    }
}
