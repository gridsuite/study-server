/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.RequestWithBody;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.gridsuite.study.server.StudyException.Type.LINE_MODIFICATION_FAILED;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class NetworkModificationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final String SUBSTATION_ID_1 = "SUBSTATION_ID_1";
    private static final String VL_ID_1 = "VL_ID_1";

    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String POST = "POST";

    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";

    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");

    private static final String TEST_FILE = "testCase.xiidm";

    private static final long TIMEOUT = 1000;

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
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    //output destinations
    private String studyUpdateDestination = "study.update";

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        initMockBeans(network);

        server = new MockWebServer();

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

     // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);

        String substationModificationListAsString = mapper.writeValueAsString(List.of(
                EquipmentModificationInfos.builder().substationIds(Set.of()).uuid(UUID.fromString(MODIFICATION_UUID)).build()));

        String voltageLevelModificationListAsString = mapper.writeValueAsString(List.of(
                EquipmentModificationInfos.builder().substationIds(Set.of()).uuid(UUID.fromString(MODIFICATION_UUID)).build()));

        String voltageLevelDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id(VL_ID_1).name("VL_NAME_1").build()));
        String substationDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));

        EquipmentModificationInfos lineToSplitDeletion = EquipmentModificationInfos.builder()
                .type(ModificationType.EQUIPMENT_DELETION)
                .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
                .build();
        List<EquipmentModificationInfos> lineSplitResponseInfos = new ArrayList<>();
        lineSplitResponseInfos.add(lineToSplitDeletion);

        EquipmentModificationInfos lineToAttachTo = EquipmentModificationInfos.builder()
                .type(ModificationType.LINE_ATTACH_TO_VOLTAGE_LEVEL)
                .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
                .build();
        List<EquipmentModificationInfos> lineAttachResponseInfos = new ArrayList<>();
        lineAttachResponseInfos.add(lineToAttachTo);

        EquipmentModificationInfos linesToAttachToSplitLines = EquipmentModificationInfos.builder()
                .type(ModificationType.LINES_ATTACH_TO_SPLIT_LINES)
                .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
                .build();
        List<EquipmentModificationInfos> linesToAttachToSplitLinesResponseInfos = new ArrayList<>();
        lineAttachResponseInfos.add(linesToAttachToSplitLines);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/reports/.*")) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORT_TEST))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // variant build
                    input.send(MessageBuilder.withPayload("s1,s2").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.result");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // failed build
                    input.send(MessageBuilder.withPayload("").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.failed");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // failed build
                    return new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                } else if (path.matches("/v1/build/stop.*")) {
                    // stop variant build
                    input.send(MessageBuilder.withPayload("").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.stopped");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID) ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s4", "s5", "s6", "s7")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators[?]group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/LOAD/id/idLoadToDelete\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("equipmentId", "idLoadToDelete",
                            "equipmentType", "LOAD", "substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(substationDataAsString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                }  else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(voltageLevelDataAsString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-splits[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                                .setBody(mapper.writeValueAsString(lineSplitResponseInfos))
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-attach[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                                .setBody(mapper.writeValueAsString(lineAttachResponseInfos))
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines-attach-to-split-lines[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                                .setBody(mapper.writeValueAsString(linesToAttachToSplitLinesResponseInfos))
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(substationModificationListAsString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels[?]group=.*") && POST.equals(request.getMethod())) {
                    if (body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    } else {
                        return new MockResponse().setResponseCode(200)
                            .setBody(voltageLevelModificationListAsString)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators-modification[?]group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads(-modification)?\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    }
                } else if (path.matches("/v1/groups/.*/modifications[?]") && request.getMethod().equals("DELETE")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/") && request.getMethod().equals("PUT")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*")) {
                    if (body.peek().readUtf8().equals("lockout")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*")) {
                    if (body.peek().readUtf8().equals("trip")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*")) {
                    String bodyStr = body.peek().readUtf8();
                    if (bodyStr.equals("switchOn") || bodyStr.equals("energiseEndOne")) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    } else {
                        return new MockResponse().setResponseCode(500);
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*")) {
                    return new MockResponse().setResponseCode(500).setBody(LINE_MODIFICATION_FAILED.name());
                } else if (("/v1/results/invalidate-status?resultUuid=" + SECURITY_ANALYSIS_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SECURITY_ANALYSIS_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    private void initMockBeans(Network network) {
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenReturn(network);
    }

    @Test
    public void testBuildFailed() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");

        testBuildFailedWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuildError() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");

        testBuildErrorWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuild() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1.getId(), modificationGroupUuid2, "variant_2", "node 2");
        UUID modificationGroupUuid3 = UUID.randomUUID();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2.getId(), modificationGroupUuid3, "variant_3", "node 3");
        UUID modificationGroupUuid4 = UUID.randomUUID();
        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3.getId(), modificationGroupUuid4, "variant_4", "node 4");
        UUID modificationGroupUuid5 = UUID.randomUUID();
        NetworkModificationNode modificationNode5 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode4.getId(), modificationGroupUuid5, "variant_5", "node 5");

        /*
            root
             |
          modificationNode1
             |
          modificationNode2
             |
          modificationNode3
             |
          modificationNode4
             |
          modificationNode5
         */

        BuildInfos buildInfos = networkModificationTreeService.getBuildInfos(modificationNode5.getId());
        assertNull(buildInfos.getOriginVariantId());  // previous built node is root node
        assertEquals("variant_5", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid1, modificationGroupUuid2, modificationGroupUuid3, modificationGroupUuid4, modificationGroupUuid5), buildInfos.getModificationGroupUuids());

        modificationNode3.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT, studyUpdateDestination);

        buildInfos = networkModificationTreeService.getBuildInfos(modificationNode4.getId());
        assertEquals("variant_3", buildInfos.getOriginVariantId()); // variant to clone is variant associated to node
                                                                    // modificationNode3
        assertEquals("variant_4", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid4), buildInfos.getModificationGroupUuids());

        modificationNode2.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode2 as not built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode4.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode4 as built invalid
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode4);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode5.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode5 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode5);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode2 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId(), 2);

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode3.getId()));
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));

        modificationNode3.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode3 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode3.getId(), 3);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));
    }

    @Test
    public void testNetworkModificationSwitch() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        // update switch on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                studyNameUserIdUuid, rootNodeUuid, "switchId")).andExpect(status().isForbidden());

        // update switch on first modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                studyNameUserIdUuid, modificationNode1Uuid, "switchId")).andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(1, server);

        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));

        mvcResult = mockMvc.perform(get("/v1/studies").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResult = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResult.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update switch on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                        studyNameUserIdUuid, modificationNode2Uuid, "switchId")).andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)));

        // test build status on switch modification
        modificationNode1.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode1 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode2.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode2 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT, studyUpdateDestination);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                        studyNameUserIdUuid, modificationNode1Uuid, "switchId")).andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        output.receive(TIMEOUT, studyUpdateDestination);
        try {
            output.receive(TIMEOUT, studyUpdateDestination);
        } catch (Exception e) {
            throw e;
        }
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports/.*")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode1Uuid)); // modificationNode1
                                                                                                               // is
                                                                                                               // still
                                                                                                               // built
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode2Uuid)); // modificationNode2
                                                                                                                       // is
                                                                                                                       // now
                                                                                                                       // invalid
    }

    @Test
    public void testNetworkModificationEquipment() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNodeUuid, VARIANT_ID_2, "node 2");
        UUID modificationNodeUuid2 = modificationNode2.getId();

        //update equipment on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                rootNodeUuid).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isForbidden());

        //update equipment
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                        modificationNodeUuid).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
                .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")));

        mvcResult = mockMvc.perform(get("/v1/studies").header("userId", "userId").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResponse.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update equipment on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                        modificationNodeUuid2).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*&variantId=" + VARIANT_ID_2)));
    }

    @Test
    public void testCreateGenerator() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createGeneratorAttributes = "{\"generatorId\":\"generatorId1\",\"generatorName\":\"generatorName1\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create generator on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, rootNodeUuid).content(createGeneratorAttributes))
                .andExpect(status().isForbidden());

        // create generator on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createGeneratorAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create generator on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createGeneratorAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update generator creation
        String generatorAttributesUpdated = "{\"generatorId\":\"generatorId2\",\"generatorName\":\"generatorName2\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"150.0\",\"maxActivePower\":\"50.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/generators-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(generatorAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createGeneratorAttributes2 = "{\"generatorId\":\"generatorId3\",\"generatorName\":\"generatorName3\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create generator on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createGeneratorAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*") && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/generators-creation") && r.getBody().equals(generatorAttributesUpdated)));
    }

    @Test
    public void testCreateShuntsCompensator() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        String createShuntCompensatorAttributes = "{\"shuntCompensatorId\":\"shuntCompensatorId1\",\"shuntCompensatorName\":\"shuntCompensatorName1\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create shuntCompensator on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, rootNodeUuid).content(createShuntCompensatorAttributes))
            .andExpect(status().isForbidden());

        // create shuntCompensator on modification node child of root node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createShuntCompensatorAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // update shunt compensator creation
        String shuntCompensatorAttributesUpdated = "{\"shuntCompensatorId\":\"shuntCompensatorId2\",\"shuntCompensatorName\":\"shuntCompensatorName2\",\"voltageLevelId\":\"idVL2\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/shunt-compensators-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(shuntCompensatorAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createShuntCompensatorAttributes2 = "{\"shuntCompensatorId\":\"shuntCompensatorId3\",\"shuntCompensatorName\":\"shuntCompensatorName3\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create shunt compensator on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createShuntCompensatorAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream()
                .anyMatch(r -> r
                        .getPath().matches("/v1/networks/" + NETWORK_UUID_STRING
                                + "/shunt-compensators[?]group=.*&variantId=" + VARIANT_ID)
                        && r.getBody().equals(createShuntCompensatorAttributes)));
        assertTrue(requests.stream().anyMatch(
            r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/shunt-compensators-creation")
                        && r.getBody().equals(shuntCompensatorAttributesUpdated)));
    }

    @Test
    public void testCreateLine() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createLineAttributes = "{" + "\"lineId\":\"lineId1\"," + "\"lineName\":\"lineName1\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";

        // create line on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, rootNodeUuid).content(createLineAttributes))
                .andExpect(status().isForbidden());

        // create line on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, modificationNode1Uuid).content(createLineAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create line on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, modificationNode2Uuid).content(createLineAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update line creation
        String lineAttributesUpdated = "{" + "\"lineId\":\"lineId2\"," + "\"lineName\":\"lineName2\","
                + "\"seriesResistance\":\"54.0\"," + "\"seriesReactance\":\"55.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL2\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(lineAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createLineAttributes2 = "{" + "\"lineId\":\"lineId3\"," + "\"lineName\":\"lineName3\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create line on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createLineAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*") && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/lines-creation") && r.getBody().equals(lineAttributesUpdated)));
    }

    @Test
    public void testCreateTwoWindingsTransformer() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        // create 2WT on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, rootNodeUuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isForbidden());

        // create 2WT on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create 2WT on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update Two Windings Transformer creation
        String twoWindingsTransformerAttributesUpdated = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/two-windings-transformers-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(twoWindingsTransformerAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createTwoWindingsTransformerAttributes2 = "{\"equipmentId\":\"2wtId3\",\"equipmentName\":\"2wtName3\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create Two Windings Transformer on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createTwoWindingsTransformerAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/two-windings-transformers-creation") && r.getBody().equals(twoWindingsTransformerAttributesUpdated)));

    }

    @Test
    public void testChangeModificationActiveState() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");

        UUID modificationUuid = UUID.randomUUID();
        UUID nodeNotFoundUuid = UUID.randomUUID();

        // deactivate modification on modificationNode
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false",
                studyUuid, nodeNotFoundUuid, modificationUuid))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false",
                        studyUuid, modificationNode1.getId(), modificationUuid))
            .andExpect(status().isOk());

        AtomicReference<AbstractNode> node = new AtomicReference<>();
        node.set(networkModificationTreeService.getSimpleNode(modificationNode1.getId()));
        NetworkModificationNode modificationNode = (NetworkModificationNode) node.get();
        assertEquals(Set.of(modificationUuid), modificationNode.getModificationsToExclude());

        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());

        // reactivate modification
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=true",
                        studyUuid, modificationNode1.getId(), modificationUuid))
            .andExpect(status().isOk());

        node.set(networkModificationTreeService.getSimpleNode(modificationNode1.getId()));
        modificationNode = (NetworkModificationNode) node.get();
        assertTrue(modificationNode.getModificationsToExclude().isEmpty());

        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());
    }

    @Test
    public void deleteModificationRequest() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, VARIANT_ID, "node 1");
        createNetworkModificationNode(studyUuid, rootNodeUuid, VARIANT_ID_2, "node 2");
        NetworkModificationNode node3 = createNetworkModificationNode(studyUuid, modificationNode.getId(), "variant_3", "node 3");
        /*  root
           /   \
         node  modification node
                 \
                node3
            node is only there to test that when we update modification node, it is not in notifications list
         */
        UUID studyUuid1 = UUID.randomUUID();
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification?modificationsUuids={modificationUuid}",
                        studyUuid1, modificationNode.getId(), node3.getId()))
            .andExpect(status().isForbidden());

        checkEquipmentDeletingMessagesReceived(studyUuid1, modificationNode.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid1, modificationNode.getId());

        UUID modificationUuid = UUID.randomUUID();
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification?modificationsUuids={modificationUuid}",
                studyUuid, modificationNode.getId(), modificationUuid))
            .andExpect(status().isOk());

        assertTrue(TestUtils.getRequestsDone(1, server).stream()
                .anyMatch(r -> r.matches("/v1/groups/" + modificationNode.getModificationGroupUuid()
                        + "/modifications[?]modificationsUuids=.*" + modificationUuid + ".*")));
        checkEquipmentDeletingMessagesReceived(studyUuid, modificationNode.getId());
        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, modificationNode.getId());
    }

    @Test
    public void testUpdateLines() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        // change line status on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, rootNodeUuid, "line12").content("lockout").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isForbidden());

        // lockout line
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "line12").content("lockout").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId").content("lockout").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        // trip line
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                studyNameUserIdUuid, modificationNode1Uuid, "line23").content("trip").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId").content("trip").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        // energise line end
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "line13").content("energiseEndOne").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId").content("energiseEndTwo").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        // switch on line

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "line13").content("switchOn").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode1Uuid, "lineFailedId").content("switchOn").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        // switch on line on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines/{lineId}/status",
                        studyNameUserIdUuid, modificationNode2Uuid, "line13").content("switchOn").contentType(MediaType.TEXT_PLAIN_VALUE))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        var requests = TestUtils.getRequestsWithBodyDone(9, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line12/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line23/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("energiseEndOne")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("lockout")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("trip")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("energiseEndTwo")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/lineFailedId/status\\?group=.*") && r.getBody().equals("switchOn")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines/line13/status\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals("switchOn")));
    }

    @Test
    public void testCreateLoad() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        String createLoadAttributes = "{\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        // create load on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, rootNodeUuid).content(createLoadAttributes))
            .andExpect(status().isForbidden());

        // create load on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, modificationNode1Uuid).content(createLoadAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create load on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, modificationNode2Uuid).content(createLoadAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update load creation
        String loadAttributesUpdated = "{\"loadId\":\"loadId2\",\"loadName\":\"loadName2\",\"loadType\":\"UNDEFINED\",\"activePower\":\"50.0\",\"reactivePower\":\"25.0\",\"voltageLevelId\":\"idVL2\",\"busId\":\"idBus2\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(loadAttributesUpdated).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createLoadAttributes2 = "{\"loadId\":\"loadId3\",\"loadName\":\"loadName3\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        modificationNode3.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create load on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                        studyNameUserIdUuid, modificationNode3Uuid).content(createLoadAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*") && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createLoadAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/loads-creation") && r.getBody().equals(loadAttributesUpdated)));
    }

    @Test
    public void testModifyLoad() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNodeUuid2 = modificationNode2.getId();

        String loadModificationAttributes = "{\"equipmentId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"AUXILIARY\",\"activePower\":\"100.0\"}";

        // modify load on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, rootNodeUuid).content(loadModificationAttributes))
            .andExpect(status().isForbidden());

        // modify load on first modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, modificationNodeUuid).content(loadModificationAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        // modify load on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/loads",
                studyNameUserIdUuid, modificationNodeUuid2).content(loadModificationAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);

        // update load modification
        String loadAttributesUpdated = "{\"loadId\":\"loadId1\",\"loadType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/loads-modification", studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID).content(loadAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads-modification\\?group=.*") && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads-modification\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/loads-modification\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(loadModificationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/loads-modification") && r.getBody().equals(loadAttributesUpdated)));
    }

    @Test
    public void testModifyEquipment() throws Exception {
        var modificationType = ModificationType.GENERATOR_MODIFICATION;
        String modificationTypeUrl = ModificationType.getUriFromType(modificationType);
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNodeUuid2 = modificationNode2.getId();

        String equipmentModificationAttribute = "{\"equipmentId\":\"equipmentId\"}";

        // modify generator on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationType}", studyNameUserIdUuid, rootNodeUuid, modificationTypeUrl)
                .content(equipmentModificationAttribute))
            .andExpect(status().isForbidden());

        // modify generator on first modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationType}", studyNameUserIdUuid, modificationNodeUuid, modificationTypeUrl)
                .content(equipmentModificationAttribute))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        // modify generator on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationType}", studyNameUserIdUuid, modificationNodeUuid2, modificationTypeUrl)
                .content(equipmentModificationAttribute))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);

        // update generator modification
        String generatorAttributesUpdated = "{\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationType}/{modificationUuid}/", studyNameUserIdUuid, modificationNodeUuid, modificationTypeUrl, MODIFICATION_UUID)
                .content(generatorAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/" + modificationTypeUrl + "\\?group=.*") && r.getBody().equals(equipmentModificationAttribute)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/" + modificationTypeUrl + "\\?group=.*\\&variantId=" + VARIANT_ID) && r.getBody().equals(equipmentModificationAttribute)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/" + modificationTypeUrl + "\\?group=.*\\&variantId=" + VARIANT_ID_2) && r.getBody().equals(equipmentModificationAttribute)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/" + modificationTypeUrl) && r.getBody().equals(generatorAttributesUpdated)));

    }

    @Test
    public void testCreateSubstation() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createSubstationAttributes = "{\"substationId\":\"substationId1\",\"substationName\":\"substationName1\",\"country\":\"AD\"}";

        // create substation on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations",
                        studyNameUserIdUuid, rootNodeUuid).content(createSubstationAttributes))
            .andExpect(status().isForbidden());

        // create substation on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createSubstationAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create substation on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createSubstationAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update substation creation
        String substationAttributesUpdated = "{\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"FR\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/substations-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(substationAttributesUpdated).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createSubstationAttributes2 = "{\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"AD\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create substation on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/substations",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createSubstationAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*") && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/substations\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createSubstationAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/substations-creation") && r.getBody().equals(substationAttributesUpdated)));
    }

    @Test
    public void testCreateVoltageLevel() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createVoltageLevelAttributes = "{\"voltageLevelId\":\"voltageLevelId1\",\"voltageLevelName\":\"voltageLevelName1\""
            + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s1\"}";

        // create voltage level on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels",
                        studyNameUserIdUuid, rootNodeUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVoltageLevelAttributes))
            .andExpect(status().isForbidden());

        // create voltage level
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels",
                        studyNameUserIdUuid, modificationNode1Uuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createVoltageLevelAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create voltage level on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createVoltageLevelAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update voltage level creation
        String voltageLevelAttributesUpdated = "{\"voltageLevelId\":\"voltageLevelId2\",\"voltageLevelName\":\"voltageLevelName2\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/voltage-levels-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(voltageLevelAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createVoltageLevelAttributes2 = "{\"voltageLevelId\":\"voltageLevelId3\",\"voltageLevelName\":\"voltageLevelName3\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create voltage level on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/voltage-levels",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createVoltageLevelAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*") && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createVoltageLevelAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/voltage-levels-creation") && r.getBody().equals(voltageLevelAttributesUpdated)));
    }

    @SneakyThrows
    @Test
    public void testLineSplitWithVoltageLevel() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        VoltageLevelCreationInfos vl1 = VoltageLevelCreationInfos.builder()
                .equipmentId("vl1")
                .equipmentName("NewVoltageLevel")
                .nominalVoltage(379.3)
                .substationId("s1")
                .busbarSections(Collections.singletonList(new BusbarSectionCreationInfos("v1bbs", "BBS1", 1, 1)))
                .busbarConnections(Collections.emptyList())
                .build();
        LineSplitWithVoltageLevelInfos lineSplitWoVL = new LineSplitWithVoltageLevelInfos("line3", 10.0, vl1, null, "1.A",
                "nl1", "NewLine1", "nl2", "NewLine2");
        String lineSplitWoVLasJSON = mapper.writeValueAsString(lineSplitWoVL);

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-splits",
                studyNameUserIdUuid, modificationNodeUuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(lineSplitWoVLasJSON))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-splits",
                studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(lineSplitWoVLasJSON))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertEquals(2, requests.size());
        Optional<RequestWithBody> creationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-splits\\?group=.*")).findFirst();
        Optional<RequestWithBody> updateRequest = requests.stream().filter(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/line-splits")).findFirst();
        assertTrue(creationRequest.isPresent());
        assertTrue(updateRequest.isPresent());
        assertEquals(lineSplitWoVLasJSON, creationRequest.get().getBody());
        assertEquals(lineSplitWoVLasJSON, updateRequest.get().getBody());

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-splits",
                studyNameUserIdUuid, modificationNodeUuid)
            .content("bogus"))
            .andExpectAll(
                status().is5xxServerError(),
                content().string("400 BAD_REQUEST")
            );
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-splits",
                studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
            .content("bogus"))
            .andExpectAll(
                status().is5xxServerError(),
                content().string("400 BAD_REQUEST")
            );
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        requests = TestUtils.getRequestsWithBodyDone(2, server);
    }

    @SneakyThrows
    @Test
    public void testLineAttachToVoltageLevel() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        String createVoltageLevelAttributes = "{\"voltageLevelId\":\"vl1\",\"voltageLevelName\":\"voltageLevelName1\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s1\"}";

        String createLineAttributes = "{\"seriesResistance\":\"25\",\"seriesReactance\":\"12\"}";

        String createLineAttachToVoltageLevelAttributes = "{\"lineToAttachToId\": \"line3\", \"percent\":\"10\", \"mayNewVoltageLevelInfos\":" +
                createVoltageLevelAttributes + "\"attachmentLine\":\"" + createLineAttributes + "\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/line-attach",
                        studyNameUserIdUuid, modificationNodeUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLineAttachToVoltageLevelAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/line-attach",
                        studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLineAttachToVoltageLevelAttributes))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertEquals(2, requests.size());
        Optional<RequestWithBody> creationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/line-attach\\?group=.*")).findFirst();
        Optional<RequestWithBody> updateRequest = requests.stream().filter(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/line-attach-creation")).findFirst();
        assertTrue(creationRequest.isPresent());
        assertTrue(updateRequest.isPresent());
        assertEquals(createLineAttachToVoltageLevelAttributes, creationRequest.get().getBody());
        assertEquals(createLineAttachToVoltageLevelAttributes, updateRequest.get().getBody());
    }

    @SneakyThrows
    @Test
    public void testLinesAttachToSplitLines() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        String createLinesAttachToSplitLinesAttributes = "{\"lineToAttachTo1Id\":\"line1\",\"lineToAttachTo2Id\":\"line2\",\"attachedLineId\":\"line3\",\"voltageLevelId\":\"vl1\",\"bbsBusId\":\"v1bbs\",\"replacingLine1Id\":\"replacingLine1Id\",\"replacingLine1Name\":\"replacingLine1Name\",\"replacingLine2Id\":\"replacingLine2Id\",\"replacingLine2Name\":\"replacingLine2Name\"}";

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines-attach-to-split-lines",
                        studyNameUserIdUuid, modificationNodeUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLinesAttachToSplitLinesAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, Collections.EMPTY_SET);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-attach-to-split-lines",
                        studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLinesAttachToSplitLinesAttributes))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertEquals(2, requests.size());
        Optional<RequestWithBody> creationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines-attach-to-split-lines\\?group=.*")).findFirst();
        Optional<RequestWithBody> updateRequest = requests.stream().filter(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/lines-attach-to-split-lines-creation")).findFirst();
        assertTrue(creationRequest.isPresent());
        assertTrue(updateRequest.isPresent());
        assertEquals(createLinesAttachToSplitLinesAttributes, creationRequest.get().getBody());
        assertEquals(createLinesAttachToSplitLinesAttributes, updateRequest.get().getBody());

        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines-attach-to-split-lines",
                        studyNameUserIdUuid, modificationNodeUuid)
                        .content("bogus"))
                .andExpect(status().is5xxServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-attach-to-split-lines",
                        studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content("bogus"))
                .andExpectAll(status().is5xxServerError());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        TestUtils.getRequestsWithBodyDone(2, server);
    }

    @Test
    public void testReorderModification() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        UUID studyNameUserIdUuid1 = UUID.randomUUID();
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, UUID.randomUUID(), modification1, modification2))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                        studyNameUserIdUuid1, modificationNodeUuid, modification1, modification2))
            .andExpect(status().isForbidden());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);

        // switch the 2 modifications order (modification1 is set at the end, after modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}",
                        studyNameUserIdUuid, modificationNodeUuid, modification1))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        var requests = TestUtils.getRequestsWithBodyDone(1, server);
        Optional<RequestWithBody> switchModificationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/groups/" + modificationNode.getModificationGroupUuid() + "[?]action=MOVE")).findFirst();
        assertTrue(switchModificationRequest.isPresent());
        List<UUID> modificationUuidList = Collections.singletonList(modification1);
        String expectedBody = mapper.writeValueAsString(modificationUuidList);
        assertEquals(expectedBody, switchModificationRequest.get().getBody()); // modification1 is in the request body

        // switch back the 2 modifications order (modification1 is set before modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, modificationNodeUuid, modification1, modification2))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        Optional<RequestWithBody> switchBackModificationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/groups/" + modificationNode.getModificationGroupUuid() + "[?]action=MOVE&before=" + modification2)).findFirst();
        assertTrue(switchBackModificationRequest.isPresent());
        assertEquals(expectedBody, switchBackModificationRequest.get().getBody()); // modification1 is still in the request body
    }

    @Test
    public void testDuplicateModification() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "New node 1");
        UUID nodeUuid1 = node1.getId();
        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        String modificationUuidListBody = objectWriter.writeValueAsString(Arrays.asList(modification1, modification2));

        // Random/bad studyId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}",
                        UUID.randomUUID(), rootNodeUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody))
                .andExpect(status().isForbidden());

        // Random/bad nodeId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}",
                        studyUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody))
                .andExpect(status().isNotFound());

        // duplicate 2 modifications in node1
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}",
                        studyUuid, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkUpdateNodesMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);

        var requests = TestUtils.getRequestsWithBodyDone(1, server);
        Optional<RequestWithBody> duplicateModificationRequest = requests.stream().filter(r -> r.getPath().matches("/v1/groups/" + node1.getModificationGroupUuid() + "[?]action=DUPLICATE")).findFirst();
        assertTrue(duplicateModificationRequest.isPresent());
        List<UUID> expectedList = List.of(modification1, modification2);
        String expectedBody = mapper.writeValueAsString(expectedList);
        assertEquals(expectedBody, duplicateModificationRequest.get().getBody());
    }

    @Test
    public void testDeleteEquipment() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        // delete equipment on root node (not allowed)
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, rootNodeUuid, "LOAD", "idLoadToDelete"))
            .andExpect(status().isForbidden());

        // delete equipment on first modification node
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, modificationNode1Uuid, "LOAD", "idLoadToDelete"))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid,
                NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete", NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE,
                "LOAD", NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // delete equipment on second modification node
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/equipments/type/{equipmentType}/id/{equipmentId}",
                studyNameUserIdUuid, modificationNode2Uuid, "LOAD", "idLoadToDelete"))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid,
                NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete", NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE,
                "LOAD", NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*")));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*&variantId=" + VARIANT_ID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/equipments/type/.*/id/.*\\?group=.*&variantId=" + VARIANT_ID_2)));
    }

    @Test
    public void testNodesInvalidation() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1", BuildStatus.BUILT);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", BuildStatus.NOT_BUILT);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3", BuildStatus.BUILT);
        UUID modificationNode3Uuid = modificationNode3.getId();

        UUID node1ReportUuid = UUID.randomUUID();
        UUID node3ReportUuid = UUID.randomUUID();
        modificationNode1.setReportUuid(node1ReportUuid);
        modificationNode1.setSecurityAnalysisResultUuid(UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setSensitivityAnalysisResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        modificationNode3.setReportUuid(node3ReportUuid);

        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT, studyUpdateDestination);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT, studyUpdateDestination);

        var modificationType = ModificationType.GENERATOR_MODIFICATION;
        String modificationTypeUrl = ModificationType.getUriFromType(modificationType);
        String generatorAttributesUpdated = "{\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationType}/{modificationUuid}/", studyNameUserIdUuid, modificationNode1Uuid, modificationTypeUrl, MODIFICATION_UUID)
                        .content(generatorAttributesUpdated))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid, modificationNode3Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        var requests = TestUtils.getRequestsWithBodyDone(7, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/" + modificationTypeUrl) && r.getBody().equals(generatorAttributesUpdated)));
        assertEquals(2, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*")).count());
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID)));
    }

    private void testBuildWithNodeUuid(UUID studyUuid, UUID nodeUuid, int nbReportExpected) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        // Initial node update -> BUILDING
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // Successful ->  Node update -> BUILT
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_COMPLETED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        assertTrue(TestUtils.getRequestsDone(nbReportExpected, server).stream().allMatch(r -> r.contains("reports")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is built

        networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.BUILDING);

        // stop build
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build/stop", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT, studyUpdateDestination);
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_CANCELLED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid)); // node is not
                                                                                                      // built

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/build/stop\\?receiver=.*")));
    }

    // builds on network 2 will fail
    private void testBuildFailedWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // fail -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error message sent to frontend
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_FAILED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertTrue(TestUtils.getRequestsDone(1, server).iterator().next().contains("reports"));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is not built
    }

    // builds on network 3 will throw an error on networkmodificationservice call
    private void testBuildErrorWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isInternalServerError());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertTrue(TestUtils.getRequestsDone(1, server).iterator().next().contains("reports"));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is not built
    }

    private void checkEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedIdsSet);
    }

    private void checkEquipmentCreatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
    }

    private void checkEquipmentModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedIdsSet);
    }

    private void checkLineModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageLine = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageLine.getPayload()));
        MessageHeaders headersSwitch = messageLine.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_LINE, headersSwitch.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateNodesMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateEquipmentModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeId,
            Set<String> modifiedIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedIdsSet, headersStudyUpdate.get(headerUpdateTypeId));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentDeletingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_DELETING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkSwitchModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_SWITCH, headersSwitch.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentUpdatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentDeletedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            String headerUpdateTypeEquipmentType, String equipmentType, String headerUpdateTypeEquipmentId,
            String equipmentId, String headerUpdateTypeSubstationsIds, Set<String> modifiedSubstationsIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(equipmentType, headersStudyUpdate.get(headerUpdateTypeEquipmentType));
        assertEquals(equipmentId, headersStudyUpdate.get(headerUpdateTypeEquipmentId));
        assertEquals(modifiedSubstationsIdsSet, headersStudyUpdate.get(headerUpdateTypeSubstationsIds));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));

        // assert that the broker message has been sent for updating load flow status
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkNodesInvalidationMessagesReceived(UUID studyNameUserIdUuid, List<UUID> invalidatedNodes) {
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, invalidatedNodes);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParamters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseFormat, defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
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

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName);
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
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
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
        List<String> destinations = List.of(studyUpdateDestination);

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
