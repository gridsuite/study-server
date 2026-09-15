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
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
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
import org.gridsuite.study.server.service.asymmetricalload.AsymmetricalLoadRestService;
import org.gridsuite.study.server.utils.ResultParameters;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.ComputationServerStubs;
import org.gridsuite.study.server.utils.wiremock.UserAdminServerStubs;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class AsymmetricalLoadTest {

    private static final String ASYMMETRICAL_LOAD_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/asymmetrical-load/";
    private static final String COMPUTATION_URL_BASE = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computations/";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String ASYMMETRICAL_LOAD_RESULT_UUID = "cf203721-6150-4203-8960-d61d815a9d16";
    private static final String ASYMMETRICAL_LOAD_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final UUID ASYMMETRICAL_LOAD_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f2");
    private static final String ASYMMETRICAL_LOAD_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String ALL_COMPUTATION_STATUS_JSON = "{\"LOAD_FLOW\":null,\"SECURITY_ANALYSIS\":null," +
            "\"SENSITIVITY_ANALYSIS\":null,\"SHORT_CIRCUIT\":null,\"SHORT_CIRCUIT_ONE_BUS\":null," +
            "\"VOLTAGE_INITIALIZATION\":null,\"DYNAMIC_SIMULATION\":null,\"DYNAMIC_SECURITY_ANALYSIS\":null," +
            "\"DYNAMIC_MARGIN_CALCULATION\":null,\"STATE_ESTIMATION\":null,\"PCC_MIN\":null," +
            "\"ASYMMETRICAL_LOAD\":\"{\\\"status\\\":\\\"COMPLETED\\\"}\"}";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final long TIMEOUT = 1000;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ASYMMETRICAL_LOAD_RESULT_JSON_DESTINATION = "asymmetricalload.result";
    private static final String ASYMMETRICAL_LOAD_STOPPED_DESTINATION = "asymmetricalload.stopped";
    private static final String ASYMMETRICAL_LOAD_FAILED_DESTINATION = "asymmetricalload.run.dlx";
    private static final byte[] ASYMMETRICAL_LOAD_RESULTS_AS_ZIPPED_CSV = {0x00, 0x01};

    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";

    private static final String ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID ASYMMETRICALLOAD_PARAMETERS_UUID = UUID.fromString(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING);
    private static final String ASYMMETRICAL_LOAD_PROFILE_PARAMETERS_JSON =
            "{\"uuid\":\"7cce52fd-2aca-4d93-9b7b-6a2b4c0c2c11\",\"filters\":[{\"filterId\":\"b5fafd19-25f4-45b9-b5c8-3af51fdc9d1c\",\"filterName\":\"filterName\"}]}";

    private static final String PROFILE_ASYMMETRICAL_LOAD_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String PROFILE_ASYMMETRICAL_LOAD_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";

    private static final String USER_PROFILE_VALID_PARAMS_JSON =
            "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid asymmetrical load params\",\"asymmetricalLoadParameterId\":\"" +
            PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON =
            "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken asymmetrical load params\",\"asymmetricalLoadParameterId\":\"" +
            PROFILE_ASYMMETRICAL_LOAD_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_ASYMMETRICAL_LOAD_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String ASYMMETRICAL_LOAD_PREFIX = "asymmetrical-load/";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @MockitoSpyBean
    private AsymmetricalLoadRestService asymmetricalLoadRestService;
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
    private WireMockStubs wireMockStubs;
    private ComputationServerStubs computationServerStubs;
    private UserAdminServerStubs userAdminServerStubs;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        wireMockStubs = new WireMockStubs(wireMockServer);
        computationServerStubs = new ComputationServerStubs(wireMockServer);
        userAdminServerStubs = new UserAdminServerStubs(wireMockServer);
        configureFor("localhost", wireMockServer.port());
        String baseUrl = wireMockServer.baseUrl();

        asymmetricalLoadRestService.setBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        wireMockServer.stop();
        TestUtils.assertQueuesEmptyThenClear(
            List.of(STUDY_UPDATE_DESTINATION, ASYMMETRICAL_LOAD_RESULT_JSON_DESTINATION, ASYMMETRICAL_LOAD_STOPPED_DESTINATION, ASYMMETRICAL_LOAD_FAILED_DESTINATION),
            output
        );
    }

    @AllArgsConstructor
    private static final class StudyNodeIds {
        UUID studyId;
        UUID rootNetworkUuid;
        UUID nodeId;
    }

    private StudyNodeIds createStudyAndNode(String variantId, String nodeName, UUID asymmetricalLoadParametersUuid) throws Exception {
        StudyEntity studyEntity = TestUtils.CreateDummyStudyBuilder.builder()
                .setNetworkUuid(UUID.fromString(NETWORK_UUID_STRING)).setNetworkId("netId")
                .setCaseUuid(CASE_UUID).setCaseFormat("").setCaseName("")
                .setLoadFlowParametersUuid(UUID.randomUUID())
                .setAsymmetricalLoadParametersUuid(asymmetricalLoadParametersUuid)
                .build();
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

        Message<?> msg = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertNotNull(msg);
        modificationNode.setId(UUID.fromString(String.valueOf(msg.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), msg.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(),
            studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    private void checkAsymmetricalLoadMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        Message<byte[]> message = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(updateType, updateTypeToCheck);
    }

    private void consumeAsymmetricalLoadResult(StudyNodeIds ids, String resultUuid) throws JsonProcessingException {
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        MessageHeaders headers = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeAsymmetricalLoadResult().accept(MessageBuilder.createMessage("", headers));

        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_RESULT);

        wireMockServer.verify(postRequestedFor(urlPathMatching(
            "/v1/asymmetrical-load/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .withQueryParam("variantId", equalTo(VARIANT_ID)));
    }

    private void runAsymmetricalLoad(StudyNodeIds ids) throws Exception {
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, ASYMMETRICAL_LOAD_RESULT_UUID, ASYMMETRICAL_LOAD_PREFIX);

        mockMvc.perform(post(ASYMMETRICAL_LOAD_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumeAsymmetricalLoadResult(ids, ASYMMETRICAL_LOAD_RESULT_UUID);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/asymmetrical-load/networks/" + NETWORK_UUID_STRING + "/run-and-save",
            true, Map.of("variantId", WireMock.equalTo(VARIANT_ID)), null, 1);
    }

    @Test
    void testRunAndCheckStatus() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1", ASYMMETRICAL_LOAD_PARAMETERS_UUID);

        // Run Asymmetrical load
        UUID stubRun = wireMockStubs.stubAsymmetricalLoadRun(NETWORK_UUID_STRING, VARIANT_ID, ASYMMETRICAL_LOAD_RESULT_UUID);
        mockMvc.perform(post(ASYMMETRICAL_LOAD_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumeAsymmetricalLoadResult(ids, ASYMMETRICAL_LOAD_RESULT_UUID);
        wireMockStubs.verifyAsymmetricalLoadRun(stubRun, NETWORK_UUID_STRING, VARIANT_ID);

        // verify asymmetrical load status
        computationServerStubs.stubGetResultStatus(ASYMMETRICAL_LOAD_RESULT_UUID, ASYMMETRICAL_LOAD_STATUS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        mockMvc.perform(get(ASYMMETRICAL_LOAD_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpectAll(status().isOk(), content().string(ASYMMETRICAL_LOAD_STATUS_JSON));

        computationServerStubs.verifyGetResultStatus(ASYMMETRICAL_LOAD_RESULT_UUID, 1, ASYMMETRICAL_LOAD_PREFIX);

        mockMvc.perform(get(COMPUTATION_URL_BASE + "status", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpectAll(status().isOk(), content().string(ALL_COMPUTATION_STATUS_JSON));

        computationServerStubs.verifyGetResultStatus(ASYMMETRICAL_LOAD_RESULT_UUID, 1, ASYMMETRICAL_LOAD_PREFIX);
    }

    @Test
    void testStop() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 2", ASYMMETRICAL_LOAD_PARAMETERS_UUID);
        runAsymmetricalLoad(ids);

        wireMockServer.stubFor(put(urlPathMatching("/v1/asymmetrical-load/results/" + ASYMMETRICAL_LOAD_RESULT_UUID + "/stop.*"))
            .willReturn(ok()));

        // stop asymmetrical load
        mockMvc.perform(put(ASYMMETRICAL_LOAD_URL_BASE + "stop", ids.studyId, ids.rootNetworkUuid, ids.nodeId))
            .andExpect(status().isOk());

        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> stoppedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", ASYMMETRICAL_LOAD_RESULT_UUID)
            .build();
        consumerService.consumeAsymmetricalLoadStopped().accept(stoppedMessage);
        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        computationServerStubs.verifyComputationStop(ASYMMETRICAL_LOAD_RESULT_UUID, Map.of("receiver", WireMock.matching(".*")), ASYMMETRICAL_LOAD_PREFIX);
    }

    @Test
    void testFailure() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID_2, "node 2", ASYMMETRICAL_LOAD_PARAMETERS_UUID);
        UUID stubFail = wireMockStubs.stubAsymmetricalLoadFailed(NETWORK_UUID_STRING, VARIANT_ID_2, ASYMMETRICAL_LOAD_ERROR_RESULT_UUID);

        mockMvc.perform(post(ASYMMETRICAL_LOAD_URL_BASE + "run", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        // asymmetrical load failed
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(ids.nodeId, ids.rootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, resultUuidJson)
            .setHeader("resultUuid", ASYMMETRICAL_LOAD_ERROR_RESULT_UUID)
            .build();
        consumerService.consumeAsymmetricalLoadFailed().accept(failedMessage);

        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS);
        checkAsymmetricalLoadMessagesReceived(ids.studyId, UPDATE_TYPE_ASYMMETRICAL_LOAD_FAILED);

        wireMockStubs.verifyAsymmetricalLoadFail(stubFail, NETWORK_UUID_STRING, VARIANT_ID_2);
    }

    @Test
    void testResetAsymmetricalLoadParametersUserHasValidParamsInProfileButNoExistingAsymmetricalLoadParams() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/asymmetrical-load/parameters/" +
                PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING + "/duplicate"))
            .willReturn(ok()
                .withBody(DUPLICATED_PARAMS_JSON)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        createOrUpdateParametersAndDoChecks(studyUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        wireMockServer.verify(postRequestedFor(
                urlPathEqualTo("/v1/asymmetrical-load/parameters/" + PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING + "/duplicate"))
        );
    }

    @Test
    void testResetAsymmetricalLoadParametersUserHasNoProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), UUID.randomUUID(), ASYMMETRICALLOAD_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PROFILE_PARAMETERS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);
        computationServerStubs.verifyParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PREFIX);
    }

    @Test
    void testResetAsymmetricalLoadParametersUserHasNoParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, ASYMMETRICALLOAD_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PROFILE_PARAMETERS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        createOrUpdateParametersAndDoChecks(studyUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        userAdminServerStubs.verifyGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PREFIX);
    }

    @Test
    void testResetAsymmetricalLoadParametersUserHasInvalidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, ASYMMETRICALLOAD_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PROFILE_PARAMETERS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        computationServerStubs.stubParametersDuplicateFromNotFound(PROFILE_ASYMMETRICAL_LOAD_INVALID_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PREFIX);
        createOrUpdateParametersAndDoChecks(studyUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        // --- Verify WireMock requests ---
        userAdminServerStubs.verifyGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PREFIX);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_ASYMMETRICAL_LOAD_INVALID_PARAMETERS_UUID_STRING, 1, ASYMMETRICAL_LOAD_PREFIX);
    }

    @Test
    void testResetAsymmetricalLoadParametersUserHasValidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, ASYMMETRICALLOAD_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        wireMockServer.stubFor(post(urlPathMatching("/v1/asymmetrical-load/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .willReturn(ok()));
        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(ASYMMETRICAL_LOAD_PARAMETERS_UUID_STRING, ASYMMETRICAL_LOAD_PROFILE_PARAMETERS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING, DUPLICATED_PARAMS_JSON, ASYMMETRICAL_LOAD_PREFIX);
        wireMockServer.stubFor(put(urlPathMatching("/v1/asymmetrical-load/results/invalidate-status.*"))
            .withQueryParam("resultUuid", matching(".*"))
            .willReturn(ok()));
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, ASYMMETRICAL_LOAD_RESULT_UUID, ASYMMETRICAL_LOAD_PREFIX);
        mockMvc.perform(post(ASYMMETRICAL_LOAD_URL_BASE + "run", studyUuid, firstRootNetworkUuid, modificationNode1.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        Message<byte[]> message = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));

        createOrUpdateParametersAndDoChecks(studyUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);
        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyComputationRun(NETWORK_UUID_STRING, Map.of("reportUuid", matching(".*")), ASYMMETRICAL_LOAD_PREFIX);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_ASYMMETRICAL_LOAD_VALID_PARAMETERS_UUID_STRING, 1, ASYMMETRICAL_LOAD_PREFIX);
        List<ServeEvent> invalidateCalls = wireMockServer.getAllServeEvents().stream()
            .filter(e -> e.getRequest().getUrl().startsWith("/v1/asymmetrical-load/results/invalidate-status"))
            .toList();
        assertTrue(invalidateCalls.size() <= 1);
    }

    @Test
    void testResultsDeletion() throws Exception {
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", ASYMMETRICAL_LOAD_PARAMETERS_UUID);
        runAsymmetricalLoad(ids);

        assertEquals(1, rootNetworkNodeInfoRepository.findAllByAsymmetricalLoadResultUuidNotNull().size());

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/supervision/asymmetrical-load/results-count"))
            .willReturn(okJson("1")));

        wireMockServer.stubFor(get(urlPathEqualTo("/v1/asymmetrical-load/results"))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(WireMock.ok().withBody(ASYMMETRICAL_LOAD_RESULT_UUID)));

        wireMockServer.stubFor(delete(urlPathEqualTo("/v1/asymmetrical-load/results"))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(ok()));

        Integer dryRunCount = supervisionService.deleteComputationResults(ComputationType.ASYMMETRICAL_LOAD, true);
        assertEquals(1, dryRunCount);
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/v1/supervision/asymmetrical-load/results-count")));

        Integer deletedCount = supervisionService.deleteComputationResults(ComputationType.ASYMMETRICAL_LOAD, false);
        assertEquals(1, deletedCount);

        wireMockServer.verify(1, deleteRequestedFor(urlPathEqualTo("/v1/asymmetrical-load/results"))
            .withQueryParam("resultsUuids", matching(".*")));

        assertEquals(0, rootNetworkNodeInfoRepository.findAllByAsymmetricalLoadResultUuidNotNull().size());
    }

    @Test
    void testGetAsymmetricalLoadResults() throws Exception {
        // --- create study and node ---
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node 1", ASYMMETRICAL_LOAD_PARAMETERS_UUID);
        runAsymmetricalLoad(ids);

        //get pages, sorted and filtered results
        UUID stubId = wireMockStubs.stubPagedAsymmetricalLoadResult(ASYMMETRICAL_LOAD_RESULT_UUID, TestUtils.resourceToString("/asymmetricalload-result-paged.json"));
        mockMvc.perform(get(ASYMMETRICAL_LOAD_URL_BASE + "result", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .param("page", "0")
                .param("size", "20")
                .param("sort", "id,DESC")
                .param("filters", "fakeFilters")
                .param("globalFilters", "fakeGlobalFilters"))
            .andExpect(status().isOk())
            .andExpect(content().string(TestUtils.resourceToString("/asymmetricalload-result-paged.json")));

        wireMockStubs.verifyAsymmetricalLoadPagedGet(stubId, ASYMMETRICAL_LOAD_RESULT_UUID);

        UUID resultUuid = UUID.randomUUID();
        ResultParameters params = new ResultParameters(UUID.randomUUID(), UUID.randomUUID(), "variantId", UUID.randomUUID(), resultUuid);

        // results NOT FOUND
        wireMockServer.stubFor(
            WireMock.get("/v1/asymmetrical-load/results/" + resultUuid)
                .willReturn(WireMock.notFound())
        );
        PageRequest pageRequest = PageRequest.of(0, 20);
        assertThrows(HttpClientErrorException.NotFound.class, () ->
            asymmetricalLoadRestService.getAsymmetricalLoadResultsPage(params, null, null, pageRequest)
        );
        wireMockServer.resetRequests();

        // no content
        ResultParameters params2 = new ResultParameters(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "variantId",
            null,
            null
        );
        String result = asymmetricalLoadRestService.getAsymmetricalLoadResultsPage(params2, null, null, PageRequest.of(0, 20));
        assertNull(result);
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching("/v1/asymmetrical-load/results/.*")));
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/asymmetrical-load/parameters", studyUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        Message<byte[]> message = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_ASYMMETRICAL_LOAD_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID asymmetricalLoadParametersUuid) {
        StudyEntity studyEntity = TestUtils.CreateDummyStudyBuilder.builder()
                .setNetworkUuid(networkUuid).setNetworkId("netId")
                .setCaseUuid(caseUuid).setCaseFormat("").setCaseName("")
                .setLoadFlowParametersUuid(UUID.randomUUID())
                .setAsymmetricalLoadParametersUuid(asymmetricalLoadParametersUuid)
                .build();
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
    void testGetAsymmetricalLoadParameters() throws Exception {
        String parametersToCreate = "{asymmetricalLoadParameters}";
        computationServerStubs.stubParametersGet(
            String.valueOf(ASYMMETRICAL_LOAD_PARAMETERS_UUID),
            parametersToCreate,
            ASYMMETRICAL_LOAD_PREFIX
        );

        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), ASYMMETRICAL_LOAD_PARAMETERS_UUID).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/asymmetrical-load/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(parametersToCreate));

        computationServerStubs.verifyParametersGet(String.valueOf(ASYMMETRICAL_LOAD_PARAMETERS_UUID), ASYMMETRICAL_LOAD_PREFIX);

        // Not found case
        UUID wrongParamUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.get("v1/" + ASYMMETRICAL_LOAD_PREFIX + "parameters/" + wrongParamUuid)
            .willReturn(WireMock.notFound()));

        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> asymmetricalLoadRestService.getAsymmetricalLoadParameters(wrongParamUuid)
        );
    }

    @Test
    void testSetAsymmetricalLoadParameters() throws Exception {
        String parameterToUpdate = buildFilter();

        wireMockServer.stubFor(put(urlPathEqualTo("/v1/asymmetrical-load/parameters/" + ASYMMETRICAL_LOAD_PARAMETERS_UUID))
            .willReturn(ok()));
        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), ASYMMETRICAL_LOAD_PARAMETERS_UUID).getId();

        createOrUpdateParametersAndDoChecks(studyUuid, parameterToUpdate, "userId", HttpStatus.OK);
        wireMockServer.verify(putRequestedFor(urlPathEqualTo("/v1/asymmetrical-load/parameters/" + ASYMMETRICAL_LOAD_PARAMETERS_UUID)));

        // Fail case
        UUID wrongParamUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.put("v1/asymmetrical-load/parameters/" + wrongParamUuid)
            .willReturn(WireMock.notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> asymmetricalLoadRestService.updateAsymmetricalLoadParameters(wrongParamUuid, "parameterToUpdate")
        );
    }

    @Test
    void testCreateAsymmetricalLoadParameters() {
        String parameterToCreate = "\"fakeParamsToCreate\"";

        UUID expectedUuid = UUID.randomUUID();
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/asymmetrical-load/parameters"))
            .willReturn(okJson("\"" + expectedUuid + "\"")));

        UUID paramUuid = asymmetricalLoadRestService.createAsymmetricalLoadParameters(parameterToCreate);

        assertEquals(expectedUuid, paramUuid);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/asymmetrical-load/parameters"))
            .withRequestBody(equalToJson(parameterToCreate)));

        //failure
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/asymmetrical-load/parameters"))
            .willReturn(notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> asymmetricalLoadRestService.createAsymmetricalLoadParameters(parameterToCreate)
        );
    }

    @Test
    void testDefaultParameters() throws Exception {
        String params = buildFilter();
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/asymmetrical-load/parameters/default"))
            .willReturn(okJson(objectMapper.writeValueAsString(ASYMMETRICAL_LOAD_PARAMETERS_UUID))));

        computationServerStubs.stubParametersGet(
            String.valueOf(ASYMMETRICAL_LOAD_PARAMETERS_UUID),
            params,
            ASYMMETRICAL_LOAD_PREFIX
        );

        UUID studyUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/asymmetrical-load/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string(params));

        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/asymmetrical-load/parameters/default")));
        computationServerStubs.verifyParametersGet(String.valueOf(ASYMMETRICAL_LOAD_PARAMETERS_UUID), ASYMMETRICAL_LOAD_PREFIX);

        assertNotNull(studyUuid);
        assertEquals(ASYMMETRICAL_LOAD_PARAMETERS_UUID,
            studyRepository.findById(studyUuid).orElseThrow().getAsymmetricalLoadParametersUuid());

        // Fail case
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/asymmetrical-load/parameters/default"))
            .willReturn(WireMock.notFound()));
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> asymmetricalLoadRestService.createDefaultParameters()
        );
    }

    @Test
    void testExportAsymmetricalLoadResults() throws Exception {
        // --- create study and node ---
        StudyNodeIds ids = createStudyAndNode(VARIANT_ID, "node1", ASYMMETRICAL_LOAD_PARAMETERS_UUID);
        runAsymmetricalLoad(ids);

        // Prepare body: JSON array of csv headers
        List<String> csvHeaders = List.of("busId", "imbalanceRate", "calculatedP", "calculatedQ");
        String content = objectMapper.writeValueAsString(csvHeaders);

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/asymmetrical-load/results/" + ASYMMETRICAL_LOAD_RESULT_UUID + "/csv"))
            .withQueryParam("sort", WireMock.equalTo("id,DESC"))
            .withQueryParam("filters", WireMock.equalTo("fakeFilters"))
            .withQueryParam("globalFilters", WireMock.equalTo("fakeGlobalFilters"))
            .withRequestBody(WireMock.equalToJson(content))
            .willReturn(WireMock.ok()
                .withBody(ASYMMETRICAL_LOAD_RESULTS_AS_ZIPPED_CSV)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            )
        ).getId();
        mockMvc.perform(post(ASYMMETRICAL_LOAD_URL_BASE + "result/csv", ids.studyId, ids.rootNetworkUuid, ids.nodeId)
                .param("sort", "id,DESC")
                .param("filters", "fakeFilters")
                .param("globalFilters", "fakeGlobalFilters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content)
                .header("userId", "userId"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(ASYMMETRICAL_LOAD_RESULTS_AS_ZIPPED_CSV));

        // Verification of the POST to the ASYMMETRICAL LOAD server
        wireMockStubs.verifyExportAsymmetricalLoadResult(stubId, UUID.fromString(ASYMMETRICAL_LOAD_RESULT_UUID));

        // --- NOT FOUND CASE ---
        UUID notFoundUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/asymmetrical-load/results/" + notFoundUuid + "/csv"))
            .withRequestBody(WireMock.equalToJson(content))
            .willReturn(WireMock.notFound())
        );

        // test csv failure
        assertThrows(HttpClientErrorException.NotFound.class, () ->
            asymmetricalLoadRestService.exportAsymmetricalLoadResultsAsCsv(notFoundUuid, "", null, null, Sort.unsorted(), null, null));
        assertThrows(StudyException.class, () ->
            asymmetricalLoadRestService.exportAsymmetricalLoadResultsAsCsv(null, "", null, null, Sort.unsorted(), null, null));
    }
}
