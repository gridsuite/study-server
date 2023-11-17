/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.security.SecurityAnalysisParameters;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SecurityAnalysisParametersValues;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.sensianalysis.SensitivityAnalysisParametersEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class SecurityAnalysisTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityAnalysisTest.class);

    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11111111-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "22222222-9594-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_SECURITY_ANALYSIS_UUID = "e3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String CONTINGENCY_LIST_NAME = "ls";
    private static final String SECURITY_ANALYSIS_N_RESULT_JSON = "{\"status\":\"CONVERGED\",\"limitViolationsResult\":{\"limitViolations\":[{\"subjectId\":\"l3\",\"limitType\":\"CURRENT\",\"acceptableDuration\":1200,\"limit\":10.0,\"limitReduction\":1.0,\"value\":11.0,\"side\":\"ONE\"}],\"actionsTaken\":[]},\"networkResult\":{\"branchResults\":[],\"busResults\":[],\"threeWindingsTransformerResults\":[]}}";
    private static final String SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON = "[{\"id\":\"l1\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l2\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l3\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l4\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l6\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l7\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l8\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"l9\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"la\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]},{\"id\":\"lb\",\"status\":\"CONVERGED\",\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}],\"constraints\":[{\"subjectId\":\"vl1\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0}]}]";
    private static final String SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON = "[{\"constraintId\":\"l3\",\"contingencies\":[]},{\"constraintId\":\"vl1\",\"contingencies\":[{\"contingencyId\":\"l1\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l1\",\"elementType\":\"BRANCH\"}]},{\"contingencyId\":\"l2\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l2\",\"elementType\":\"GENERATOR\"}]},{\"contingencyId\":\"l3\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l3\",\"elementType\":\"BUSBAR_SECTION\"}]},{\"contingencyId\":\"l4\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l4\",\"elementType\":\"LINE\"}]},{\"contingencyId\":\"l6\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l6\",\"elementType\":\"HVDC_LINE\"}]},{\"contingencyId\":\"l7\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l7\",\"elementType\":\"DANGLING_LINE\"}]},{\"contingencyId\":\"l8\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l8\",\"elementType\":\"SHUNT_COMPENSATOR\"}]},{\"contingencyId\":\"l9\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l9\",\"elementType\":\"TWO_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"la\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"l0\",\"elementType\":\"THREE_WINDINGS_TRANSFORMER\"}]},{\"contingencyId\":\"lb\",\"computationStatus\":\"CONVERGED\",\"limitType\":\"HIGH_VOLTAGE\",\"limitName\":\"\",\"side\":null,\"acceptableDuration\":0,\"limit\":400.0,\"value\":410.0,\"elements\":[{\"id\":\"la\",\"elementType\":\"STATIC_VAR_COMPENSATOR\"}]}]}]";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "\"CONVERGED\"";
    private static final String CONTINGENCIES_JSON = "[{\"id\":\"l1\",\"elements\":[{\"id\":\"l1\",\"type\":\"BRANCH\"}]}]";

    public static final String SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":0.0,\"lowVoltageProportionalThreshold\":0.0,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";
    public static final String SECURITY_ANALYSIS_UPDATED_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":90.0,\"lowVoltageProportionalThreshold\":0.6,\"highVoltageAbsoluteThreshold\":90.0,\"highVoltageProportionalThreshold\":0.1,\"flowProportionalThreshold\":0.2}";

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

    private static final long TIMEOUT = 1000;

    private static final SecurityAnalysisParameters SECURITY_ANALYSIS_PARAMETERS = new SecurityAnalysisParameters();

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
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String saResultDestination = "sa.result";
    private final String saStoppedDestination = "sa.stopped";
    private final String saFailedDestination = "sa.failed";

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
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        actionsService.setActionsServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), saResultDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + resultUuid + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/n-result\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_N_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/nmk-constraints-result/paged\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_3 + ".*") ? SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID : SECURITY_ANALYSIS_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                        .setHeader("resultUuid", resultUuid)
                        .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), saStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                         .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING)
                        || path.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(CONTINGENCIES_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/n-result\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_N_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/nmk-constraints-result/paged\\?page=.*size=.*filters=.*sort=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), saFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*")) {
                    input.send(MessageBuilder.withPayload("")
                        .build(), saFailedDestination);
                    return new MockResponse().setResponseCode(200).setBody("\"" + SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if ("/v1/results".equals(path)) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/treereports")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("1");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    @Test
    public void testSecurityAnalysis() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run security analysis on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyNameUserIdUuid, rootNodeUuid, CONTINGENCY_LIST_NAME).header("userId", "userId")).andExpect(status().isForbidden());

        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode1Uuid, UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID), SECURITY_ANALYSIS_PARAMETERS);
        testSecurityAnalysisWithNodeUuid(studyNameUserIdUuid, modificationNode3Uuid, UUID.fromString(SECURITY_ANALYSIS_OTHER_NODE_RESULT_UUID), null);

        // run additional security analysis for deletion test
        MockHttpServletRequestBuilder requestBuilder = post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
            studyNameUserIdUuid, modificationNode1Uuid, CONTINGENCY_LIST_NAME);

        requestBuilder.contentType(MediaType.APPLICATION_JSON)
            .content(objectWriter.writeValueAsString(SECURITY_ANALYSIS_PARAMETERS))
            .header("userId", "userId");
        mockMvc.perform(requestBuilder).andExpect(status().isOk());

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, updateType);

        securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));

        //Test delete all results
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SECURITY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(true)))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));

        //Delete Security analysis results
        assertEquals(1, networkModificationNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(ComputationType.SECURITY_ANALYSIS))
                .queryParam("dryRun", String.valueOf(false)))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/treereports")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull().size());
    }

    @Test
    @SneakyThrows
    public void testResetUuidResultWhenSAFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = mapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateSecurityAnalysisResultUuid(modificationNode.getId(), resultUuid);
        assertTrue(networkModificationTreeService.getSecurityAnalysisResultUuid(modificationNode.getId()).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getSecurityAnalysisResultUuid(modificationNode.getId()).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), saFailedDestination);
            return resultUuid;
        }).when(studyService).runSecurityAnalysis(any(), any(), any());
        studyService.runSecurityAnalysis(studyEntity.getId(), List.of(), modificationNode.getId());

        // Test reset uuid result in the database
        assertTrue(networkModificationTreeService.getSecurityAnalysisResultUuid(modificationNode.getId()).isEmpty());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED, updateType);
    }

    //test security analysis on network 2 will fail
    @Test
    public void testSecurityAnalysisFailedForNotification() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        //run failing security analysis (because in network 2)
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, modificationNode1Uuid, CONTINGENCY_LIST_NAME).header("userId", "userId"))
            .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        String uuidResponse = mapper.readValue(resultAsString, String.class);

        assertEquals(SECURITY_ANALYSIS_ERROR_NODE_RESULT_UUID, uuidResponse);

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

        /**
         *  what follows is mostly for test coverage -> a failed message without receiver is sent -> will be ignored by consumer
         */
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID);
        UUID studyUuid2 = studyEntity2.getId();
        UUID rootNodeUuid2 = getRootNode(studyUuid2).getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid2, modificationNode1Uuid2, CONTINGENCY_LIST_NAME).header("userId", "userId"))
            .andExpect(status().isOk());

        // failed security analysis without receiver -> no failure message sent to frontend

        // message sent by run and save controller to notify frontend security analysis is running and should update SA status
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));
    }

    private void testSecurityAnalysisWithNodeUuid(UUID studyUuid, UUID nodeUuid, UUID resultUuid, SecurityAnalysisParameters securityAnalysisParameters) throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        // security analysis not found
        mockMvc.perform(get("/v1/security-analysis/results/{resultUuid}", NOT_FOUND_SECURITY_ANALYSIS_UUID)).andExpect(status().isNotFound());

        // run security analysis
        MockHttpServletRequestBuilder requestBuilder = post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/run?contingencyListName={contingencyListName}",
                studyUuid, nodeUuid, CONTINGENCY_LIST_NAME);
        if (securityAnalysisParameters != null) {
            requestBuilder.contentType(MediaType.APPLICATION_JSON)
                        .content(objectWriter.writeValueAsString(securityAnalysisParameters))
                        .header("userId", "userId");
        }
        mvcResult = mockMvc.perform(requestBuilder).andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = mapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, resultUuid);

        Message<byte[]> securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> securityAnalysisUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT, updateType);

        securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, updateType);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*contingencyListName=" + CONTINGENCY_LIST_NAME + "&receiver=.*nodeUuid.*")));

        // get N security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&sort=random_sort", studyUuid, nodeUuid, SecurityAnalysisResultType.N)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_N_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches(String.format("/v1/results/%s/n-result\\?page=.*size=.*filters=.*sort=.*", resultUuid))));

        // get NMK_CONTINGENCIES security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&sort=random_sort", studyUuid, nodeUuid, SecurityAnalysisResultType.NMK_CONTINGENCIES)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_NMK_CONTINGENCIES_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches(String.format("/v1/results/%s/nmk-contingencies-result/paged\\?page=.*size=.*filters=.*sort=.*", resultUuid))));

        // get NMK_CONSTRAINTS security analysis result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/result?resultType={resultType}&page=0&size=10&filters=random_filters&sort=random_sort", studyUuid, nodeUuid, SecurityAnalysisResultType.NMK_LIMIT_VIOLATIONS)).andExpectAll(
            status().isOk(),
            content().string(SECURITY_ANALYSIS_NMK_CONSTRAINTS_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches(String.format("/v1/results/%s/nmk-constraints-result/paged\\?page=.*size=.*filters=.*sort=.*", resultUuid))));

        // get security analysis status
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/status", studyUuid, nodeUuid)).andExpectAll(
                status().isOk()).andReturn();
        assertEquals("CONVERGED", result.getResponse().getContentAsString());

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/results/%s/status", resultUuid)));

        // stop security analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/security-analysis/stop", studyUuid, nodeUuid).header("userId", "userId")).andExpect(status().isOk());

        securityAnalysisStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) securityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertTrue(updateType.equals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS) || updateType.equals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + resultUuid + "/stop\\?receiver=.*nodeUuid.*")));

        // get contingency count
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/contingency-count?contingencyListName={contingencyListName}",
                studyUuid, nodeUuid, CONTINGENCY_LIST_NAME))
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        Integer integerResponse = Integer.parseInt(resultAsString);
        assertEquals(integerResponse, Integer.valueOf(1));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/contingency-lists/" + CONTINGENCY_LIST_NAME + "/export\\?networkUuid=" + NETWORK_UUID_STRING + ".*")));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
            .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
            .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
            .dcPowerFactor(1.0)
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        SensitivityAnalysisParametersEntity defaultSensitivityParametersEntity = SensitivityAnalysisService.toEntity(SensitivityAnalysisService.getDefaultSensitivityAnalysisParametersValues());
        SecurityAnalysisParametersValues securityAnalysisParametersValues = SecurityAnalysisParametersValues.builder()
                .lowVoltageAbsoluteThreshold(0.0)
                .lowVoltageProportionalThreshold(0.0)
                .highVoltageProportionalThreshold(0.0)
                .highVoltageAbsoluteThreshold(0.0)
                .flowProportionalThreshold(0.1)
                .build();
        SecurityAnalysisParametersEntity securityAnalysisParametersEntity = SecurityAnalysisService.toEntity(securityAnalysisParametersValues);
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider,
                defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, securityAnalysisParametersEntity, defaultSensitivityParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
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
        return modificationNode;
    }

    @Test
    public void testSecurityAnalysisParameters() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID());
        UUID studyNameUserIdUuid = studyEntity.getId();
        //get security analysis parameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        //create security analysis Parameters
        SecurityAnalysisParametersValues securityAnalysisParametersValues = SecurityAnalysisParametersValues.builder()
                .lowVoltageAbsoluteThreshold(90)
                .lowVoltageProportionalThreshold(0.6)
                .highVoltageProportionalThreshold(0.1)
                .highVoltageAbsoluteThreshold(90)
                .flowProportionalThreshold(0.2)
                .build();
        String mnBodyJson = objectWriter.writeValueAsString(securityAnalysisParametersValues);

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mnBodyJson)).andExpect(
                status().isOk());

        //getting set values
        mockMvc.perform(get("/v1/studies/{studyUuid}/security-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(SECURITY_ANALYSIS_UPDATED_PARAMETERS_JSON));
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination, saFailedDestination, saResultDestination, saStoppedDestination);

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
