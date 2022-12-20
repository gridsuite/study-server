/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.IdentifiableType;
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
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.RequestWithBody;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.hamcrest.core.StringContains;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.service.NetworkModificationService.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;
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
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "\"CONVERGED\"";

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String SHORTCIRCUIT_ANALYSIS_RESULT_UUID = "72f94d64-4fc6-11ed-bdc3-0242ac120002";
    private static final String SHORTCIRCUIT_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";

    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");

    private static final String TEST_FILE = "testCase.xiidm";

    private static final String USER_ID_HEADER = "userId";

    private static final long TIMEOUT = 1000;

    private static final String URI_NETWORK_MODIF = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications";
    private static final String URI_NETWORK_MODIF_WITH_ID = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/{uuid}";

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    // old mock server
    private MockWebServer server;

    // new mock server (use this one to mock API calls)
    private WireMockServer wireMock;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper mapper;

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

    @Autowired
    private ShortCircuitService shortCircuitService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private StudyRepository studyRepository;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String elementUpdateDestination = "element.update";

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        initMockBeans(network);

        server = new MockWebServer();
        wireMock = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));

        // Start the mock servers
        server.start();
        wireMock.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        reportService.setReportServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        String baseUrlWireMock = wireMock.baseUrl();
        networkModificationService.setNetworkModificationServerBaseUri(baseUrlWireMock);

        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_STRING + "/build"))
                .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", "s1,s2", "destination", "build.result"))
                .willReturn(WireMock.ok()));
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_2_STRING + "/build"))
                .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", "", "destination", "build.failed"))
                .willReturn(WireMock.ok()));
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_3_STRING + "/build"))
                .willReturn(WireMock.serverError()));
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/build/stop"))
                .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", "", "destination", "build.stopped"))
                .willReturn(WireMock.ok()));

        wireMock.stubFor(WireMock.any(WireMock.urlPathMatching("/v1/groups/.*"))
                .willReturn(WireMock.ok()
                        .withBody(mapper.writeValueAsString(List.of(Map.of("substationIds", List.of("s1", "s2", "s3")))))
                        .withHeader("Content-Type", "application/json")));

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if (path.matches("/v1/reports/.*")) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORT_TEST))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
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
                } else if (("/v1/results/invalidate-status?resultUuid=" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
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
                modificationGroupUuid1, "variant_1", "node 1", "userId");

        testBuildFailedWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuildError() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", "userId");

        testBuildErrorWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuild() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", userId);
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1.getId(), modificationGroupUuid2, "variant_2", "node 2", userId);
        UUID modificationGroupUuid3 = UUID.randomUUID();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2.getId(), modificationGroupUuid3, "variant_3", "node 3", userId);
        UUID modificationGroupUuid4 = UUID.randomUUID();
        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3.getId(), modificationGroupUuid4, "variant_4", "node 4", userId);
        UUID modificationGroupUuid5 = UUID.randomUUID();
        NetworkModificationNode modificationNode5 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode4.getId(), modificationGroupUuid5, "variant_5", "node 5", userId);

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
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        buildInfos = networkModificationTreeService.getBuildInfos(modificationNode4.getId());
        assertEquals("variant_3", buildInfos.getOriginVariantId()); // variant to clone is variant associated to node
                                                                    // modificationNode3
        assertEquals("variant_4", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid4), buildInfos.getModificationGroupUuids());

        modificationNode2.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode2 as not built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode4.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode4 as built invalid
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode4, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode5.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode5 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode5, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode2 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId(), 1);

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode3.getId()));
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));

        modificationNode3.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode3 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode3.getId(), 1);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));
    }

    @Test
    public void testNetworkModificationSwitch() throws Exception {

        String responseBody = createEquipmentModificationInfosBody(ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION, "s1", "s2", "s3");
        stubNetworkModificationPost(responseBody);

        MvcResult mvcResult;
        String resultAsString;
        String userId = "userId";

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        Map<String, Object> body = Map.of(
                "type", ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION,
                "equipmentAttributeName", "open",
                "equipmentAttributeValue", true,
                "equipmentType", "SWITCH",
                "equipmentId", "switchId"
        );
        String bodyJson = mapper.writeValueAsString(body);

        // update switch on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // update switch on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(bodyJson, VARIANT_ID);

        mvcResult = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, userId)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResult = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResult.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid,  "UCTE"));

        // update switch on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(bodyJson, VARIANT_ID_2);

        // test build status on switch modification
        modificationNode1.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode1 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode2.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode2 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
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

        verifyPostWithBodyAndVariant(2, bodyJson, VARIANT_ID);
        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(1, server);
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

        String responseBody = createEquipmentModificationInfosBody(ModificationType.GROOVY_SCRIPT, "s4", "s5", "s6", "s7");
        stubNetworkModificationPost(responseBody);

        MvcResult mvcResult;
        String resultAsString;
        String userId = "userId";

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNodeUuid, VARIANT_ID_2, "node 2", userId);
        UUID modificationNodeUuid2 = modificationNode2.getId();

        Map<String, Object> body = Map.of(
                "type", ModificationType.GROOVY_SCRIPT,
                "script", "equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"
        );
        String bodyJson = mapper.writeValueAsString(body);

        //update equipment on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        //update equipment
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        verifyPostWithBody(bodyJson);

        mvcResult = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId").header(USER_ID_HEADER, "userId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() {
        });

        assertThat(csbiListResponse.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "UCTE"));

        // update equipment on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid2)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(bodyJson, VARIANT_ID_2);
    }

    @Test
    public void testCreateGenerator() throws Exception {
        String userId = "userId";

        String responseBody = createEquipmentModificationInfosBody(ModificationType.GENERATOR_CREATION, "s2");
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        Map<String, Object> body = new HashMap<>();
        body.put("type", ModificationType.GENERATOR_CREATION);
        body.put("generatorId", "generatorId1");
        body.put("generatorName", "generatorName1");
        body.put("energySource", "UNDEFINED");
        body.put("minActivePower", "100.0");
        body.put("maxActivePower", "200.0");
        body.put("ratedNominalPower", "50.0");
        body.put("activePowerSetpoint", "10.0");
        body.put("reactivePowerSetpoint", "20.0");
        body.put("voltageRegulatorOn", "true");
        body.put("voltageSetpoint", "225.0");
        body.put("voltageLevelId", "idVL1");
        body.put("busOrBusbarSectionId", "idBus1");
        String bodyJsonCreate = mapper.writeValueAsString(body);

        // create generator on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create generator on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create generator on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update generator creation
        body.replace("generatorId", "generatorId2");
        body.replace("generatorName", "generatorName2");
        body.replace("energySource", "UNDEFINED");
        body.replace("minActivePower", "150.0");
        body.replace("maxActivePower", "50.0");
        String bodyJsonUpdate = mapper.writeValueAsString(body);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(bodyJsonUpdate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        body.replace("generatorId", "generatorId3");
        body.replace("generatorName", "generatorName3");
        body.replace("energySource", "UNDEFINED");
        body.replace("minActivePower", "100.0");
        body.replace("maxActivePower", "200.0");
        String bodyJsonCreateBis = mapper.writeValueAsString(body);
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create generator on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreateBis).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(bodyJsonCreate, VARIANT_ID);
        verifyPostWithBodyAndVariant(bodyJsonCreate, VARIANT_ID_2);
        verifyPutWithBody(bodyJsonUpdate);
    }

    @Test
    public void testCreateShuntsCompensator() throws Exception {
        String userId = "userId";

        String responseBody = createEquipmentModificationInfosBody(ModificationType.SHUNT_COMPENSATOR_CREATION, "s2");
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();

        String createShuntCompensatorAttributes = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"shuntCompensatorId\":\"shuntCompensatorId1\",\"shuntCompensatorName\":\"shuntCompensatorName1\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create shuntCompensator on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createShuntCompensatorAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create shuntCompensator on modification node child of root node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createShuntCompensatorAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update shunt compensator creation
        String shuntCompensatorAttributesUpdated = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"shuntCompensatorId\":\"shuntCompensatorId2\",\"shuntCompensatorName\":\"shuntCompensatorName2\",\"voltageLevelId\":\"idVL2\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(shuntCompensatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createShuntCompensatorAttributes2 = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"shuntCompensatorId\":\"shuntCompensatorId3\",\"shuntCompensatorName\":\"shuntCompensatorName3\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create shunt compensator on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createShuntCompensatorAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createShuntCompensatorAttributes, VARIANT_ID);
        verifyPutWithBody(shuntCompensatorAttributesUpdated);
    }

    @Test
    public void testCreateLine() throws Exception {
        String userId = "userId";

        String responseBody = createEquipmentModificationInfosBody(ModificationType.LINE_CREATION, "s2");
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createLineAttributes = "{\"type\":\"" + ModificationType.LINE_CREATION
                + "\",\"lineId\":\"lineId1\"," + "\"lineName\":\"lineName1\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";

        // create line on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createLineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create line on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create line on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createLineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update line creation
        String lineAttributesUpdated = "{\"type\":\"" + ModificationType.LINE_CREATION
                + "\",\"lineId\":\"lineId2\"," + "\"lineName\":\"lineName2\","
                + "\"seriesResistance\":\"54.0\"," + "\"seriesReactance\":\"55.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL2\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(lineAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createLineAttributes2 = "{\"type\":\"" + ModificationType.LINE_CREATION
                + "\",\"lineId\":\"lineId3\"," + "\"lineName\":\"lineName3\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create line on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLineAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createLineAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(createLineAttributes, VARIANT_ID_2);
        verifyPutWithBody(lineAttributesUpdated);
    }

    @Test
    public void testCreateTwoWindingsTransformer() throws Exception {
        String userId = "userId";

        String responseBody = createEquipmentModificationInfosBody(ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, "s2");
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        // create 2WT on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create 2WT on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create 2WT on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update Two Windings Transformer creation
        String twoWindingsTransformerAttributesUpdated = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(twoWindingsTransformerAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createTwoWindingsTransformerAttributes2 = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId3\",\"equipmentName\":\"2wtName3\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create Two Windings Transformer on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createTwoWindingsTransformerAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createTwoWindingsTransformerAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(createTwoWindingsTransformerAttributes, VARIANT_ID_2);
        verifyPutWithBody(twoWindingsTransformerAttributesUpdated);
    }

    @Test
    public void testChangeModificationActiveState() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", "userId");

        UUID modificationUuid = UUID.randomUUID();
        UUID nodeNotFoundUuid = UUID.randomUUID();

        // deactivate modification on modificationNode
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false",
                studyUuid, nodeNotFoundUuid, modificationUuid).header(USER_ID_HEADER, "userId")
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=false",
                        studyUuid, modificationNode1.getId(), modificationUuid).header(USER_ID_HEADER, "userId")
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        AtomicReference<AbstractNode> node = new AtomicReference<>();
        node.set(networkModificationTreeService.getNode(modificationNode1.getId()));
        NetworkModificationNode modificationNode = (NetworkModificationNode) node.get();
        assertEquals(Set.of(modificationUuid), modificationNode.getModificationsToExclude());

        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());

        // reactivate modification
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=true",
                        studyUuid, modificationNode1.getId(), modificationUuid).header(USER_ID_HEADER, "userId")
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        node.set(networkModificationTreeService.getNode(modificationNode1.getId()));
        modificationNode = (NetworkModificationNode) node.get();
        assertTrue(modificationNode.getModificationsToExclude().isEmpty());

        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());
    }

    @Test
    public void deleteModificationRequest() throws Exception {
        String userId = "userId";

        wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/network-modifications"))
                .willReturn(WireMock.ok()));

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid, VARIANT_ID, "node 1", userId);
        createNetworkModificationNode(studyUuid, rootNodeUuid, VARIANT_ID_2, "node 2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(studyUuid, modificationNode.getId(), "variant_3", "node 3", userId);
        /*  root
           /   \
         node  modification node
                 \
                node3
            node is only there to test that when we update modification node, it is not in notifications list
         */
        UUID studyUuid1 = UUID.randomUUID();
        mockMvc.perform(delete(URI_NETWORK_MODIF, studyUuid1, modificationNode.getId())
                        .queryParam("uuids", node3.getId().toString())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        checkEquipmentDeletingMessagesReceived(studyUuid1, modificationNode.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid1, modificationNode.getId());

        UUID modificationUuid = UUID.randomUUID();
        mockMvc.perform(delete(URI_NETWORK_MODIF, studyUuid, modificationNode.getId())
                        .queryParam("uuids", modificationUuid.toString())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        wireMock.verify(1, WireMock.deleteRequestedFor(
                WireMock.urlPathMatching("/v1/network-modifications"))
                .withQueryParam("uuids", WireMock.equalTo(modificationUuid.toString())));
        checkEquipmentDeletingMessagesReceived(studyUuid, modificationNode.getId());
        checkUpdateNodesMessageReceived(studyUuid, List.of(modificationNode.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, modificationNode.getId());
    }

    @Test
    public void testUpdateLines() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        HashMap<String, Object> bodyLineInfos = new HashMap<>();
        bodyLineInfos.put("type", ModificationType.BRANCH_STATUS);
        bodyLineInfos.put("equipmentId", "line12");
        bodyLineInfos.put("action", "lockout");
        String bodyJsonCreate1 = mapper.writeValueAsString(bodyLineInfos);
        String responseBody1 = createEquipmentModificationInfosBody(ModificationType.BRANCH_STATUS, "s1", "s2");
        stubNetworkModificationPostWithBody(bodyJsonCreate1, responseBody1);

        // change line status on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJsonCreate1).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // lockout line
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate1).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate2 = mapper.writeValueAsString(bodyLineInfos);
        stubNetworkModificationPostWithError(bodyJsonCreate2);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // trip line
        bodyLineInfos.put("equipmentId", "line23");
        bodyLineInfos.put("action", "trip");
        String bodyJsonCreate3 = mapper.writeValueAsString(bodyLineInfos);
        String responseBody3 = createEquipmentModificationInfosBody(ModificationType.BRANCH_STATUS, "s2", "s3");
        stubNetworkModificationPostWithBody(bodyJsonCreate3, responseBody3);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate3).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate4 = mapper.writeValueAsString(bodyLineInfos);
        stubNetworkModificationPostWithError(bodyJsonCreate4);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate4).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // energise line end
        bodyLineInfos.put("equipmentId", "line13");
        bodyLineInfos.put("action", "energiseEndOne");
        String bodyJsonCreate5 = mapper.writeValueAsString(bodyLineInfos);
        String responseBody5 = createEquipmentModificationInfosBody(ModificationType.BRANCH_STATUS, "s1", "s3");
        stubNetworkModificationPostWithBody(bodyJsonCreate5, responseBody5);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate5).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate6 = mapper.writeValueAsString(bodyLineInfos);
        stubNetworkModificationPostWithError(bodyJsonCreate6);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate6).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        // switch on line

        bodyLineInfos.put("equipmentId", "line13");
        bodyLineInfos.put("action", "switchOn");
        String bodyJsonCreate7 = mapper.writeValueAsString(bodyLineInfos);
        String responseBody7 = responseBody5;
        stubNetworkModificationPostWithBody(bodyJsonCreate7, responseBody7);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate7).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate8 = mapper.writeValueAsString(bodyLineInfos);
        stubNetworkModificationPostWithError(bodyJsonCreate8);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate8).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isInternalServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // switch on line on second modification node
        String bodyJsonCreate9 = bodyJsonCreate7;
        String responseBody9 = responseBody5;
        stubNetworkModificationPostWithBody(bodyJsonCreate9, responseBody9);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJsonCreate9).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkLineModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s1", "s3"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBody(bodyJsonCreate1);
        verifyPostWithBody(bodyJsonCreate2);
        verifyPostWithBody(bodyJsonCreate3);
        verifyPostWithBody(bodyJsonCreate4);
        verifyPostWithBody(bodyJsonCreate5);
        verifyPostWithBody(bodyJsonCreate6);
        verifyPostWithBody(2, bodyJsonCreate7);
        verifyPostWithBodyAndVariant(bodyJsonCreate9, VARIANT_ID_2);
    }

    @Test
    public void testCreateLoad() throws Exception {
        String userId = "userId";

        String responseBody = createEquipmentModificationInfosBody(ModificationType.LOAD_CREATION, "s2");
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 3", userId);
        UUID modificationNode3Uuid = modificationNode3.getId();

        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        stubNetworkModificationPostWithBody(createLoadAttributes, responseBody);
        // create load on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create load on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create load on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update load creation
        String loadAttributesUpdated = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId2\",\"loadName\":\"loadName2\",\"loadType\":\"UNDEFINED\",\"activePower\":\"50.0\",\"reactivePower\":\"25.0\",\"voltageLevelId\":\"idVL2\",\"busId\":\"idBus2\"}";
        stubNetworkModificationPutWithBody(loadAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(loadAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createLoadAttributes2 = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId3\",\"loadName\":\"loadName3\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        modificationNode3.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        stubNetworkModificationPostWithBody(createLoadAttributes2, responseBody);
        // create load on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode3Uuid)
                        .content(createLoadAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createLoadAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(createLoadAttributes, VARIANT_ID_2);
        verifyPutWithBody(loadAttributesUpdated);
    }

    @Test
    public void testModifyLoad() throws Exception {
        String userId = "userId";
        String responseBody = createEquipmentModificationInfosBody(ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, "s2");
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNodeUuid2 = modificationNode2.getId();

        String loadModificationAttributes = "{\"type\":\"" + ModificationType.LOAD_MODIFICATION + "\",\"equipmentId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"AUXILIARY\",\"activePower\":\"100.0\"}";

        // modify load on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(loadModificationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // modify load on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(loadModificationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // modify load on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid2)
                        .content(loadModificationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update load modification
        String loadAttributesUpdated = "{\"type\":\"" + ModificationType.LOAD_MODIFICATION + "\",\"loadId\":\"loadId1\",\"loadType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(loadAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(loadModificationAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(loadModificationAttributes, VARIANT_ID_2);
        verifyPutWithBody(loadAttributesUpdated);
    }

    @Test
    public void testModifyEquipment() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNodeUuid2 = modificationNode2.getId();

        String equipmentModificationAttribute = "{\"type\":\"" + ModificationType.GENERATOR_MODIFICATION + "\",\"equipmentId\":\"equipmentId\"}";
        String responseBody = createEquipmentModificationInfosBody(ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION, "s2");
        stubNetworkModificationPostWithBody(equipmentModificationAttribute, responseBody);
        // modify generator on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // modify generator on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // modify generator on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid2)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update generator modification
        String generatorAttributesUpdated = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        stubNetworkModificationPutWithBody(generatorAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(generatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(equipmentModificationAttribute, VARIANT_ID);
        verifyPostWithBodyAndVariant(equipmentModificationAttribute, VARIANT_ID_2);
        verifyPutWithBody(generatorAttributesUpdated);
    }

    @Test
    public void testCreateSubstation() throws Exception {
        String userId = "userId";
        String substationDataAsString = mapper.writeValueAsString(List.of(
            EquipmentModificationInfos.builder().type(ModificationType.SUBSTATION_CREATION).equipmentId(SUBSTATION_ID_1)
                .equipmentType(IdentifiableType.SUBSTATION.name())
                .build()));
        stubNetworkModificationPost(substationDataAsString);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createSubstationAttributes = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId1\",\"substationName\":\"substationName1\",\"country\":\"AD\"}";

        // create substation on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create substation on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create substation on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update substation creation
        String substationAttributesUpdated = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"FR\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(substationAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createSubstationAttributes2 = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"AD\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create substation on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createSubstationAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createSubstationAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(createSubstationAttributes, VARIANT_ID_2);
        verifyPutWithBody(substationAttributesUpdated);
    }

    @Test
    public void testCreateVoltageLevel() throws Exception {
        String userId = "userId";
        String voltageLevelDataAsString = mapper.writeValueAsString(List.of(
            EquipmentModificationInfos.builder().type(ModificationType.VOLTAGE_LEVEL_CREATION).equipmentId(VL_ID_1)
                .equipmentType(IdentifiableType.VOLTAGE_LEVEL.name())
                .build()));
        stubNetworkModificationPost(voltageLevelDataAsString);
        stubNetworkModificationPut();

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createVoltageLevelAttributes = "{\"type\":\"" + ModificationType.VOLTAGE_LEVEL_CREATION + "\",\"voltageLevelId\":\"voltageLevelId1\",\"voltageLevelName\":\"voltageLevelName1\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s1\"}";

        // create voltage level on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create voltage level
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // create voltage level on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, new HashSet<>());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update voltage level creation
        String voltageLevelAttributesUpdated = "{\"type\":\"" + ModificationType.VOLTAGE_LEVEL_CREATION + "\",\"voltageLevelId\":\"voltageLevelId2\",\"voltageLevelName\":\"voltageLevelName2\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(voltageLevelAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        String createVoltageLevelAttributes2 = "{\"type\":\"" + ModificationType.VOLTAGE_LEVEL_CREATION + "\",\"voltageLevelId\":\"voltageLevelId3\",\"voltageLevelName\":\"voltageLevelName3\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create voltage level on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createVoltageLevelAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        verifyPostWithBodyAndVariant(createVoltageLevelAttributes, VARIANT_ID);
        verifyPostWithBodyAndVariant(createVoltageLevelAttributes, VARIANT_ID_2);
        verifyPutWithBody(voltageLevelAttributesUpdated);
    }

    @SneakyThrows
    @Test
    public void testLineSplitWithVoltageLevel() {
        String userId = "userId";
        EquipmentModificationInfos lineToSplitDeletion = EquipmentModificationInfos.builder()
                .type(ModificationType.EQUIPMENT_DELETION)
                .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
                .build();
        List<EquipmentModificationInfos> lineSplitResponseInfos = new ArrayList<>();
        lineSplitResponseInfos.add(lineToSplitDeletion);
        String responseBody = mapper.writeValueAsString(lineSplitResponseInfos);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
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
        lineSplitWoVL.setType(ModificationType.LINE_SPLIT_WITH_VOLTAGE_LEVEL);
        String lineSplitWoVLasJSON = mapper.writeValueAsString(lineSplitWoVL);
        stubNetworkModificationPostWithBody(lineSplitWoVLasJSON, responseBody);
        stubNetworkModificationPutWithBody(lineSplitWoVLasJSON);

        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(lineSplitWoVLasJSON).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(lineSplitWoVLasJSON).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBody(lineSplitWoVLasJSON);
        verifyPutWithBody(lineSplitWoVLasJSON);

        String badBody = "{\"type\":\"" + ModificationType.LINE_SPLIT_WITH_VOLTAGE_LEVEL + "\",\"bogus\":\"bogus\"}";
        stubNetworkModificationPostWithBodyAndError(badBody);
        stubNetworkModificationPutWithBodyAndError(badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(
                        status().is5xxServerError(),
                        content().string("400 BAD_REQUEST"));
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(
                        status().is5xxServerError(),
                        content().string("400 BAD_REQUEST"));
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        verifyPostWithBody(badBody);
        verifyPutWithBody(badBody);
    }

    @SneakyThrows
    @Test
    public void testLineAttachToVoltageLevel() {
        String userId = "userId";

        EquipmentModificationInfos lineToAttachTo = EquipmentModificationInfos.builder()
                .type(ModificationType.LINE_ATTACH_TO_VOLTAGE_LEVEL)
                .equipmentId("line3").equipmentType("LINE").substationIds(Set.of("s1", "s2"))
                .build();
        List<EquipmentModificationInfos> lineAttachResponseInfos = new ArrayList<>();
        lineAttachResponseInfos.add(lineToAttachTo);
        String responseBody = mapper.writeValueAsString(lineAttachResponseInfos);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        String createVoltageLevelAttributes = "{\"voltageLevelId\":\"vl1\",\"voltageLevelName\":\"voltageLevelName1\""
                + ",\"nominalVoltage\":\"379.1\",\"substationId\":\"s1\"}";

        String createLineAttributes = "{\"seriesResistance\":\"25\",\"seriesReactance\":\"12\"}";

        String createLineAttachToVoltageLevelAttributes = "{\"type\":\"" + ModificationType.LINE_ATTACH_TO_VOLTAGE_LEVEL + "\",\"lineToAttachToId\":\"line3\",\"percent\":\"10\",\"mayNewVoltageLevelInfos\":" +
                createVoltageLevelAttributes + ",\"attachmentLine\":" + createLineAttributes + "}";

        stubNetworkModificationPostWithBody(createLineAttachToVoltageLevelAttributes, responseBody);
        stubNetworkModificationPutWithBody(createLineAttachToVoltageLevelAttributes);

        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(createLineAttachToVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, ImmutableSet.of("s1", "s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(createLineAttachToVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBody(createLineAttachToVoltageLevelAttributes);
        verifyPutWithBody(createLineAttachToVoltageLevelAttributes);
    }

    @SneakyThrows
    @Test
    public void testLinesAttachToSplitLines() {
        String userId = "userId";

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
        String responseBody = mapper.writeValueAsString(linesToAttachToSplitLinesResponseInfos);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        String createLinesAttachToSplitLinesAttributes = "{\"type\":\"" + ModificationType.LINES_ATTACH_TO_SPLIT_LINES + "\",\"lineToAttachTo1Id\":\"line1\",\"lineToAttachTo2Id\":\"line2\",\"attachedLineId\":\"line3\",\"voltageLevelId\":\"vl1\",\"bbsBusId\":\"v1bbs\",\"replacingLine1Id\":\"replacingLine1Id\",\"replacingLine1Name\":\"replacingLine1Name\",\"replacingLine2Id\":\"replacingLine2Id\",\"replacingLine2Name\":\"replacingLine2Name\"}";

        stubNetworkModificationPostWithBody(createLinesAttachToSplitLinesAttributes, responseBody);
        stubNetworkModificationPutWithBody(createLinesAttachToSplitLinesAttributes);

        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(createLinesAttachToSplitLinesAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, Set.of());
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(createLinesAttachToSplitLinesAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBody(createLinesAttachToSplitLinesAttributes);
        verifyPutWithBody(createLinesAttachToSplitLinesAttributes);

        String badBody = "{\"type\":\"" + ModificationType.LINES_ATTACH_TO_SPLIT_LINES + "\",\"bogus\":\"bogus\"}";
        stubNetworkModificationPostWithBodyAndError(badBody);
        stubNetworkModificationPutWithBodyAndError(badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().is5xxServerError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().is5xxServerError());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        verifyPostWithBody(badBody);
        verifyPutWithBody(badBody);
    }

    @SneakyThrows
    @Test
    @DisplayName("Should throw an exception when the body of a network modification is wrongly formatted")
    public void testNetworkModificationBodyBadFormat() {

        // setup
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode1.getId();

        // bad json format
        String bodyWithBadJsonFormat = "{,}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(bodyWithBadJsonFormat).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isBadRequest(),
                content().string(StringContains.containsString("Unexpected")));

        // missing a ','
        String bodyWithBadFormat = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"equipmentId\":\"equipId\"\"equipmentName\":\"equipName\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyWithBadFormat).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(status().isBadRequest(),
                        content().string(StringContains.containsString("Unexpected character")));

        // missing a '"'
        bodyWithBadFormat = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"equipmentId\":\"equipId,\"equipmentName\":\"equipName\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyWithBadFormat).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(status().isBadRequest(),
                        content().string(StringContains.containsString("Unexpected character")));
    }

    @SneakyThrows
    @Test
    @DisplayName("Should throw an exception when the body of a network modification doesn't have a known type")
    public void testNetworkModificationBodyBadType() {

        // setup
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode1.getId();

        // missing type
        String bodyWithMissingType = "{}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(bodyWithMissingType).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isBadRequest(),
                content().string(StringContains.containsString("missing type id property 'type'")));

        // missing type
        bodyWithMissingType = "{\"equipmentId\":\"equipId\",\"equipmentName\":\"equipName\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyWithMissingType).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(status().isBadRequest(),
                        content().string(StringContains.containsString("missing type id property 'type'")));

        // bad type
        String bodyWithBadType = "{\"type\":\"" + "VERY_BAD_TYPE" + "\",\"equipmentId\":\"equipId\",\"equipmentName\":\"equipName\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyWithBadType).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(status().isBadRequest(),
                    content().string(StringContains.containsString("Could not resolve type id 'VERY_BAD_TYPE'")));
    }

    @Test
    public void testReorderModification() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node", userId);
        UUID modificationNodeUuid = modificationNode.getId();

        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        UUID studyNameUserIdUuid1 = UUID.randomUUID();
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, UUID.randomUUID(), modification1, modification2).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                        studyNameUserIdUuid1, modificationNodeUuid, modification1, modification2).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);

        // switch the 2 modifications order (modification1 is set at the end, after modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}",
                        studyNameUserIdUuid, modificationNodeUuid, modification1).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        List<UUID> modificationUuidList = Collections.singletonList(modification1);
        String expectedBody = mapper.writeValueAsString(modificationUuidList);
        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(
                        "/v1/groups/" + modificationNode.getModificationGroupUuid() + "?action=MOVE&originGroupUuid=" + modificationNode.getModificationGroupUuid()))
                .withRequestBody(WireMock.equalToJson(expectedBody))); // modification1 is in the request body

        // switch back the 2 modifications order (modification1 is set before modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, modificationNodeUuid, modification1, modification2).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(
                        "/v1/groups/" + modificationNode.getModificationGroupUuid() + "?action=MOVE&originGroupUuid=" + modificationNode.getModificationGroupUuid() + "&before=" + modification2))
                .withRequestBody(WireMock.equalToJson(expectedBody))); // modification1 is still in the request body
    }

    @Test
    public void testDuplicateModification() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "New node 1", "userId");
        UUID nodeUuid1 = node1.getId();
        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        String modificationUuidListBody = mapper.writeValueAsString(Arrays.asList(modification1, modification2));

        // Random/bad studyId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        UUID.randomUUID(), rootNodeUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());

        // Random/bad nodeId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        studyUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        // duplicate 2 modifications in node1
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        studyUuid, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkUpdateNodesMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);
        checkElementUpdatedMessageSent(studyUuid, userId);

        List<UUID> expectedList = List.of(modification1, modification2);
        String expectedBody = mapper.writeValueAsString(expectedList);
        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(
                        "/v1/groups/" + node1.getModificationGroupUuid() + "?action=COPY"))
                .withRequestBody(WireMock.equalToJson(expectedBody)));
    }

    @Test
    public void testCutAndPasteModification() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "New node 1", userId);
        UUID nodeUuid1 = node1.getId();
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "New node 2", userId);
        UUID nodeUuid2 = node2.getId();
        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        String modificationUuidListBody = mapper.writeValueAsString(Arrays.asList(modification1, modification2));

        // Random/bad studyId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        UUID.randomUUID(), rootNodeUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());

        // Random/bad nodeId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

     // move 2 modifications within node 1
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, nodeUuid1, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkUpdateNodesMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);

        List<UUID> expectedList = List.of(modification1, modification2);
        String expectedBody = mapper.writeValueAsString(expectedList);
        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(
                        "/v1/groups/" + node1.getModificationGroupUuid() + "?action=MOVE&originGroupUuid=" + node1.getModificationGroupUuid()))
                .withRequestBody(WireMock.equalToJson(expectedBody)));

        // move 2 modifications from node1 to node2
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, nodeUuid2, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid2);
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkUpdateNodesMessageReceived(studyUuid, List.of(nodeUuid2));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid2);
        checkUpdateNodesMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid2);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);
        checkElementUpdatedMessageSent(studyUuid, userId);
        checkElementUpdatedMessageSent(studyUuid, userId);

        expectedList = List.of(modification1, modification2);
        expectedBody = mapper.writeValueAsString(expectedList);
        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlEqualTo(
                        "/v1/groups/" + node1.getModificationGroupUuid() + "?action=MOVE&originGroupUuid=" + node1.getModificationGroupUuid()))
                .withRequestBody(WireMock.equalToJson(expectedBody)));

        // move modification without defining originNodeUuid
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=MOVE",
                        studyUuid, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testDeleteEquipment() throws Exception {
        String userId = "userId";

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        Map<String, Object> body = Map.of(
                "type", ModificationType.EQUIPMENT_DELETION,
                "equipmentId", "idLoadToDelete",
                "equipmentType", "LOAD",
                "substationIds", List.of("s2"));
        String responseBody = mapper.writeValueAsString(List.of(body));
        stubNetworkModificationPost(responseBody);
        stubNetworkModificationPut();

        String bodyJson = mapper.writeValueAsString(body);

        // delete equipment on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // delete equipment on first modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid,
                NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete", NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE,
                "LOAD", NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // delete equipment on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentDeletedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid,
                NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_ID, "idLoadToDelete", NotificationService.HEADER_UPDATE_TYPE_DELETED_EQUIPMENT_TYPE,
                "LOAD", NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        // update equipment deletion
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPostWithBodyAndVariant(bodyJson, VARIANT_ID);
        verifyPostWithBodyAndVariant(bodyJson, VARIANT_ID_2);
        verifyPutWithBody(bodyJson);
    }

    @Test
    public void testNodesInvalidation() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1", BuildStatus.BUILT, userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", BuildStatus.NOT_BUILT, userId);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3", BuildStatus.BUILT, userId);
        UUID modificationNode3Uuid = modificationNode3.getId();

        UUID node1ReportUuid = UUID.randomUUID();
        UUID node3ReportUuid = UUID.randomUUID();
        modificationNode1.setReportUuid(node1ReportUuid);
        modificationNode1.setSecurityAnalysisResultUuid(UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setSensitivityAnalysisResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setShortCircuitAnalysisResultUuid(UUID.fromString(SHORTCIRCUIT_ANALYSIS_RESULT_UUID));
        modificationNode3.setReportUuid(node3ReportUuid);

        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        String generatorAttributesUpdated = "{\"type\":\"" + ModificationType.GENERATOR_MODIFICATION + "\",\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        stubNetworkModificationPutWithBody(generatorAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(generatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid, modificationNode3Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        verifyPutWithBody(generatorAttributesUpdated);
        var requests = TestUtils.getRequestsWithBodyDone(8, server);
        assertEquals(2, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*")).count());
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID)));
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
        wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_STRING + "/build"))
                .withQueryParam(QUERY_PARAM_RECEIVER, WireMock.matching(".*"))
        );

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

        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo("/v1/build/stop"))
                .withQueryParam(QUERY_PARAM_RECEIVER, WireMock.matching(".*"))
        );
        //TODO remove after refactoring (it's here because there is several tests inside the same test method
        wireMock.resetRequests();
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
        wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_2_STRING + "/build"))
                .withQueryParam(QUERY_PARAM_RECEIVER, WireMock.matching(".*"))
        );

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
        wireMock.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_3_STRING + "/build"))
                .withQueryParam(QUERY_PARAM_RECEIVER, WireMock.matching(".*"))
        );

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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
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
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseFormat, defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {

        return mapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    private String createEquipmentModificationInfosBody(ModificationType modificationType, String... substationIds) throws Exception {
        EquipmentModificationInfos equipmentModificationInfos = EquipmentModificationInfos.builder()
                .type(modificationType)
                .substationIds(Set.of(substationIds))
                .build();
        return mapper.writeValueAsString(List.of(equipmentModificationInfos));
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                UUID modificationGroupUuid, String variantId, String nodeName, String userId) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT, userId);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus, String userId) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = mapper.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).header(USER_ID_HEADER, userId).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() throws IOException {
        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(List.of(studyUpdateDestination), output);

        try {
            // it returns an exception if a request was not matched by wireMock, but does not complain if it was not verified by 'verify'
            wireMock.checkForUnmatchedRequests();
        } finally {
            wireMock.shutdown();
        }

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }

    private void stubNetworkModificationPost(String responseBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
                .willReturn(WireMock.ok()
                        .withBody(responseBody)
                        .withHeader("Content-Type", "application/json")));
    }

    private void stubNetworkModificationPostWithBody(String requestBody, String responseBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(WireMock.ok()
                        .withBody(responseBody)
                        .withHeader("Content-Type", "application/json")));
    }

    private void stubNetworkModificationPostWithError(String requestBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(WireMock.serverError()));
    }

    private void stubNetworkModificationPostWithBodyAndError(String requestBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(WireMock.badRequest()));
    }

    private void stubNetworkModificationPut() {
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + MODIFICATION_UUID))
                .willReturn(WireMock.ok()));
    }

    private void stubNetworkModificationPutWithBody(String requestBody) {
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + MODIFICATION_UUID))
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(WireMock.ok()));
    }

    private void stubNetworkModificationPutWithBodyAndError(String requestBody) {
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + MODIFICATION_UUID))
                .withRequestBody(WireMock.equalToJson(requestBody))
                .willReturn(WireMock.badRequest()));
    }

    private void verifyPostWithBody(String requestBody) {
        verifyPostWithBody(1, requestBody);
    }

    private void verifyPostWithBody(Integer count, String requestBody) {
        wireMock.verify(count, WireMock.postRequestedFor(WireMock.urlPathEqualTo(
                        "/v1/network-modifications"))
                .withQueryParam("networkUuid", WireMock.equalTo(NETWORK_UUID_STRING))
                .withQueryParam("groupUuid", WireMock.matching(".*"))
                .withRequestBody(WireMock.equalToJson(requestBody)));
    }

    private void verifyPostWithBodyAndVariant(String requestBody, String variantId) {
        verifyPostWithBodyAndVariant(1, requestBody, variantId);
    }

    private void verifyPostWithBodyAndVariant(Integer count, String requestBody, String variantId) {
        wireMock.verify(count, WireMock.postRequestedFor(WireMock.urlPathEqualTo(
                        "/v1/network-modifications"))
                .withQueryParam("networkUuid", WireMock.equalTo(NETWORK_UUID_STRING))
                .withQueryParam("groupUuid", WireMock.matching(".*"))
                .withQueryParam("variantId", WireMock.equalTo(variantId))
                .withRequestBody(WireMock.equalToJson(requestBody)));
    }

    private void verifyPutWithBody(String requestBody) {
        wireMock.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo(
                        "/v1/network-modifications/" + MODIFICATION_UUID))
                .withRequestBody(WireMock.equalToJson(requestBody)));
    }

}
