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
import com.powsybl.security.LimitViolationType;
import com.powsybl.security.SecurityAnalysisParameters;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.dto.ComputationType.SECURITY_ANALYSIS;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS;
import static org.gridsuite.study.server.utils.TestUtils.getBinaryAsBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SecurityAnalysisTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAnalysisTest.class);

    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11111111-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "22222222-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String CONTINGENCY_LIST_NAME = "ls";
    private String limitTypeJson;
    private static final String SECURITY_ANALYSIS_N_RESULT_JSON = "{\"status\":\"CONVERGED\",\"limitViolationsResult\":{\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"networkResult\":{\"branchResults\":[],\"busResults\":[],\"threeWindingsTransformerResults\":[]}}";
    private static final String SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON = "[{\"id\":\"l1\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l2\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l3\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l4\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l6\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l7\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l8\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l9\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"la\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"lb\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]}]";
    private static final String SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON = "[{\"constraintId\":\"l3\",\"contingencies\":[]},{\"constraintId\":\"vl1\",\"contingencies\":[{\"contingencyId\":\"l1\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}]},{\"contingencyId\":\"l2\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}]},{\"contingencyId\":\"l3\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}]},{\"contingencyId\":\"l4\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}]},{\"contingencyId\":\"l6\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}]},{\"contingencyId\":\"l7\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}]},{\"contingencyId\":\"l8\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}]},{\"contingencyId\":\"l9\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"la\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"lb\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}]}]}]";
    private static final byte[] SECURITY_ANALYSIS_N_RESULT_CSV_ZIPPED = {0x00, 0x01};
    private static final byte[] SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED = {0x02, 0x03};
    private static final byte[] SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED = {0x04, 0x03};
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "\"CONVERGED\"";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";
    private static final String CONTINGENCIES_COUNT = "2";

    public static final String SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":0.0,\"lowVoltageProportionalThreshold\":0.0,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";
    private static final String SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":30.0,\"lowVoltageProportionalThreshold\":0.4,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";

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

    private static final String SECURITY_ANALYSIS_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID SECURITY_ANALYSIS_PARAMETERS_UUID = UUID.fromString(SECURITY_ANALYSIS_PARAMETERS_UUID_STRING);
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"securityAnalysisParameterId\":\"" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";

    private static final String CSV_TRANSLATION_DTO_STRING = "{translationsObject}";

    private static final long TIMEOUT = 1000;

    private static final SecurityAnalysisParameters SECURITY_ANALYSIS_PARAMETERS = new SecurityAnalysisParameters();

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
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ConsumerService consumerService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String saResultDestination = "sa.result";
    private final String saStoppedDestination = "sa.stopped";
    private final String saFailedDestination = "sa.run.dlx";
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
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
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        limitTypeJson = objectMapper.writeValueAsString(List.of(LimitViolationType.CURRENT.name(), LimitViolationType.HIGH_VOLTAGE.name()));

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = Objects.requireNonNull(request.getMethod());
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + resultUuid + "\"");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/limit-types")
                        || ("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/limit-types").equals(path)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), limitTypeJson);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/n-result\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_N_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-constraints-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/n-result/csv")) {
                    return new MockResponse.Builder().code(200).body(getBinaryAsBuffer(SECURITY_ANALYSIS_N_RESULT_CSV_ZIPPED))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/n-result/csv")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/csv")) {
                    return new MockResponse.Builder().code(200).body(getBinaryAsBuffer(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-constraints-result/csv")) {
                    return new MockResponse.Builder().code(200).body(getBinaryAsBuffer(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED))
                            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
                } else if (("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_STATUS_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                        .build(), saStoppedDestination);
                    return new MockResponse(200);
                } else if (path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING)
                        || path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), CONTINGENCIES_JSON);
                } else if (path.matches("/v1/contingency-lists/count\\?ids=" + CONTINGENCY_LIST_NAME + "&networkUuid=" + NETWORK_UUID_STRING)
                        || path.matches("/v1/contingency-lists/count\\?ids=" + CONTINGENCY_LIST_NAME + "&networkUuid=" + NETWORK_UUID_STRING + "&variantId=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), CONTINGENCIES_COUNT);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/n-result\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_N_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON);
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/nmk-constraints-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON);
                } else if (("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_STATUS_JSON);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + request.getPath().split("%")[11].substring(4) + "%22%2C%20%22userId%22%3A%22userId%22%7D")
                            .build(), saFailedDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .build(), saFailedDestination);
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"");
                } else if (path.matches("/v1/results\\?resultsUuids.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/reports")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "1");
                } else if (path.matches("/v1/results/invalidate-status\\?resultUuid=.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID)) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);
                    } else {
                        //Method PUT
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS_UUID));
                    }
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS_UUID_STRING));
                } else if (path.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_NO_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                } else if (path.matches("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_INVALID_PARAMS_JSON);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request KO
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters/" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/parameters\\?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING) && method.equals("POST")) {
                    // params duplication request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), DUPLICATED_PARAMS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING) && method.equals("GET")) {
                    // profile params get request OK
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SECURITY_ANALYSIS_PROFILE_PARAMETERS_JSON);
                } else if (path.matches("/v1/parameters/" + PROFILE_SECURITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "/provider") && method.equals("PUT")) {
                    // provider update in duplicated params OK
                    return new MockResponse(200);
                } else if (path.matches("/v1/parameters") && method.equals("POST")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS_UUID));
                } else if (path.matches("/v1/parameters/default") && method.equals("POST")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), objectMapper.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS_UUID));
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testSecurityAnalysis(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);

        // run security analysis on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, CONTINGENCY_LIST_NAME)
                .header(HEADER_USER_ID, "testUserId"))
                .andExpect(status().isForbidden());

        testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(server, studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SECURITY_ANALYSIS_RESULT_UUID, SECURITY_ANALYSIS_PARAMETERS);
        testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(server, studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID, null);

        // run additional security analysis for deletion test
        MockHttpServletRequestBuilder requestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, CONTINGENCY_LIST_NAME);

        requestBuilder.contentType(MediaType.APPLICATION_JSON)
            .content(objectWriter.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS))
            .header(HEADER_USER_ID, "testUserId");
        mockMvc.perform(requestBuilder).andExpect(status().isOk());

        consumeSAResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SECURITY_ANALYSIS_RESULT_UUID, server);

        //Test delete all results
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SECURITY_ANALYSIS.toString())
                .queryParam("dryRun", "true"))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        //Delete Security analysis results
        assertEquals(1, rootNetworkNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SECURITY_ANALYSIS.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/results\\?resultsUuids")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports")));
        assertEquals(0, rootNetworkNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
    }

    @Test
    void testResetUuidResultWhenSAFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, SECURITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), saFailedDestination);
            return resultUuid;
        }).when(studyService).runSecurityAnalysis(any(), any(), any(), any(), any());
        studyService.runSecurityAnalysis(studyEntity.getId(), List.of(), modificationNode.getId(), rootNetworkUuid, "");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, SECURITY_ANALYSIS));

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED, updateType);
    }

    //test security analysis on network 2 will fail
    @Test
    void testSecurityAnalysisFailedForNotification(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        //run failing security analysis (because in network 2)
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, firstRootNetworkUuid, modificationNode1Uuid, CONTINGENCY_LIST_NAME)
                        .header(HEADER_USER_ID, "testUserId"))
                        .andExpect(status().isOk());

        // failed security analysis
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED, updateType);

        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));

        /*
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, null);
        UUID studyUuid2 = studyEntity2.getId();
        UUID rootNodeUuid2 = getRootNode(studyUuid2).getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();
        UUID firstRootNetworkUuid2 = studyTestUtils.getOneRootNetworkUuid(studyUuid2);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid2, firstRootNetworkUuid2, modificationNode1Uuid2, CONTINGENCY_LIST_NAME)
                        .header(HEADER_USER_ID, "testUserId"))
                        .andExpect(status().isOk());

        // failed security analysis without receiver -> no failure message sent to frontend

        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));
    }

    private void testSecurityAnalysisWithRootNetworkUuidAndNodeUuid(final MockWebServer server, UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid, SecurityAnalysisParameters securityAnalysisParameters) throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        // security analysis not found
        mockMvc.perform(get("/v1/security-analysis/results/{resultUuid}", NOT_FOUND_SECURITY_ANALYSIS_UUID)).andExpect(status().isNotFound());

        // run security analysis
        MockHttpServletRequestBuilder requestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, rootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME);
        if (securityAnalysisParameters != null) {
            requestBuilder.contentType(MediaType.APPLICATION_JSON)
                        .content(objectWriter.writeValueAsString(securityAnalysisParameters));
        }
        requestBuilder.header(HEADER_USER_ID, "testUserId");
        mockMvc.perform(requestBuilder).andExpect(status().isOk());

        consumeSAResult(studyUuid, rootNetworkUuid, nodeUuid, resultUuid, server);

        // get limit types empty list
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyUuid, rootNetworkUuid, nodeUuid, LOAD_FLOW, "limit-types"))
                .andExpectAll(status().isOk(),
                        content().string("[]"));

        // get limit types
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyUuid, rootNetworkUuid, nodeUuid, SECURITY_ANALYSIS, "limit-types"))
                .andExpectAll(status().isOk(),
                        content().string(limitTypeJson));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/limit-types")));

        // get N security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort", studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.N)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_N_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/%s/n-result\\?page=.*size=.*filters=.*globalFilters=.*sort=.*".formatted(resultUuid))));

        // get NMK_CONTINGENCIES security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort", studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_CONTINGENCIES)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/%s/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*".formatted(resultUuid))));

        // get NMK_CONSTRAINTS security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&globalFilters=random_globalfilters&sort=random_sort", studyUuid, rootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_LIMIT_VIOLATIONS)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/%s/nmk-constraints-result/paged\\?page=.*size=.*filters=.*globalFilters=.*sort=.*".formatted(resultUuid))));

        // get security analysis status
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/status", studyUuid, rootNetworkUuid, nodeUuid)).andExpectAll(
                status().isOk()).andReturn();
        assertEquals("CONVERGED", result.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server).contains("/v1/results/%s/status".formatted(resultUuid)));

        // stop security analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/stop", studyUuid, rootNetworkUuid, nodeUuid).header("userId", "userId")).andExpect(status().isOk());

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));

        // get contingency count
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count?contingencyListName={contingencyListName}",
                studyUuid, rootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME))
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        Integer integerResponse = Integer.parseInt(resultAsString);
        Integer expectedResponse = Integer.parseInt(CONTINGENCIES_COUNT);
        assertEquals(expectedResponse, integerResponse);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/contingency-lists/count\\?ids=" + CONTINGENCY_LIST_NAME + "&networkUuid=" + NETWORK_UUID_STRING + ".*")));

        // get contingency count with no list
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/contingency-count",
                        studyUuid, rootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME))
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        integerResponse = Integer.parseInt(resultAsString);
        assertEquals(0, integerResponse.intValue());
    }

    private void consumeSAResult(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid, MockWebServer server) throws JsonProcessingException {
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));

        // consume SA result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeSaResult().accept(MessageBuilder.createMessage("", messageHeaders));

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, updateType);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID securityAnalysisParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                UUID.randomUUID(), null, securityAnalysisParametersUuid, null, null);
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

    @Test
    void testSecurityAnalysisParameters(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        assertNotNull(studyNameUserIdUuid);
        //get security analysis parameters but since it wasn't created before it will create the default parameters and then return them
        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSecurityAnalysisParametersUuid());
        Set<String> requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters/default")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID)));

        String mnBodyJson = objectWriter.writeValueAsString(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);

        //update the parameters
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson)).andExpect(
                status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID)));
        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSecurityAnalysisParametersUuid());
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        //get security analysis parameters but with an already registered securityAnalysisParametersUuid
        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID)));

        //same with set parameters
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson)).andExpect(
                status().isOk());
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID)));
        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSecurityAnalysisParametersUuid());
    }

    @Test
    void testCreateSecurityAnalysisParameters(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), null);
        UUID studyUuid = studyEntity.getId();
        assertNotNull(studyUuid);
        assertNull(studyEntity.getSecurityAnalysisParametersUuid());
        String mnBodyJson = objectWriter.writeValueAsString(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON);

        //test update parameters without having already created parameters -> should call create instead of update
        mockMvc.perform(post("/v1/studies/{studyUuid}/security-analysis/parameters", studyUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson))
                .andExpect(status().isOk());

        assertEquals(SECURITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyUuid).orElseThrow().getSecurityAnalysisParametersUuid());
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, output.receive(TIMEOUT, studyUpdateDestination).getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        Set<String> requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters")));
    }

    @Test
    void getResultZippedCsv(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID nodeUuid = modificationNode.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        /*
         * RUN SECURITY ANALYSIS START
         */
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyUuid, firstRootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME).header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk());

        consumeSAResult(studyUuid, firstRootNetworkUuid, nodeUuid, SECURITY_ANALYSIS_RESULT_UUID, server);

        /*
         * RUN SECURITY ANALYSIS END
         */
        // get N security analysis result zipped csv
        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}", studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.N).content(CSV_TRANSLATION_DTO_STRING)).andExpectAll(
            status().isOk()).andReturn();
        byte[] byteArrayResult = mvcResult.getResponse().getContentAsByteArray();
        assertArrayEquals(SECURITY_ANALYSIS_N_RESULT_CSV_ZIPPED, byteArrayResult);

        assertTrue(TestUtils.getRequestsWithBodyDone(1, server).stream().anyMatch(r ->
            r.getPath().matches("/v1/results/%s/n-result/csv".formatted(SECURITY_ANALYSIS_RESULT_UUID))
                && CSV_TRANSLATION_DTO_STRING.equals(r.getBody())
        ));

        // get NMK_CONTINGENCIES security analysis result zipped csv
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}", studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_CONTINGENCIES).content(CSV_TRANSLATION_DTO_STRING)).andExpectAll(
            status().isOk()).andReturn();
        byteArrayResult = mvcResult.getResponse().getContentAsByteArray();
        assertArrayEquals(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_CSV_ZIPPED, byteArrayResult);

        assertTrue(TestUtils.getRequestsWithBodyDone(1, server).stream().anyMatch(r ->
            r.getPath().matches("/v1/results/%s/nmk-contingencies-result/csv".formatted(SECURITY_ANALYSIS_RESULT_UUID))
                && CSV_TRANSLATION_DTO_STRING.equals(r.getBody())
        ));

        // get NMK_CONSTRAINTS security analysis result zipped csv
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}", studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.NMK_LIMIT_VIOLATIONS).content(CSV_TRANSLATION_DTO_STRING)).andExpectAll(
            status().isOk()).andReturn();
        byteArrayResult = mvcResult.getResponse().getContentAsByteArray();
        assertArrayEquals(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_CSV_ZIPPED, byteArrayResult);

        assertTrue(TestUtils.getRequestsWithBodyDone(1, server).stream().anyMatch(r ->
            r.getPath().matches("/v1/results/%s/nmk-constraints-result/csv".formatted(SECURITY_ANALYSIS_RESULT_UUID))
                && CSV_TRANSLATION_DTO_STRING.equals(r.getBody())
        ));
    }

    @Test
    void getResultZippedCsvNotFound(final MockWebServer server) throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_3, "node 1");
        UUID nodeUuid = modificationNode.getId();

        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

            /*
             * RUN SECURITY ANALYSIS START
             */
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, firstRootNetworkUuid, nodeUuid, CONTINGENCY_LIST_NAME).header(HEADER_USER_ID, "testUserId")).andExpect(status().isOk());

        consumeSAResult(studyUuid, firstRootNetworkUuid, nodeUuid, SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID, server);

        /*
         * RUN SECURITY ANALYSIS END
         */
        // get NOT_FOUND security analysis result zipped csv
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/security-analysis/result/csv?resultType={resultType}", studyUuid, firstRootNetworkUuid, nodeUuid, SecurityAnalysisResultType.N).content(CSV_TRANSLATION_DTO_STRING)).andExpectAll(
            status().isNotFound());

        assertTrue(TestUtils.getRequestsWithBodyDone(1, server).stream().anyMatch(r ->
            r.getPath().matches("/v1/results/%s/n-result/csv".formatted(SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID))
                && CSV_TRANSLATION_DTO_STRING.equals(r.getBody())
        ));
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination, saFailedDestination, saResultDestination, saStoppedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasNoProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasNoParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + NO_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasInvalidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + INVALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING))); // update existing with dft
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING))); // post duplicate ko
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasValidParamsInProfile(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SECURITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters/" + SECURITY_ANALYSIS_PARAMETERS_UUID_STRING))); // 2 requests: 1 get for provider and then delete existing
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }

    @Test
    void testResetSecurityAnalysisParametersUserHasValidParamsInProfileButNoExistingSecurityAnalysisParams(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/users/" + VALID_PARAMS_IN_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/parameters?duplicateFrom=" + PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))); // post duplicate ok
    }
}
