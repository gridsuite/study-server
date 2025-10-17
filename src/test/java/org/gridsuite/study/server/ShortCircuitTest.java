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
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
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
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.gridsuite.study.server.utils.TestUtils.USER_DEFAULT_PROFILE_JSON;
import static org.gridsuite.study.server.utils.TestUtils.getBinaryAsBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private ObjectWriter objectWriter;

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
    private StudyService studyService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        String shortCircuitAnalysisResultUuidStr = objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        String shortCircuitAnalysisResultNotFoundUuidStr = objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND);
        String shortCircuitAnalysisErrorResultUuidStr = objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_ERROR_RESULT_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = Objects.requireNonNull(request.getMethod());

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&busId=BUS_TEST_ID&variantId=" + VARIANT_ID_2)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), shortCircuitAnalysisResultUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + ".*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), shortCircuitAnalysisResultUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING_NOT_FOUND + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_4)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), shortCircuitAnalysisResultNotFoundUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), shortCircuitAnalysisFailedDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), shortCircuitAnalysisErrorResultUuidStr);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SHORT_CIRCUIT_ANALYSIS_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault-types")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault_results/paged" + "\\?rootNetworkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID_2 + "&mode=FULL&page=0&size=20&sort=id,DESC")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SHORT_CIRCUIT_ANALYSIS_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/csv")) {
                    return new MockResponse.Builder().code(200).body(getBinaryAsBuffer(SHORT_CIRCUIT_ANALYSIS_CSV_RESULT)).addHeader("Content-Type", "application/json; charset=utf-8").build();
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND + "/csv")) {
                        return new MockResponse(404);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/feeder_results/paged" + "\\?rootNetworkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID_2 + "&mode=FULL&filters=fakeFilters&page=0&size=20&sort=id,DESC")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SHORT_CIRCUIT_ANALYSIS_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SHORT_CIRCUIT_ANALYSIS_STATUS_JSON);
                } else if (path.matches("/v1/results/invalidate-status\\?resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)) {
                    // TODO : fix duplicate ressult uuids
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? SHORT_CIRCUIT_ANALYSIS_OTHER_NODE_RESULT_UUID : SHORT_CIRCUIT_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), shortCircuitAnalysisStoppedDestination);
                    return new MockResponse(200);
                } else if (path.matches("/v1/results\\?resultsUuids.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING));
                } else if (path.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_DEFAULT_PROFILE_JSON);
                } else if (path.matches("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_NO_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_INVALID_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID)) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), TestUtils.resourceToString("/short-circuit-parameters.json"));
                    } else {
                        //Method PUT
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
                    }
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request KO
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters/" + PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    // profile params get request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SHORT_CIRCUIT_ANALYSIS_PROFILE_PARAMETERS_JSON);
                } else if (path.matches("/v1/parameters") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
                } else if (path.matches("/v1/parameters/default") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID));
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse(418, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE), "Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testShortCircuitAnalysisParameters(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), UUID.fromString(CASE_SHORT_CIRCUIT_UUID_STRING), null);
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get default ShortCircuitParameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid))
               .andExpectAll(status().isOk(), content().string(TestUtils.resourceToString("/short-circuit-parameters.json")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.equals("/v1/parameters/default")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID)));

        mockMvc.perform(post("/v1/studies/{studyUuid}/short-circuit-analysis/parameters", studyNameUserIdUuid)
                        .header(HEADER_USER_ID, "testUserId")
                        .content("{\"dumb\": \"json\"}").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID)));
    }

    @Test
    void testAllBusesShortCircuit(final MockWebServer server) throws Exception {
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

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");

        UUID unknownModificationNodeUuid = UUID.randomUUID();

        // run a short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run in debug mode an all-buses short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .param(QUERY_PARAM_DEBUG, "true")
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, true);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + "&debug=true")));

        // get short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")));

        // export short circuit analysis csv result
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .param("type", "ALL_BUSES")
                .content(CSV_HEADERS)).andExpectAll(status().isOk(), content().bytes(SHORT_CIRCUIT_ANALYSIS_CSV_RESULT));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/csv")));

        // get short circuit result but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        assertTrue(TestUtils.getRequestsDone(0, server).isEmpty());

        // get short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));

        // stop short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        // short circuit analysis failed
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode2Uuid)
                .header(HEADER_USER_ID, "testUserId"))
                .andExpect(status().isOk()).andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        // Test result count
        // In short-circuit server there is no distinction between 1-bus and all-buses, so the count will return all kinds of short-circuit
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        // Delete Shortcircuit results
        // In short-circuit server there is no distinction between 1-bus and all-buses, so we remove all kinds of short-circuit
        assertEquals(1, rootNetworkNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/results\\?resultsUuids")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull().size());
    }

    @Test
    void testGetShortCircuitAnalysisCsvResultNotFound(final MockWebServer server) throws Exception {
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

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_4, "node 4");
        UUID modificationNode4Uuid = modificationNode4.getId();

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode4Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode4Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND, false);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING_NOT_FOUND + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_4)));

        // export short circuit analysis csv result not found

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result/csv", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode4Uuid)
                .param("type", "ALL_BUSES")
                .content(CSV_HEADERS)).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID_NOT_FOUND + "/csv")));
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
    void testPagedShortCircuit(final MockWebServer server) throws Exception {
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
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());

        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // get fault types
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, ComputationType.SHORT_CIRCUIT, "fault-types"))
                .andExpectAll(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault-types")));

        // get short circuit result with pagination
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/fault_results/paged\\?rootNetworkUuid=" + NETWORK_UUID_STRING + "&variantId=variant_2&mode=FULL&page=0&size=20&sort=id,DESC")));

        // get short circuit result with pagination but with unknown node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20", studyNameUserIdUuid, firstRootNetworkUuid, unknownModificationNodeUuid)).andExpect(
                status().isNoContent());

        assertTrue(TestUtils.getRequestsDone(0, server).isEmpty());

        // get short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));

        // stop short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));
    }

    @Test
    void testOneBusShortCircuit(final MockWebServer server) throws Exception {
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

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
            modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");

        // run a one bus short circuit analysis on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                .param("busId", "BUS_TEST_ID")
                .header("userId", "userId"))
            .andExpect(status().isForbidden());

        //run in debug mode a one bus short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                .param("busId", "BUS_TEST_ID")
                .param(QUERY_PARAM_DEBUG, "true")
                .header("userId", "userId"))
            .andExpect(status().isOk());

        consumeShortCircuitAnalysisOneBusResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, true);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + "&debug=true")));

        assertEquals(1, rootNetworkNodeInfoRepository.findAllByOneBusShortCircuitAnalysisResultUuidNotNull().size());

        // get one bus short circuit result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON)
            );

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "\\?mode=FULL")));

        // get short circuit result with pagination
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/result?paged=true&page=0&size=20&sort=id,DESC&filters=fakeFilters", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name())
        ).andExpectAll(
            status().isOk(),
            content().string(SHORT_CIRCUIT_ANALYSIS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/feeder_results/paged\\?rootNetworkUuid=" + NETWORK_UUID_STRING + "&variantId=variant_2&mode=FULL&filters=fakeFilters&page=0&size=20&sort=id,DESC")));

        // get one bus short circuit status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/status", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
            .param("type", ShortcircuitAnalysisType.ONE_BUS.name()))
            .andExpectAll(
                status().isOk(),
                content().string(SHORT_CIRCUIT_ANALYSIS_STATUS_JSON)
            );

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "/status")));

        //Test result count
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        // Delete Shortcircuit results
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", ComputationType.SHORT_CIRCUIT.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/results\\?resultsUuids")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
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
    void testInvalidateShortCircuitStatus(final MockWebServer server) throws Exception {
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
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        consumeShortCircuitAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        //run a one bus short circuit analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/shortcircuit/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .param("busId", "BUS_TEST_ID")
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        consumeShortCircuitAnalysisOneBusResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SHORT_CIRCUIT_ANALYSIS_RESULT_UUID, false);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // invalidate status
        mockMvc.perform(put("/v1/studies/{studyUuid}/short-circuit/invalidate-status", studyNameUserIdUuid)
                .header("userId", "userId")).andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        // TODO : fix duplicate ressult uuids
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/invalidate-status\\?resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID + "&resultUuid=" + SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)));
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
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasNoProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasNoParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasInvalidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SHORT_CIRCUIT_ANALYSIS_INVALID_PARAMETERS_UUID_STRING))); // post duplicate ko
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasValidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SHORT_CIRCUIT_ANALYSIS_PARAMETERS_UUID_STRING)));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    @Test
    void testResetShortCircuitAnalysisParametersUserHasValidParamsInProfileButNoExistingShortcircuitAnalysisParams(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_SHORT_CIRCUIT_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SHORT_CIRCUIT_ANALYSIS_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID shortCircuitParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                UUID.randomUUID(), shortCircuitParametersUuid, null, null, null);
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

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(NodeBuildStatus.from(buildStatus)).build());

        return modificationNode;
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, shortCircuitAnalysisResultDestination, shortCircuitAnalysisStoppedDestination, shortCircuitAnalysisFailedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
