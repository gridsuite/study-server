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
import com.github.tomakehurst.wiremock.client.WireMock;
import lombok.AllArgsConstructor;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.voltageinit.parameters.FilterEquipments;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.ResultParameters;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.web.client.HttpClientErrorException;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class PccMinTest {

    private static final String PCC_MIN_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/pcc-min/";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString("11a91c11-2c2d-83bb-b45f-20b83e4ef00c");
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String PCC_MIN_RESULT_UUID = "cf203721-6150-4203-8960-d61d815a9d16";
    private static final String PCC_MIN_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final UUID PCCMIN_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f2");
    private static final String PCC_MIN_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final long TIMEOUT = 1000;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String PCC_MIN_RESULT_JSON_DESTINATION = "pccmin.result";
    private static final String PCC_MIN_STOPPED_DESTINATION = "pccmin.stopped";
    private static final String PCC_MIN_FAILED_DESTINATION = "pccmin.run.dlx";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoSpyBean
    private PccMinService pccMinService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private UserAdminService userAdminService;
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
    private WireMockUtils wireMockUtils;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        wireMockUtils = new WireMockUtils(wireMockServer);
        configureFor("localhost", wireMockServer.port());
        String baseUrl = wireMockServer.baseUrl();

        pccMinService.setPccMinServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

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

    @AllArgsConstructor
    private static final class StudyNodeIds {
        UUID studyId;
        UUID rootNetworkUuid;
        UUID nodeId;
    }

    private StudyNodeIds createStudyAndNode(String variantId, String nodeName, UUID pccMinParametersUuid) throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(UUID.fromString(NETWORK_UUID_STRING),
            "netId", CASE_LOADFLOW_UUID, "", "", null, null, null, null, null, null, pccMinParametersUuid);
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

        Message<?> msg = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(msg);
        modificationNode.setId(UUID.fromString(String.valueOf(msg.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), msg.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(),
            studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    private void checkPccMinMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(updateType, updateTypeToCheck);
    }

    private void consumePccMinResult(StudyNodeIds ids, String resultUuid) throws JsonProcessingException {
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        MessageHeaders headers = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumePccMinResult().accept(MessageBuilder.createMessage("", headers));

        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_RESULT);

        wireMockServer.verify(postRequestedFor(urlPathMatching(
            "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .withQueryParam("variantId", equalTo(VARIANT_ID)));
    }

    private void runPccMin(StudyNodeIds ids) throws Exception {
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
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1", PCCMIN_PARAMETERS_UUID);

        // Run Pcc min
        UUID stubRun = wireMockUtils.stubPccMinRun(NETWORK_UUID_STRING, VARIANT_ID, PCC_MIN_RESULT_UUID);
        mockMvc.perform(post(PCC_MIN_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumePccMinResult(ids, PCC_MIN_RESULT_UUID);
        wireMockUtils.verifyPccMinRun(stubRun, NETWORK_UUID_STRING, VARIANT_ID);

        // verify pcc min status
        UUID stubStatus = wireMockUtils.stubPccMinStatus(PCC_MIN_RESULT_UUID, PCC_MIN_STATUS_JSON);
        mockMvc.perform(get(PCC_MIN_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(PCC_MIN_STATUS_JSON));

        wireMockUtils.verifyPccMinStatus(stubStatus, PCC_MIN_RESULT_UUID);
    }

    @Test
    void testStop() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 2", PCCMIN_PARAMETERS_UUID);
        runPccMin(ids);

        UUID stubId = wireMockServer.stubFor(
            put(urlPathMatching("/v1/results/" + PCC_MIN_RESULT_UUID + "/stop.*"))
                .willReturn(ok())
        ).getId();

        // stop pcc min
        mockMvc.perform(put(PCC_MIN_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk());

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> stoppedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", PCC_MIN_RESULT_UUID)
            .build();
        consumerService.consumePccMinStopped().accept(stoppedMessage);
        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        wireMockUtils.verifyPccMinStop(stubId, PCC_MIN_RESULT_UUID);
    }

    @Test
    void testFailure() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2", PCCMIN_PARAMETERS_UUID);
        UUID stubFail = wireMockUtils.stubPccMinFailed(NETWORK_UUID_STRING, VARIANT_ID_2, PCC_MIN_ERROR_RESULT_UUID);

        mockMvc.perform(post(PCC_MIN_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        // pcc min failed
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, resultUuidJson)
            .setHeader("resultUuid", PCC_MIN_ERROR_RESULT_UUID)
            .build();
        consumerService.consumePccMinFailed().accept(failedMessage);

        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        checkPccMinMessagesReceived(ids.studyId, NotificationService.UPDATE_TYPE_PCC_MIN_FAILED);

        wireMockUtils.verifyPccMinFail(stubFail, NETWORK_UUID_STRING, VARIANT_ID_2);
    }

    @Test
    void testResultsDeletion() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", PCCMIN_PARAMETERS_UUID);
        runPccMin(ids);

        assertEquals(1, rootNetworkNodeInfoRepository.findAllByPccMinResultUuidNotNull().size());

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/supervision/results-count"))
            .willReturn(okJson("1")));

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(WireMock.ok().withBody(PCC_MIN_RESULT_UUID)));

        wireMockServer.stubFor(delete(urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(ok()));

        Integer dryRunCount = supervisionService.deleteComputationResults(ComputationType.PCC_MIN, true);
        assertEquals(1, dryRunCount);
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v1/supervision/results-count")));

        Integer deletedCount = supervisionService.deleteComputationResults(ComputationType.PCC_MIN, false);
        assertEquals(1, deletedCount);

        wireMockServer.verify(1, deleteRequestedFor(urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", matching(".*")));

        assertEquals(0, rootNetworkNodeInfoRepository.findAllByPccMinResultUuidNotNull().size());
    }

    @Test
    void testGetPccMinResults() throws Exception {
        // --- create study and node ---
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", PCCMIN_PARAMETERS_UUID);
        runPccMin(ids);

        //get pages, sorted and filtered results
        UUID stubId = wireMockUtils.stubPagedPccMinResult(PCC_MIN_RESULT_UUID, TestUtils.resourceToString("/pccmin-result-paged.json"));
        mockMvc.perform(get(PCC_MIN_URL_BASE + "result", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .param("page", "0")
                .param("size", "20")
                .param("sort", "id,DESC")
                .param("filters", "fakeFilters")
                .param("globalFilters", "fakeGlobalFilters"))
            .andExpect(status().isOk())
            .andExpect(content().string(TestUtils.resourceToString("/pccmin-result-paged.json")));

        wireMockUtils.verifyPccMinPagedGet(stubId, PCC_MIN_RESULT_UUID);

        UUID resultUuid = UUID.randomUUID();
        ResultParameters params = new ResultParameters(UUID.randomUUID(), UUID.randomUUID(), "variantId", UUID.randomUUID(), resultUuid);

        // results NOT FOUND
        wireMockServer.stubFor(
            WireMock.get("/v1/pcc-min/results/" + resultUuid)
                .willReturn(WireMock.notFound())
        );
        PageRequest pageRequest = PageRequest.of(0, 20);
        assertThrows(HttpClientErrorException.NotFound.class, () ->
            pccMinService.getPccMinResultsPage(params, null, null, pageRequest)
        );

        // no content
        ResultParameters params2 = new ResultParameters(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "variantId",
            null,
            null
        );
        String result = pccMinService.getPccMinResultsPage(params2, null, null, PageRequest.of(0, 20));
        assertNull(result);
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching("/v1/pcc-min/results/.*")));
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/pcc-min/parameters", studyNameUserIdUuid)
                    .header("userId", "userId")
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_PCC_MIN_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID pccMinParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, UUID.randomUUID(), null, null, null, null, pccMinParametersUuid);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private String buildFilter() throws JsonProcessingException {
        return objectMapper.writeValueAsString(
            List.of(new FilterEquipments(UUID.randomUUID(), "updatedFilter"))
        );
    }

    @Test
    void testGetPccMinParameters() throws Exception {
        String parametersToCreate = buildFilter();
        UUID stubId = wireMockUtils.stubPccMinParametersGet(
            String.valueOf(PCCMIN_PARAMETERS_UUID),
            parametersToCreate
        );

        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), PCCMIN_PARAMETERS_UUID).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/pcc-min/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(parametersToCreate));

        wireMockUtils.verifyPccMinParametersGet(stubId, String.valueOf(PCCMIN_PARAMETERS_UUID));

        // Not found case
        UUID wrongParamUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.get("v1/parameters/" + wrongParamUuid)
            .willReturn(WireMock.notFound()));

        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> pccMinService.getPccMinParameters(wrongParamUuid)
        );
    }

    @Test
    void testSetPccMinParameters() throws Exception {
        String parameterToUpdate = buildFilter();

        wireMockServer.stubFor(put(urlPathEqualTo("/v1/parameters/" + PCCMIN_PARAMETERS_UUID))
            .willReturn(ok()));
        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), PCCMIN_PARAMETERS_UUID).getId();

        createOrUpdateParametersAndDoChecks(studyUuid, parameterToUpdate, HttpStatus.OK);
        wireMockServer.verify(putRequestedFor(urlPathEqualTo("/v1/parameters/" + PCCMIN_PARAMETERS_UUID)));

        // Fail case
        UUID wrongParamUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.put("v1/parameters/" + wrongParamUuid)
            .willReturn(WireMock.notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> pccMinService.updatePccMinParameters(wrongParamUuid, "parameterToUpdate")
        );
    }

    @Test
    void testCreatePccMinParameters() {
        String parameterToCreate = "\"fakeParamsToCreate\"";

        UUID expectedUuid = UUID.randomUUID();
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters"))
            .willReturn(okJson("\"" + expectedUuid + "\"")));

        UUID paramUuid = pccMinService.createPccMinParameters(parameterToCreate);

        assertEquals(expectedUuid, paramUuid);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/parameters"))
            .withRequestBody(equalToJson(parameterToCreate)));

        //failure
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters"))
            .willReturn(notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> pccMinService.createPccMinParameters(parameterToCreate)
        );
    }

    @Test
    void testDefaultParameters() throws Exception {
        String params = buildFilter();
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters/default"))
            .willReturn(okJson(objectMapper.writeValueAsString(PCCMIN_PARAMETERS_UUID))));

        UUID stubId = wireMockUtils.stubPccMinParametersGet(
            String.valueOf(PCCMIN_PARAMETERS_UUID),
            params
        );

        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/pcc-min/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(params));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/parameters/default")));
        wireMockUtils.verifyPccMinParametersGet(stubId, String.valueOf(PCCMIN_PARAMETERS_UUID));

        assertNotNull(studyUuid);
        assertEquals(PCCMIN_PARAMETERS_UUID,
            studyRepository.findById(studyUuid).orElseThrow().getPccMinParametersUuid());

        // Fail case
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters/default"))
            .willReturn(WireMock.notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> pccMinService.createDefaultPccMinParameters()
        );
    }
}
