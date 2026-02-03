/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
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
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import mockwebserver3.MockWebServer;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.*;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_DEBUG;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.utils.TestUtils.USER_DEFAULT_PROFILE_JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ShortCircuitTest implements WithAssertions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShortCircuitTest.class);

    private static final String CASE_SHORT_CIRCUIT_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final String CASE_SHORT_CIRCUIT_UUID_STRING_NOT_FOUND = "22291c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_SHORT_CIRCUIT_UUID = UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String NETWORK_UUID_STRING_NOT_FOUND = "48400000-8cf0-11bd-b23e-10b96e4ef33d";

    private static final String SHORT_CIRCUIT_ANALYSIS_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND = "2b6cc22c-3f33-11ed-b888-0242ac122222";

    private static final String SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final String SHORT_CIRCUIT_ANALYSIS_RESULT_JSON = "{\"version\":\"1.0\",\"faults\":[]}";

    private static final String CSV_HEADERS = "{csvHeaders}";

    private static final byte[] SHORT_CIRCUIT_ANALYSIS_CSV_RESULT = {0x00, 0x11};

    private static final String SHORT_CIRCUIT_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String SHORT_CIRCUIT_ANALYSIS_PROFILE_PARAMETERS_JSON = "{\"withLimitViolations\":\"true\",\"withFortescueResult\":\"false\",\"withFeederResult\":\"true\"}";
    private static final String SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID = UUID.fromString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SHORT_CIRCUIT_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"shortcircuitParameterId\":\"" + PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"shortcircuitParameterId\":\"" + PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_SHORT_CIRCUIT_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";
    private static final String VARIANT_ID_4 = "variant_4";

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @Autowired
    private ConsumerService consumerService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String elementUpdateDestination = "element.update";
    private final String shortCircuitAnalysisDebugDestination = "shortcircuitanalysis.debug";
    private final String shortCircuitAnalysisResultDestination = "shortcircuitanalysis.result";
    private final String shortCircuitAnalysisStoppedDestination = "shortcircuitanalysis.stopped";
    private final String shortCircuitAnalysisFailedDestination = "shortcircuitanalysis.run.dlx";
    @Autowired
    private TestUtils studyTestUtils;

    private static WireMockServer wireMockServer;

    private ShortcircuitServerStubs shortcircuitServerStubs;
    private UserAdminServerStubs userAdminServerStubs;
    private ReportServerStubs reportServerStubs;
    private ComputationServerStubs computationServerStubs;

    @BeforeAll
    static void initWireMock(@Autowired InputDestination input) {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockServer.start();
    }

    @BeforeEach
    void setup() {
        shortcircuitServerStubs = new ShortcircuitServerStubs(wireMockServer);
        userAdminServerStubs = new UserAdminServerStubs(wireMockServer);
        computationServerStubs = new ComputationServerStubs(wireMockServer);
        reportServerStubs = new ReportServerStubs(wireMockServer);

        shortCircuitService.setShortCircuitServerBaseUri(wireMockServer.baseUrl());
        reportService.setReportServerBaseUri(wireMockServer.baseUrl());
        userAdminService.setUserAdminServerBaseUri(wireMockServer.baseUrl());
    }

    @Test
    void testShortCircuitAnalysisParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING), null);
        UUID studyNameUserIdUuid = studyEntity.getId();

        computationServerStubs.stubPostParametersDefault(objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING));
        computationServerStubs.stubParametersGet(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, TestUtils.resourceToString("/short-circuit-parameters.json"));
        //get default ShortCircuitParameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid))
               .andExpectAll(status().isOk(), content().string(TestUtils.resourceToString("/short-circuit-parameters.json")));
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/parameters/default", Map.of());
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, Map.of());

        computationServerStubs.stubParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        mockMvc.perform(post("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)
                        .header(HEADER_USER_ID, "testUserId")
                        .content("{\"dumb\": \"json\"}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertEquals(NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(NotificationService.UPDATE_TYPE_PCC_MIN_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        WireMockUtilsCriteria.verifyPutRequest(wireMockServer, "/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, Map.of(), null);
    }

    @Test
    void testAllBusesShortCircuit() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
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
        UUID unknownModificationNodeUuid = UUID.randomUUID();

        // run a short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run in debug mode an all-buses short circuit analysis
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .param(QUERY_PARAM_DEBUG, "true")
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        shortcircuitServerStubs.verifyShortcircuitRun(NETWORK_UUID_STRING, VARIANT_ID_2, true);
        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, true);

        // get short circuit result
        computationServerStubs.stubGetResult(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_RESULT_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid))
                .andExpectAll(
                        status().isOk(),
                        content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, Map.of("mode", WireMock.equalTo("FULL")));

        // export short circuit analysis csv result
        computationServerStubs.stubGetResultCsv(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_CSV_RESULT);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .param("type", "ALL_BUSES")
                .content(CSV_HEADERS)).andExpectAll(status().isOk(), content().bytes(SHORT_CIRCUIT_ANALYSIS_CSV_RESULT));
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/csv", Map.of());

        // get short circuit result but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        // get short circuit status
        computationServerStubs.stubGetResultStatus(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_STATUS_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status", Map.of());

        // stop short circuit analysis
        shortcircuitServerStubs.stubShortCircuitStopWithPostAction(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, shortCircuitAnalysisStoppedDestination, modificationNode3Uuid, firstRootNetworkUuid);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyPutRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop", true, Map.of(
                "receiver", WireMock.matching(".*")), null);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        // short circuit analysis failed
        shortcircuitServerStubs.stubShortCircuitRunWithPostAction(NETWORK_UUID_STRING, VARIANT_ID, SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID, shortCircuitAnalysisFailedDestination, modificationNode2Uuid, firstRootNetworkUuid);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode2Uuid)
                .header(HEADER_USER_ID, "testUserId"))
                .andExpect(status().isOk()).andReturn();
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save", true,
                Map.of(
                        "receiver", WireMock.matching(".*"),
                        "reporterId", WireMock.matching(".*"),
                        "variantId", WireMock.equalTo(VARIANT_ID)),
                null);

        // Test result count
        // In short-circuit server there is no distinction between 1-bus and all-buses, so the count will return all kinds of short-circuit
        computationServerStubs.stubResultsCount(1);
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/supervision/results-count", Map.of());

        // Delete Shortcircuit results
        // In short-circuit server there is no distinction between 1-bus and all-buses, so we remove all kinds of short-circuit
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
        computationServerStubs.stubDeleteResults("/v1/results");
        reportServerStubs.stubDeleteReport();
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyDeleteRequest(wireMockServer, "/v1/results", Map.of("resultsUuids", matching(".*")));
        reportServerStubs.verifyDeleteReport();
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
    }

    @Test
    void testGetShortCircuitAnalysisCsvResultNotFound() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING_NOT_FOUND), UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING_NOT_FOUND), null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_4, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING_NOT_FOUND, VARIANT_ID_4, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/networks/" + NETWORK_UUID_STRING_NOT_FOUND + "/run-and-save", true,
                Map.of(
                        "receiver", WireMock.matching(".*"),
                        "reporterId", WireMock.matching(".*"),
                        "variantId", WireMock.equalTo(VARIANT_ID_4)),
                null);
        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND, false);

        // export short circuit analysis csv result not found
        computationServerStubs.stubGetResultCsvNotFound(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .param("type", "ALL_BUSES")
                .content(CSV_HEADERS)).andExpectAll(status().isNotFound());
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND + "/csv", Map.of());
    }

    private void consumeShortCircuitAnalysisResult(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid, boolean debug) throws JsonProcessingException {
        // consume result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));

        consumerService.consumeShortCircuitAnalysisResult().accept(MessageBuilder.createMessage("", messageHeaders));

        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        if (debug) {
            consumerService.consumeShortCircuitAnalysisDebug().accept(MessageBuilder.createMessage("", messageHeaders));
            checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.COMPUTATION_DEBUG_FILE_STATUS);
        }
    }

    private void consumeShortCircuitAnalysisOneBusResult(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid, boolean debug) throws JsonProcessingException {
        // consume result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", resultUuid, "busId", "BUS_TEST_ID", HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeShortCircuitAnalysisResult().accept(MessageBuilder.createMessage("", messageHeaders));

        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT);

        if (debug) {
            consumerService.consumeShortCircuitAnalysisDebug().accept(MessageBuilder.createMessage("", messageHeaders));
            checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.COMPUTATION_DEBUG_FILE_STATUS);
        }
    }

    @Test
    void testPagedShortCircuit() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID_2, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        UUID unknownModificationNodeUuid = UUID.randomUUID();

        //run a short circuit analysis
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());
        computationServerStubs.verifyComputationRun(NETWORK_UUID_STRING, Map.of(
                        "receiver", WireMock.matching(".*"),
                        "reporterId", WireMock.matching(".*"),
                        "variantId", WireMock.equalTo(VARIANT_ID_2)));
        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        // get fault types
        shortcircuitServerStubs.stubGetFaultTypes(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, ComputationType.SHORT_CIRCUIT, "fault-types"))
                .andExpectAll(status().isOk());
        shortcircuitServerStubs.verifyGetFaultTypes(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);

        // get short circuit result with pagination
        shortcircuitServerStubs.stubGetPagedFaultResults(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID,
                SHORT_CIRCUIT_ANALYSIS_RESULT_JSON, NETWORK_UUID_STRING, VARIANT_ID_2, "FULL", "0", "20", "id,DESC");
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));
        shortcircuitServerStubs.verifyGetPagedFaultResults(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, NETWORK_UUID_STRING, VARIANT_ID_2,
                "FULL", "0", "20", "id,DESC");

        // get short circuit result with pagination but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20", studyNameUserIdUuid, firstRootNetworkUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        // get short circuit status
        computationServerStubs.stubGetResultStatus(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_STATUS_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));
        computationServerStubs.verifyGetResultStatus(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, 1);

        // stop short circuit analysis
        shortcircuitServerStubs.stubShortCircuitStopWithPostAction(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, shortCircuitAnalysisStoppedDestination, modificationNode1Uuid, firstRootNetworkUuid);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);
        WireMockUtilsCriteria.verifyPutRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop", true, Map.of(
                "receiver", WireMock.matching(".*")), null);
    }

    @Test
    void testOneBusShortCircuit() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
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

        // run a one bus short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                .param("busId", "BUS_TEST_ID")
                .header("userId", "userId"))
            .andExpect(status().isForbidden());

        //run in debug mode a one bus short circuit analysis
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .param("busId", "BUS_TEST_ID")
                .param(QUERY_PARAM_DEBUG, "true")
                .header("userId", "userId"))
            .andExpect(status().isOk());
        shortcircuitServerStubs.verifyShortcircuitRun(NETWORK_UUID_STRING, VARIANT_ID_2, true);
        consumeShortCircuitAnalysisOneBusResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, true);
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByOneBusShortCircuitAnalysisResultUuidNotNull().size());

        // get one bus short circuit result
        computationServerStubs.stubGetResult(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_RESULT_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
            );
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, Map.of("mode", WireMock.equalTo("FULL")));

        // get short circuit result with pagination
        shortcircuitServerStubs.stubGetPagedFeederResults(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_RESULT_JSON, NETWORK_UUID_STRING, VARIANT_ID_2, "fakeFilters", "FULL", "0", "20", "id,DESC");
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC&filters=fakeFilters", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name())
        ).andExpectAll(
            status().isOk(),
            content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));
        shortcircuitServerStubs.verifyGetPagedFeederResults(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, NETWORK_UUID_STRING, VARIANT_ID_2, "fakeFilters", "FULL", "0", "20", "id,DESC");

        // get one bus short circuit status
        computationServerStubs.stubGetResultStatus(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, SHORT_CIRCUIT_ANALYSIS_STATUS_JSON);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON)
            );
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status", Map.of());

        //Test result count
        computationServerStubs.stubResultsCount(1);
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/supervision/results-count", Map.of());

        // Delete Shortcircuit results
        computationServerStubs.stubDeleteResults("/v1/results");
        reportServerStubs.stubDeleteReport();
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk());
        WireMockUtilsCriteria.verifyDeleteRequest(wireMockServer, "/v1/results", Map.of("resultsUuids", matching(".*")));
        reportServerStubs.verifyDeleteReport();
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByOneBusShortCircuitAnalysisResultUuidNotNull().size());
    }

    @Test
    void testResetUuidResultWhenSCFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, ComputationType.SHORT_CIRCUIT);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, ComputationType.SHORT_CIRCUIT));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, ComputationType.SHORT_CIRCUIT));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), shortCircuitAnalysisFailedDestination);
            return resultUuid;
        }).when(studyService).runShortCircuit(any(), any(), any(), any(), anyBoolean(), any());
        studyService.runShortCircuit(studyEntity.getId(), modificationNode.getId(), rootNetworkUuid, Optional.empty(), false, "user_1");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, ComputationType.SHORT_CIRCUIT));

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
    void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No one bus short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                    .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
                .andExpectAll(status().isNoContent());

        // NOT_FOUND short circuit analysis result csv
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .param("type", ShortcircuitAnalysisType.ONE_BUS.name())
                .content(CSV_HEADERS)).andExpectAll(status().isNotFound());

        // No short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // stop non-existing short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());
    }

    @Test
    void testSetParamInvalidateShortCircuitStatus() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
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
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();
        computationServerStubs.verifyComputationRun(NETWORK_UUID_STRING, Map.of(
                "receiver", WireMock.matching(".*"),
                "reporterId", WireMock.matching(".*"),
                "variantId", WireMock.equalTo(VARIANT_ID_2)));
        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        // update parameters invalidate the status
        computationServerStubs.stubGetParametersDefault(objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING));
        computationServerStubs.stubPostParametersDefault(objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        computationServerStubs.stubInvalidateStatus();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);
        computationServerStubs.verifyParametersDefault();
        userAdminServerStubs.verifyGetUserProfile(NO_PROFILE_USER_ID);
        computationServerStubs.verifyInvalidateStatus(Map.of("resultUuid", new RegexPattern(".*")));
    }

    @Test
    void testSetParamInvalidateOneBusShortCircuitStatus(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
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

        //run a one bus short circuit analysis
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, VARIANT_ID_2, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .param("busId", "BUS_TEST_ID")
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();
        computationServerStubs.verifyComputationRun(NETWORK_UUID_STRING, Map.of(
                "receiver", WireMock.matching(".*"),
                "reporterId", WireMock.matching(".*"),
                "variantId", WireMock.equalTo(VARIANT_ID_2)));
        consumeShortCircuitAnalysisOneBusResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        // update parameters invalidate the status
        computationServerStubs.stubGetParametersDefault(objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING));
        computationServerStubs.stubPostParametersDefault(objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        computationServerStubs.stubInvalidateStatus();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);
        computationServerStubs.verifyParametersDefault();
        userAdminServerStubs.verifyGetUserProfile(NO_PROFILE_USER_ID);
        computationServerStubs.verifyInvalidateStatus(Map.of("resultUuid", new RegexPattern(".*")));
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.UPDATE_TYPE_PCC_MIN_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasNoProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        computationServerStubs.stubParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);
        userAdminServerStubs.verifyGetUserProfile(NO_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasNoParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        computationServerStubs.stubParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);
        userAdminServerStubs.verifyGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasInvalidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
        computationServerStubs.stubParametersDuplicateFromNotFound(PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);
        userAdminServerStubs.verifyGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasValidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(PROFILE_SHORT_CIRCUIT_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING));
        computationServerStubs.stubDeleteParameters(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);
        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        computationServerStubs.verifyDeleteParameters(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasValidParamsInProfileButNoExistingShortcircuitAnalysisParams(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING, objectMapper.writeValueAsString(PROFILE_SHORT_CIRCUIT_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING));
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);
        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID shortCircuitParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                UUID.randomUUID(), shortCircuitParametersUuid, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
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
        String mnBodyJson = objectMapper.writeValueAsString(modificationNode);
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

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(NodeBuildStatus.from(buildStatus)).build());

        return modificationNode;
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, shortCircuitAnalysisResultDestination, shortCircuitAnalysisStoppedDestination, shortCircuitAnalysisFailedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenClear(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
