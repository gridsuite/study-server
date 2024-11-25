/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
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
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.security.LimitViolationType;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.LimitViolationInfos;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
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

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class LoadFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowTest.class);

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String LOADFLOW_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String LOADFLOW_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String LOADFLOW_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final String LOADFLOW_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";

    private static final String PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"loadFlowParameterId\":\"" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString(LOADFLOW_PARAMETERS_UUID_STRING);

    private static final String PROVIDER = "LF_PROVIDER";

    private static final String LOADFLOW_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VARIANT_ID = "variant_1";

    private static final String VARIANT_ID_2 = "variant_2";

    private static final long TIMEOUT = 1000;

    private static final String USER_ID_HEADER = "userId";

    private static final String DEFAULT_PROVIDER = "defaultProvider";
    private static final String OTHER_PROVIDER = "otherProvider";

    private static String LIMIT_VIOLATIONS_JSON;

    private static String COMPUTING_STATUS_JSON;

    private static String LOADFLOW_DEFAULT_PARAMETERS_JSON;
    private static String LOADFLOW_PROFILE_PARAMETERS_JSON;

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String LOADFLOW_RESULT_DESTINATION = "loadflow.result";
    private static final String LOADFLOW_STOPPED_DESTINATION = "loadflow.stopped";
    private static final String LOADFLOW_FAILED_DESTINATION = "loadflow.failed";

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
    private LoadFlowService loadFlowService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private StudyService studyService;

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        String loadFlowResultUuidStr = objectMapper.writeValueAsString(LOADFLOW_RESULT_UUID);

        String loadFlowErrorResultUuidStr = objectMapper.writeValueAsString(LOADFLOW_ERROR_RESULT_UUID);
        String loadflowResult = TestUtils.resourceToString("/loadflow-result.json");
        List<LimitViolationInfos> limitViolations = List.of(LimitViolationInfos.builder()
                        .subjectId("lineId2")
                        .limit(100.)
                        .limitName("lineName2")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(300)
                        .overload(null)
                        .value(80.)
                        .side(TwoSides.TWO.name())
                        .limitType(LimitViolationType.CURRENT)
                        .build(),
                LimitViolationInfos.builder()
                        .subjectId("lineId1")
                        .limit(200.)
                        .limitName("lineName1")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(60)
                        .overload(null)
                        .value(150.0)
                        .side(TwoSides.ONE.name())
                        .limitType(LimitViolationType.CURRENT)
                        .build(),
                LimitViolationInfos.builder()
                        .subjectId("genId1")
                        .limit(500.)
                        .limitName("genName1")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(null)
                        .overload(null)
                        .value(370.)
                        .side(null)
                        .limitType(LimitViolationType.HIGH_VOLTAGE)
                        .build());
        LIMIT_VIOLATIONS_JSON = objectMapper.writeValueAsString(limitViolations);
        COMPUTING_STATUS_JSON = objectMapper.writeValueAsString(List.of("CONVERGED", "FAILED"));

        LoadFlowParametersInfos loadFlowParametersInfos = LoadFlowParametersInfos.builder()
                .provider(PROVIDER)
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build();
        LOADFLOW_DEFAULT_PARAMETERS_JSON = objectMapper.writeValueAsString(loadFlowParametersInfos);
        LoadFlowParametersInfos profileLoadFlowParametersInfos = LoadFlowParametersInfos.builder()
                .provider(OTHER_PROVIDER)
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build();
        LOADFLOW_PROFILE_PARAMETERS_JSON = objectMapper.writeValueAsString(profileLoadFlowParametersInfos);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows(JsonProcessingException.class)
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = Objects.requireNonNull(request.getMethod());
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + ".*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", LOADFLOW_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), LOADFLOW_RESULT_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), loadFlowResultUuidStr);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), LOADFLOW_FAILED_DESTINATION);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), loadFlowErrorResultUuidStr);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), loadflowResult);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "\\?filters=.*globalFilters=.*networkUuid=.*variantId.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), loadflowResult);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), LOADFLOW_STATUS_JSON);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/limit-violations")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), LIMIT_VIOLATIONS_JSON);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/limit-violations\\?filters=.*globalFilters=.*networkUuid=.*variantId.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), LIMIT_VIOLATIONS_JSON);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/computation-status")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), COMPUTING_STATUS_JSON);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/computation")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/results/invalidate-status\\?resultUuid=" + LOADFLOW_RESULT_UUID)) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + LOADFLOW_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? LOADFLOW_OTHER_NODE_RESULT_UUID : LOADFLOW_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), LOADFLOW_STOPPED_DESTINATION);
                    return new MockResponse(200);
                } else if (path.matches("/v1/results")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), LOADFLOW_DEFAULT_PARAMETERS_JSON);
                    } else {
                        return new MockResponse(200);
                    }
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(LOADFLOW_PARAMETERS_UUID_STRING));
                } else if (path.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_NO_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_INVALID_PARAMS_JSON);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request KO
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters/" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    // profile params get request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), LOADFLOW_PROFILE_PARAMETERS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING + "/provider") && method.equals("PATCH")) {
                    // provider update in duplicated params OK
                    return new MockResponse(200);
                } else if (path.matches("/v1/default-provider")) {
                    return new MockResponse.Builder().code(200).body(DEFAULT_PROVIDER).build();
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testLoadFlow(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
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
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

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
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/stop", studyNameUserIdUuid, modificationNode3Uuid)
                .header(HEADER_USER_ID, "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        // loadflow failed
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode2Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));
    }

    @Test
    void testGetLimitViolations(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run?limitReduction=0.7", studyNameUserIdUuid, modificationNode1Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + "&limitReduction=0.7")));

        // get computing status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, modificationNode1Uuid, LOAD_FLOW, "computation-status"))
                .andExpectAll(status().isOk(),
                        content().string(COMPUTING_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/computation-status")));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, modificationNode1Uuid, LOAD_FLOW, "computation")).andReturn();

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/computation")));

        // get limit violations
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/limit-violations", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(LIMIT_VIOLATIONS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/limit-violations")));

        // get limit violations with filters , globalFilters and sort
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/limit-violations?filters=lineId2&sort=subjectId,ASC&globalFilters=ss", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(LIMIT_VIOLATIONS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + LOADFLOW_RESULT_UUID + "/limit-violations\\?filters=lineId2&globalFilters=ss&networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID_2 + "&sort=subjectId,ASC")));

        // get limit violations on non existing node
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/limit-violations", studyNameUserIdUuid, UUID.randomUUID())).andExpectAll(
                status().isNotFound());
    }

    @Test
    void testInvalidateStatus(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run?limitReduction=0.7", studyNameUserIdUuid, modificationNode1Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2 + "&limitReduction=0.7")));

        // invalidate status
        mockMvc.perform(put("/v1/studies/{studyUuid}/loadflow/invalidate-status", studyNameUserIdUuid)
                .header("userId", "userId")).andExpect(status().isOk());
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/invalidate-status\\?resultUuid=" + LOADFLOW_RESULT_UUID)));
    }

    @Test
    void testDeleteLoadFlowResults(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
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

        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));
        //Test result count
        testResultCount(server);
        //Delete Voltage init results
        testDeleteResults(1, server);
    }

    @Test
    void testResetUuidResultWhenLFFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), LOADFLOW_PARAMETERS_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, LOAD_FLOW);
        assertNotNull(networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));
        assertEquals(resultUuid, networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), LOADFLOW_FAILED_DESTINATION);
            return resultUuid;
        }).when(studyService).runLoadFlow(any(), any(), any(), any(), any());
        studyService.runLoadFlow(studyEntity.getId(), modificationNode.getId(), rootNetworkUuid, "", null);

        // Test reset uuid result in the database
        assertNull(networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> loadFlowStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
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

    private void testResultCount(final MockWebServer server) throws Exception {
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", LOAD_FLOW.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));
    }

    private void testDeleteResults(int expectedInitialResultCount, final MockWebServer server) throws Exception {
        assertEquals(expectedInitialResultCount, rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", LOAD_FLOW.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        assertEquals(0, rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());
    }

    @Test
    void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
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

        // stop non-existing loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/shortcircuit/stop", studyNameUserIdUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "userId")).andExpect(status().isOk());
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
                .andExpect(status().is(status.value()));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testResetLoadFlowParametersUserHasNoProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetLoadFlowParametersUserHasNoParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetLoadFlowParametersUserHasInvalidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING))); // update existing with dft
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING))); // post duplicate ko
    }

    @Test
    void testResetLoadFlowParametersUserHasValidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(5, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING))); // 2 requests: 1 get for provider and then delete existing
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + PROFILE_LOADFLOW_DUPLICATED_PARAMETERS_UUID_STRING + "/provider"))); // patch duplicated params for provider
    }

    @Test
    void testResetLoadFlowParametersUserHasValidParamsInProfileButNoExistingLoadflowParams(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    // the following testGetDefaultProviders tests are related to StudyTest::testGetDefaultProviders but with a user and different profile cases
    @Test
    void testGetDefaultProvidersFromProfile(final MockWebServer server) throws Exception {
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, VALID_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(OTHER_PROVIDER));
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING))); // GET provider
    }

    @Test
    void testGetDefaultProvidersFromProfileInvalid(final MockWebServer server) throws Exception {
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, INVALID_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING))); // GET provider
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/default-provider"))); // GET fallback default provider
    }

    @Test
    void testGetDefaultProvidersWithoutProfile(final MockWebServer server) throws Exception {
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, NO_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/default-provider"))); // GET fallback default provider
    }

    @Test
    void testGetDefaultProvidersWithoutParamInProfile(final MockWebServer server) throws Exception {
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, NO_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/default-provider"))); // GET fallback default provider
    }

    @Test
    void testLoadFlowParameters(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get initial loadFlow parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, LOADFLOW_DEFAULT_PARAMETERS_JSON, "userId", HttpStatus.OK);

        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        assertTrue(TestUtils.getRequestsDone(3, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));

        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);

        studyNameUserIdUuid = studyEntity2.getId();

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, LOADFLOW_DEFAULT_PARAMETERS_JSON, "userId", HttpStatus.OK);

        //get initial loadFlow parameters
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));

    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID loadFlowParametersUuid) {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                loadFlowParametersUuid, null, null, null,
                defaultNonEvacuatedEnergyParametersEntity);
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

        rootNetworkNodeInfoService.initRootNetworkNode(modificationNode.getId(), studyService.getStudyFirstRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, LOADFLOW_RESULT_DESTINATION, LOADFLOW_STOPPED_DESTINATION, LOADFLOW_FAILED_DESTINATION);

        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
