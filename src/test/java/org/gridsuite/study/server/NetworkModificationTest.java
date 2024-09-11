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
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
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
import org.gridsuite.study.server.dto.Report;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.notification.dto.NetworkImpactsInfos;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.*;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.utils.ImpactUtils.createModificationResultWithElementImpact;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
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

    private static final UUID LOADFLOW_RESULT_UUID = UUID.randomUUID();
    private static final String SECURITY_ANALYSIS_RESULT_UUID = "f3a85c9b-9594-4e55-8ec7-07ea965d24eb";
    private static final String SECURITY_ANALYSIS_STATUS_JSON = "\"CONVERGED\"";

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String LOADFLOW_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String SHORTCIRCUIT_ANALYSIS_RESULT_UUID = "72f94d64-4fc6-11ed-bdc3-0242ac120002";
    private static final String ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID = "72f94d88-4fc6-11ed-bdc3-0242ac120009";

    private static final String SHORTCIRCUIT_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String ONE_BUS_SHORTCIRCUIT_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String VOLTAGE_INIT_RESULT_UUID = "37cf33ad-f4a0-4ab8-84c5-6ad2e22d9866";

    private static final String VOLTAGE_INIT_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String STATE_ESTIMATION_RESULT_UUID = "d3a85e9b-9894-4255-8ec7-07ea965d24eb";
    private static final String STATE_ESTIMATION_STATUS_JSON = "\"COMPLETED\"";

    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";

    private static final Report REPORT_TEST = Report.builder().id(UUID.randomUUID()).message("test").severities(List.of(StudyConstants.Severity.WARN)).build();

    private static final String TEST_FILE = "testCase.xiidm";

    private static final String USER_ID_HEADER = "userId";
    private static final String USER_ID = "userLambda";
    private static final String USER_ID_NO_PROFILE = "userNoProfile";
    private static final String USER_ID_NO_QUOTA = "userNoQuota";
    private static final String USER_ID_QUOTA_EXCEEDED = "userOverQuota";

    private static final long TIMEOUT = 1000;

    private static final String URI_NETWORK_MODIF = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications";
    private static final String URI_NETWORK_MODIF_WITH_ID = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications/{uuid}";

    private static final NetworkModificationResult DEFAULT_BUILD_RESULT = createModificationResultWithElementImpact(SimpleImpactType.CREATION, IdentifiableType.LINE, "lineId", Set.of("s1", "s2")).get();

    @Autowired
    private MockMvc mockMvc;

    // old mock server
    private MockWebServer server;

    // new mock server (use this one to mock API calls)
    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

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
    private LoadFlowService loadFlowService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private NonEvacuatedEnergyService nonEvacuatedEnergyService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private VoltageInitService voltageInitService;

    @Autowired
    private StateEstimationService stateEstimationService;

    @Autowired
    private UserAdminService userAdminService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private StudyRepository studyRepository;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String elementUpdateDestination = "element.update";

    private UUID buildOkStubId;
    private UUID buildStopStubId;
    private UUID buildFailedStubId;
    private UUID buildErrorStubId;
    private UUID userProfileQuotaStubId;
    private UUID userProfileQuotaExceededStubId;
    private UUID userProfileNoQuotaStubId;
    private UUID userNoProfileStubId;

    private final String errorMessage = "nullPointerException: unexpected null somewhere";

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        initMockBeans(network);

        server = new MockWebServer();
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockUtils = new WireMockUtils(wireMockServer);

        // Start the mock servers
        server.start();
        wireMockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        reportService.setReportServerBaseUri(baseUrl);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        nonEvacuatedEnergyService.setSensitivityAnalysisServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);
        voltageInitService.setVoltageInitServerBaseUri(baseUrl);
        stateEstimationService.setStateEstimationServerServerBaseUri(baseUrl);

        String baseUrlWireMock = wireMockServer.baseUrl();
        networkModificationService.setNetworkModificationServerBaseUri(baseUrlWireMock);
        userAdminService.setUserAdminServerBaseUri(baseUrlWireMock);

        buildOkStubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_STRING + "/build"))
            .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", DEFAULT_BUILD_RESULT, "destination", "build.result"))
            .willReturn(WireMock.ok())).getId();
        buildFailedStubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_2_STRING + "/build"))
            .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", "", "destination", "build.failed", "message", errorMessage))
            .willReturn(WireMock.ok())).getId();
        buildErrorStubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_3_STRING + "/build"))
            .willReturn(WireMock.serverError())).getId();
        buildStopStubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/build/stop"))
            .withPostServeAction(POST_ACTION_SEND_INPUT, Map.of("payload", "", "destination", "build.stopped"))
            .willReturn(WireMock.ok())).getId();
        userProfileQuotaStubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/users/" + USER_ID + "/profile/max-builds"))
            .willReturn(WireMock.ok()
                .withBody("10")
                .withHeader("Content-Type", "application/json"))).getId();
        userProfileQuotaExceededStubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/users/" + USER_ID_QUOTA_EXCEEDED + "/profile/max-builds"))
            .willReturn(WireMock.ok()
                .withBody("1")
                .withHeader("Content-Type", "application/json"))).getId();
        userProfileNoQuotaStubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/users/" + USER_ID_NO_QUOTA + "/profile/max-builds"))
                .willReturn(WireMock.ok()
                .withBody((String) null)
                .withHeader("Content-Type", "application/json"))).getId();
        userNoProfileStubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/users/" + USER_ID_NO_PROFILE + "/profile/max-builds"))
                .willReturn(WireMock.notFound())).getId();

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
                } else if (("/v1/non-evacuated-energy/results/invalidate-status?resultUuid=" + SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (("/v1/non-evacuated-energy/results/" + SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/non-evacuated-energy/results/" + SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/invalidate-status?resultUuid=" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (("/v1/results/invalidate-status?resultUuid=" + ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(ONE_BUS_SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/" + ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(ONE_BUS_SHORTCIRCUIT_ANALYSIS_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/invalidate-status?resultUuid=" + VOLTAGE_INIT_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(VOLTAGE_INIT_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + VOLTAGE_INIT_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_INIT_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/invalidate-status?resultUuid=" + LOADFLOW_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (("/v1/results/" + LOADFLOW_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(LOADFLOW_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + LOADFLOW_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(LOADFLOW_STATUS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else if (("/v1/results/invalidate-status?resultUuid=" + STATE_ESTIMATION_RESULT_UUID).equals(path)) {
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                        "application/json; charset=utf-8");
                } else if (("/v1/results/" + STATE_ESTIMATION_RESULT_UUID + "/status").equals(path)) {
                    return new MockResponse().setResponseCode(200).setBody(STATE_ESTIMATION_STATUS_JSON)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (("/v1/results/" + STATE_ESTIMATION_RESULT_UUID).equals(path)) {
                    if (request.getMethod().equals("DELETE")) {
                        return new MockResponse().setResponseCode(200).setBody(STATE_ESTIMATION_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                    return new MockResponse().setResponseCode(500);
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
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
                modificationGroupUuid1, "variant_1", "node 1", USER_ID);

        testBuildFailedWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuildError() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", USER_ID);

        testBuildErrorWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuildQuotaExceeded() throws Exception {
        String userId = USER_ID_QUOTA_EXCEEDED;
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", userId);
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1.getId(), modificationGroupUuid2, "variant_2", "node 2", userId);

        // build modificationNode1: ok
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode1.getId(), userId, userProfileQuotaExceededStubId);

        // build modificationNode2: cannot be done cause quota is 1 build max (err 403)
        MvcResult result = mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyNameUserIdUuid, modificationNode2.getId())
                        .header("userId", userId)
                        .contentType(APPLICATION_JSON)
                ).andExpect(status().isForbidden())
                .andReturn();
        assertTrue(result.getResponse().getContentAsString().equalsIgnoreCase("MAX_NODE_BUILDS_EXCEEDED max allowed built nodes : 1"));
        wireMockUtils.verifyGetRequest(userProfileQuotaExceededStubId, "/v1/users/" + userId + "/profile/max-builds", Map.of());
    }

    @Test
    public void testBuildNoQuotaInProfile() throws Exception {
        String userId = USER_ID_NO_QUOTA;
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", userId);

        // build modificationNode1: ok
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode1.getId(), userId, userProfileNoQuotaStubId);
    }

    @Test
    public void testBuildNoProfile() throws Exception {
        String userId = USER_ID_NO_PROFILE;
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1", userId);

        // build modificationNode1: ok
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode1.getId(), userId, userNoProfileStubId);
    }

    @Test
    public void testBuild() throws Exception {
        String userId = USER_ID;
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

        modificationNode3.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        buildInfos = networkModificationTreeService.getBuildInfos(modificationNode4.getId());
        assertEquals("variant_3", buildInfos.getOriginVariantId()); // variant to clone is variant associated to node
                                                                    // modificationNode3
        assertEquals("variant_4", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid4), buildInfos.getModificationGroupUuids());

        modificationNode2.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));  // mark node modificationNode2 as not built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode4.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));  // mark node modificationNode4 as built invalid
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode4, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode5.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));  // mark node modificationNode5 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode5, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode2 and stop build
        testBuildAndStopWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId(), userId, userProfileQuotaStubId);

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode3.getId()).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode4.getId()).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode5.getId()).getGlobalBuildStatus());

        modificationNode3.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // build modificationNode3 and stop build
        testBuildAndStopWithNodeUuid(studyNameUserIdUuid, modificationNode3.getId(), userId, userProfileQuotaStubId);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode4.getId()).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode5.getId()).getGlobalBuildStatus());
    }

    @Test
    public void testLocalBuildValue() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        String userId = "userId";
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();

        Map<String, Object> createLoadInfos = Map.of("type", ModificationType.LOAD_CREATION, "equipmentId", "loadId");
        String jsonCreateLoadInfos = mapper.writeValueAsString(createLoadInfos);
        wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));

        // Mark the node status as built
        modificationNode.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 2", userId);
        UUID modificationNode2Uuid = modificationNode2.getId();

        // Create network modification on BUILT modification node
        Optional<NetworkModificationResult> networkModificationResult =
                createModificationResultWithElementImpact(SimpleImpactType.CREATION, IdentifiableType.LOAD, "loadId", Set.of("s1"));
        wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        NetworkImpactsInfos expectedPayload = NetworkImpactsInfos.builder().impactedSubstationsIds(ImmutableSet.of("s1")).deletedEquipments(ImmutableSet.of()).build();

        // Build first node with errors
        networkModificationResult.get().setApplicationStatus(NetworkModificationResult.ApplicationStatus.WITH_ERRORS);
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        // Build second node is OK
        networkModificationResult.get().setApplicationStatus(NetworkModificationResult.ApplicationStatus.ALL_OK);
        stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(modificationNode2Uuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        //Build modification node 2, local status should be BUILT and computed one should be BUILT_WITH_ERRORS
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode2Uuid).getLocalBuildStatus());
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(modificationNode2Uuid).getGlobalBuildStatus());
    }

    @Test
    public void testNetworkModificationSwitch() throws Exception {

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
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID);

        mvcResult = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, userId)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResult = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResult.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "UCTE"));

        // update switch on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID_2);

        // test build status on switch modification
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));  // mark modificationNode1 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        modificationNode2.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));  // mark modificationNode2 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        Optional<NetworkModificationResult> networkModificationResult =
            createModificationResultWithElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.SWITCH, "switchId", Set.of("s1", "s2", "s3"));
        stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        Set<String> substationsSet = ImmutableSet.of("s3", "s1", "s2");
        NetworkImpactsInfos expectedPayload = NetworkImpactsInfos.builder().impactedSubstationsIds(substationsSet).build();
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid, modificationNode2Uuid), expectedPayload);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports/.*")));

        // modificationNode2 is still built
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode1Uuid).getGlobalBuildStatus());

        // modificationNode2 is now invalid
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode2Uuid).getGlobalBuildStatus());
    }

    @Test
    public void testNetworkModificationEquipment() throws Exception {
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
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubId, bodyJson, NETWORK_UUID_STRING);

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
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid2));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID_2);
    }

    @Test
    public void testCreateGenerator() throws Exception {
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
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJsonCreate, NETWORK_UUID_STRING, VARIANT_ID);

        // create generator on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJsonCreate, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update generator creation
        body.replace("generatorId", "generatorId2");
        body.replace("generatorName", "generatorName2");
        body.replace("energySource", "UNDEFINED");
        body.replace("minActivePower", "150.0");
        body.replace("maxActivePower", "50.0");
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        String bodyJsonUpdate = mapper.writeValueAsString(body);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(bodyJsonUpdate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, bodyJsonUpdate);

        // create generator on building node
        body.replace("generatorId", "generatorId3");
        body.replace("generatorName", "generatorName3");
        body.replace("energySource", "UNDEFINED");
        body.replace("minActivePower", "100.0");
        body.replace("maxActivePower", "200.0");
        String bodyJsonCreateBis = mapper.writeValueAsString(body);
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreateBis).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateShuntsCompensator() throws Exception {
        String userId = "userId";
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
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createShuntCompensatorAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createShuntCompensatorAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // update shunt compensator creation
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        String shuntCompensatorAttributesUpdated = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"shuntCompensatorId\":\"shuntCompensatorId2\",\"shuntCompensatorName\":\"shuntCompensatorName2\",\"voltageLevelId\":\"idVL2\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(shuntCompensatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, shuntCompensatorAttributesUpdated);

        String createShuntCompensatorAttributes2 = "{\"type\":\"" + ModificationType.SHUNT_COMPENSATOR_CREATION + "\",\"shuntCompensatorId\":\"shuntCompensatorId3\",\"shuntCompensatorName\":\"shuntCompensatorName3\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create shunt compensator on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createShuntCompensatorAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateStaticVarCompensator() throws Exception {
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

        Map<String, Object> body = new HashMap<>();
        body.put("type", ModificationType.STATIC_VAR_COMPENSATOR_CREATION);
        body.put("staticVarCompensatorId", "staticVarCompensatorId1");
        body.put("staticVarCompensatorName", "staticVarCompensatorName1");
        body.put("susceptanceMin", "200.0");
        body.put("susceptanceMax", "100.0");
        body.put("reactivePowerSetpoint", "20.0");
        body.put("voltageSetpoint", "225.0");
        body.put("voltageLevelId", "idVL1");
        body.put("busOrBusbarSectionId", "idBus1");
        String bodyJsonCreate = mapper.writeValueAsString(body);

        // create static var compensator on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create static var compensator on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJsonCreate, NETWORK_UUID_STRING, VARIANT_ID);

        // create static var compensator on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJsonCreate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJsonCreate, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update static var compensator creation
        body.replace("staticVarCompensatorId", "staticVarCompensatorId2");
        body.replace("staticVarCompensatorName", "staticVarCompensatorName2");
        body.put("reactivePowerSetpoint", "50.0");
        body.put("voltageSetpoint", "230.0");
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        String bodyJsonUpdate = mapper.writeValueAsString(body);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(bodyJsonUpdate).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, bodyJsonUpdate);

        // create static var compensator on building node
        body.replace("staticVarCompensatorId", "staticVarCompensatorId3");
        body.replace("staticVarCompensatorName", "staticVarCompensatorName3");
        body.put("reactivePowerSetpoint", "50.0");
        body.put("voltageSetpoint", "230.0");
        String bodyJsonCreateBis = mapper.writeValueAsString(body);
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreateBis).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateLine() throws Exception {
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
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createLineAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // create line on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createLineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createLineAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update line creation
        String lineAttributesUpdated = "{\"type\":\"" + ModificationType.LINE_CREATION
                + "\",\"lineId\":\"lineId2\"," + "\"lineName\":\"lineName2\","
                + "\"seriesResistance\":\"54.0\"," + "\"seriesReactance\":\"55.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL2\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(lineAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, lineAttributesUpdated);

        String createLineAttributes2 = "{\"type\":\"" + ModificationType.LINE_CREATION
                + "\",\"lineId\":\"lineId3\"," + "\"lineName\":\"lineName3\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create line on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLineAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateTwoWindingsTransformer() throws Exception {
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

        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        // create 2WT on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create 2WT on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createTwoWindingsTransformerAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // create 2WT on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createTwoWindingsTransformerAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update Two Windings Transformer creation
        String twoWindingsTransformerAttributesUpdated = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(twoWindingsTransformerAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, twoWindingsTransformerAttributesUpdated);

        String createTwoWindingsTransformerAttributes2 = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId3\",\"equipmentName\":\"2wtName3\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create Two Windings Transformer on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createTwoWindingsTransformerAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
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

        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());

        // reactivate modification
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network_modifications/{modificationUuid}?active=true",
                        studyUuid, modificationNode1.getId(), modificationUuid).header(USER_ID_HEADER, "userId")
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        node.set(networkModificationTreeService.getNode(modificationNode1.getId()));
        modificationNode = (NetworkModificationNode) node.get();
        assertTrue(modificationNode.getModificationsToExclude().isEmpty());

        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(modificationNode1.getId()));
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode1.getId());
    }

    @Test
    public void deleteModificationRequest() throws Exception {
        String userId = "userId";

        UUID stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/network-modifications")).willReturn(WireMock.ok())).getId();

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
        checkEquipmentDeletingFinishedMessagesReceived(studyUuid1, modificationNode.getId());

        UUID modificationUuid = UUID.randomUUID();
        mockMvc.perform(delete(URI_NETWORK_MODIF, studyUuid, modificationNode.getId())
                        .queryParam("uuids", modificationUuid.toString())
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        wireMockUtils.verifyDeleteRequest(stubId, "/v1/network-modifications", false, Map.of("uuids", WireMock.equalTo(modificationUuid.toString())));
        checkEquipmentDeletingMessagesReceived(studyUuid, modificationNode.getId());
        checkUpdateModelsStatusMessagesReceived(studyUuid, modificationNode.getId());
        checkEquipmentDeletingFinishedMessagesReceived(studyUuid, modificationNode.getId());

        stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/network-modifications.*"))
            .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
        mockMvc.perform(delete("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications", studyUuid, modificationNode.getId())
                .queryParam("uuids", modificationUuid.toString())
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isBadRequest());
        wireMockUtils.verifyDeleteRequest(stubId, "/v1/network-modifications", false, Map.of("uuids", WireMock.equalTo(modificationUuid.toString())));
        checkEquipmentDeletingMessagesReceived(studyUuid, modificationNode.getId());
        checkEquipmentDeletingFinishedMessagesReceived(studyUuid, modificationNode.getId());
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
        bodyLineInfos.put("type", ModificationType.OPERATING_STATUS_MODIFICATION);
        bodyLineInfos.put("equipmentId", "line12");
        bodyLineInfos.put("action", "lockout");
        String bodyJsonCreate1 = mapper.writeValueAsString(bodyLineInfos);

        // change line status on root node (not allowed)
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
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
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate1, NETWORK_UUID_STRING);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate2 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPostWithError(bodyJsonCreate2);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate2, NETWORK_UUID_STRING);

        // trip line
        bodyLineInfos.put("equipmentId", "line23");
        bodyLineInfos.put("action", "trip");
        String bodyJsonCreate3 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate3).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate3, NETWORK_UUID_STRING);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate4 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPostWithError(bodyJsonCreate4);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate4).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate4, NETWORK_UUID_STRING);

        // energise line end
        bodyLineInfos.put("equipmentId", "line13");
        bodyLineInfos.put("action", "energiseEndOne");
        String bodyJsonCreate5 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate5).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate5, NETWORK_UUID_STRING);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate6 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPostWithError(bodyJsonCreate6);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate6).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate6, NETWORK_UUID_STRING);

        // switch on line
        bodyLineInfos.put("equipmentId", "line13");
        bodyLineInfos.put("action", "switchOn");
        String bodyJsonCreate7 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate7).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate7, NETWORK_UUID_STRING);

        bodyLineInfos.put("equipmentId", "lineFailedId");
        String bodyJsonCreate8 = mapper.writeValueAsString(bodyLineInfos);
        stubPostId = wireMockUtils.stubNetworkModificationPostWithError(bodyJsonCreate8);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJsonCreate8).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, bodyJsonCreate8, NETWORK_UUID_STRING);

        // switch on line on second modification node
        String bodyJsonCreate9 = bodyJsonCreate7;
        stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJsonCreate9).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJsonCreate9, NETWORK_UUID_STRING, VARIANT_ID_2);
    }

    @Test
    public void testCreateLoad() throws Exception {
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
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 3", userId);
        UUID modificationNode3Uuid = modificationNode3.getId();

        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        // create load on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create load on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createLoadAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // create load on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createLoadAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createLoadAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update load creation
        String loadAttributesUpdated = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId2\",\"loadName\":\"loadName2\",\"loadType\":\"UNDEFINED\",\"activePower\":\"50.0\",\"reactivePower\":\"25.0\",\"voltageLevelId\":\"idVL2\",\"busId\":\"idBus2\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, loadAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(loadAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPostId, MODIFICATION_UUID, loadAttributesUpdated);

        String createLoadAttributes2 = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId3\",\"loadName\":\"loadName3\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        modificationNode3.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // create load on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode3Uuid)
                        .content(createLoadAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testModifyLoad() throws Exception {
        String userId = "userId";
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
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(loadModificationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, loadModificationAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // modify load on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid2)
                        .content(loadModificationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid2));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, loadModificationAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update load modification
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        String loadAttributesUpdated = "{\"type\":\"" + ModificationType.LOAD_MODIFICATION + "\",\"loadId\":\"loadId1\",\"loadType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(loadAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, loadAttributesUpdated);
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

        // modify generator on root node (not allowed)
        String equipmentModificationAttribute = "{\"type\":\"" + ModificationType.GENERATOR_MODIFICATION + "\",\"equipmentId\":\"equipmentId\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // modify generator on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, equipmentModificationAttribute, NETWORK_UUID_STRING, VARIANT_ID);

        // modify generator on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid2)
                        .content(equipmentModificationAttribute).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid2));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, equipmentModificationAttribute, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update generator modification
        String generatorAttributesUpdated = "{\"type\":\"" + ModificationType.GENERATOR_MODIFICATION + "\",\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        UUID stubPutId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, generatorAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(generatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, generatorAttributesUpdated);
    }

    @Test
    public void testCreateSubstation() throws Exception {
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

        String createSubstationAttributes = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId1\",\"substationName\":\"substationName1\",\"country\":\"AD\"}";

        // create substation on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // create substation on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createSubstationAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // create substation on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createSubstationAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createSubstationAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update substation creation
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        String substationAttributesUpdated = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"FR\"}";
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(substationAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, substationAttributesUpdated);

        String createSubstationAttributes2 = "{\"type\":\"" + ModificationType.SUBSTATION_CREATION + "\",\"substationId\":\"substationId2\",\"substationName\":\"substationName2\",\"country\":\"AD\"}";
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create substation on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createSubstationAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testCreateVoltageLevel() throws Exception {
        String userId = "userId";
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
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createVoltageLevelAttributes, NETWORK_UUID_STRING, VARIANT_ID);

        // create voltage level on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(createVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, createVoltageLevelAttributes, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update voltage level creation
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
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
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, voltageLevelAttributesUpdated);

        String createVoltageLevelAttributes2 = "{\"type\":\"" + ModificationType.VOLTAGE_LEVEL_CREATION + "\",\"voltageLevelId\":\"voltageLevelId3\",\"voltageLevelName\":\"voltageLevelName3\""
                + ",\"nominalVoltage\":\"379.1\", \"substationId\":\"s2\"}";
        modificationNode1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILDING));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        // create voltage level on building node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(createVoltageLevelAttributes2).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());
    }

    @SneakyThrows
    @Test
    public void testLineSplitWithVoltageLevel() {
        String userId = "userId";
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

        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(lineSplitWoVLasJSON).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, lineSplitWoVLasJSON, NETWORK_UUID_STRING);

        UUID stubPutId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, lineSplitWoVLasJSON);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(lineSplitWoVLasJSON).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, lineSplitWoVLasJSON);

        String badBody = "{\"type\":\"" + ModificationType.LINE_SPLIT_WITH_VOLTAGE_LEVEL + "\",\"bogus\":\"bogus\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPostWithBodyAndError(badBody);
        stubPutId = wireMockUtils.stubNetworkModificationPutWithBodyAndError(MODIFICATION_UUID, badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(
                        status().isBadRequest(),
                        content().string("400 BAD_REQUEST"));
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpectAll(
                        status().isBadRequest(),
                        content().string("400 BAD_REQUEST"));
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, badBody, NETWORK_UUID_STRING);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, badBody);
    }

    @SneakyThrows
    @Test
    public void testLineAttachToVoltageLevel() {
        String userId = "userId";
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

        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(createLineAttachToVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, createLineAttachToVoltageLevelAttributes, NETWORK_UUID_STRING);

        UUID stubPutId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, createLineAttachToVoltageLevelAttributes);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(createLineAttachToVoltageLevelAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, createLineAttachToVoltageLevelAttributes);
    }

    @SneakyThrows
    @Test
    public void testLinesAttachToSplitLines() {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        String createLinesAttachToSplitLinesAttributes = "{\"type\":\"" + ModificationType.LINES_ATTACH_TO_SPLIT_LINES + "\",\"lineToAttachTo1Id\":\"line1\",\"lineToAttachTo2Id\":\"line2\",\"attachedLineId\":\"line3\",\"voltageLevelId\":\"vl1\",\"bbsBusId\":\"v1bbs\",\"replacingLine1Id\":\"replacingLine1Id\",\"replacingLine1Name\":\"replacingLine1Name\",\"replacingLine2Id\":\"replacingLine2Id\",\"replacingLine2Name\":\"replacingLine2Name\"}";
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(createLinesAttachToSplitLinesAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, createLinesAttachToSplitLinesAttributes, NETWORK_UUID_STRING);

        UUID stubPutId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, createLinesAttachToSplitLinesAttributes);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(createLinesAttachToSplitLinesAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, createLinesAttachToSplitLinesAttributes);

        String badBody = "{\"type\":\"" + ModificationType.LINES_ATTACH_TO_SPLIT_LINES + "\",\"bogus\":\"bogus\"}";
        stubPostId = wireMockUtils.stubNetworkModificationPostWithBodyAndError(badBody);
        stubPutId = wireMockUtils.stubNetworkModificationPutWithBodyAndError(MODIFICATION_UUID, badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isBadRequest());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, badBody, NETWORK_UUID_STRING);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, badBody);
    }

    @SneakyThrows
    @Test
    public void testScaling() {
        checkScaling(ModificationType.GENERATOR_SCALING);
        checkScaling(ModificationType.LOAD_SCALING);
    }

    private void checkScaling(ModificationType scalingType) throws Exception {
        String userId = "userId";
        ModificationInfos modificationInfos = ModificationInfos.builder().type(scalingType).substationIds(Set.of("s1")).build();
        String requestBody = mapper.writeValueAsString(modificationInfos);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(requestBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, requestBody, NETWORK_UUID_STRING);

        UUID stubPutId = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, requestBody);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(requestBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, requestBody);

        // test with errors
        stubPostId = wireMockUtils.stubNetworkModificationPostWithBodyAndError(requestBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(requestBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().is4xxClientError());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubPostId, requestBody, NETWORK_UUID_STRING);

        stubPutId = wireMockUtils.stubNetworkModificationPutWithBodyAndError(MODIFICATION_UUID, requestBody);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(requestBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().is4xxClientError());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, requestBody);
    }

    @SneakyThrows
    @Test
    public void testDeleteVoltageLevelOnline() {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        String createDeleteVoltageLevelOnlineAttributes = "{\"type\":\"" + ModificationType.DELETE_VOLTAGE_LEVEL_ON_LINE + "\",\"lineToAttachTo1Id\":\"line1\",\"lineToAttachTo2Id\":\"line2\",\"replacingLine1Id\":\"replacingLine1Id\",\"replacingLine1Name\":\"replacingLine1Name\"}";

        UUID stubIdPost = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(createDeleteVoltageLevelOnlineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                        .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubIdPost, createDeleteVoltageLevelOnlineAttributes, NETWORK_UUID_STRING);

        UUID stubIdPut = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, createDeleteVoltageLevelOnlineAttributes);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(createDeleteVoltageLevelOnlineAttributes).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                        .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPut(stubIdPut, MODIFICATION_UUID, createDeleteVoltageLevelOnlineAttributes);

        String badBody = "{\"type\":\"" + ModificationType.DELETE_VOLTAGE_LEVEL_ON_LINE + "\",\"bogus\":\"bogus\"}";
        UUID stubIdPostErr = wireMockUtils.stubNetworkModificationPostWithBodyAndError(badBody);
        UUID stubIdPutErr = wireMockUtils.stubNetworkModificationPutWithBodyAndError(MODIFICATION_UUID, badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid).header(USER_ID_HEADER, userId)
                .content(badBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubIdPostErr, badBody, NETWORK_UUID_STRING);

        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .header(USER_ID_HEADER, userId)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isBadRequest());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPut(stubIdPutErr, MODIFICATION_UUID, badBody);
    }

    @SneakyThrows
    @Test
    public void testDeleteAttachingline() {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node", "userId");
        UUID modificationNodeUuid = modificationNode.getId();

        String createDeleteAttachingLineAttributes = "{\"type\":\"" + ModificationType.DELETE_ATTACHING_LINE + "\",\"lineToAttachTo1Id\":\"line1\",\"lineToAttachTo2Id\":\"line2\",\"attachedLineId\":\"line3\",\"replacingLine1Id\":\"replacingLine1Id\",\"replacingLine1Name\":\"replacingLine1Name\"}";
        UUID stubIdPost = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                    .content(createDeleteAttachingLineAttributes).contentType(MediaType.APPLICATION_JSON)
                    .header(USER_ID_HEADER, userId))
                    .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubIdPost, createDeleteAttachingLineAttributes, NETWORK_UUID_STRING);

        UUID stubIdPut = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, createDeleteAttachingLineAttributes);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .header(USER_ID_HEADER, userId)
                        .content(createDeleteAttachingLineAttributes).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkUpdateEquipmentModificationMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPut(stubIdPut, MODIFICATION_UUID, createDeleteAttachingLineAttributes);

        String badBody = "{\"type\":\"" + ModificationType.DELETE_ATTACHING_LINE + "\",\"bogus\":\"bogus\"}";
        UUID stubIdPostErr = wireMockUtils.stubNetworkModificationPostWithBodyAndError(badBody);
        UUID stubIdPutErr = wireMockUtils.stubNetworkModificationPutWithBodyAndError(MODIFICATION_UUID, badBody);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                        .andExpect(status().isBadRequest());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubIdPostErr, badBody, NETWORK_UUID_STRING);

        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNodeUuid, MODIFICATION_UUID)
                        .content(badBody).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                        .andExpect(status().isBadRequest());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPut(stubIdPutErr, MODIFICATION_UUID, badBody);
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

        UUID groupStubId = wireMockServer.stubFor(WireMock.any(WireMock.urlPathMatching("/v1/groups/.*"))
                .withQueryParam("action", WireMock.equalTo("MOVE"))
                .willReturn(WireMock.ok()
                        .withBody(mapper.writeValueAsString(Optional.empty()))
                        .withHeader("Content-Type", "application/json"))).getId();

        // switch the 2 modifications order (modification1 is set at the end, after modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}",
                studyNameUserIdUuid, modificationNodeUuid, modification1).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        List<UUID> modificationUuidList = Collections.singletonList(modification1);
        String expectedBody = mapper.writeValueAsString(modificationUuidList);
        String url = "/v1/groups/" + modificationNode.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("MOVE"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(modificationNode.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID),
                        "originGroupUuid", WireMock.equalTo(modificationNode.getModificationGroupUuid().toString()),
                        "build", WireMock.equalTo("false")),
                expectedBody);

        // switch back the 2 modifications order (modification1 is set before modification2)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                studyNameUserIdUuid, modificationNodeUuid, modification1, modification2).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        url = "/v1/groups/" + modificationNode.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("MOVE"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(modificationNode.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID),
                        "originGroupUuid", WireMock.equalTo(modificationNode.getModificationGroupUuid().toString()),
                        "build", WireMock.equalTo("false"),
                        "before", WireMock.equalTo(modification2.toString())),
                expectedBody);
    }

    @Test
    public void testReorderModificationErrorCase() throws Exception {
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
        UUID nodeIdUuid1 = UUID.randomUUID();

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                        studyNameUserIdUuid, nodeIdUuid1, modification1, modification2).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, nodeIdUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, nodeIdUuid1);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/{modificationID}?beforeUuid={modificationID2}",
                        studyNameUserIdUuid1, modificationNodeUuid, modification1, modification2).header(USER_ID_HEADER, "userId"))
                .andExpect(status().isForbidden());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid1, modificationNodeUuid);
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

        UUID groupStubId = wireMockServer.stubFor(WireMock.any(WireMock.urlPathMatching("/v1/groups/.*"))
                .withQueryParam("action", WireMock.equalTo("COPY"))
                .willReturn(WireMock.ok()
                        .withBody(mapper.writeValueAsString(Optional.empty()))
                        .withHeader("Content-Type", "application/json"))).getId();

        // duplicate 2 modifications in node1
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                studyUuid, nodeUuid1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(modificationUuidListBody)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);
        checkElementUpdatedMessageSent(studyUuid, userId);

        List<UUID> expectedList = List.of(modification1, modification2);
        String expectedBody = mapper.writeValueAsString(expectedList);
        String url = "/v1/groups/" + node1.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("COPY"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(node1.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID)),
                expectedBody);

        // now we do the same but on a built node
        node1.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));  // mark node1 as built
        networkModificationTreeService.updateNode(studyUuid, node1, userId);
        checkElementUpdatedMessageSent(studyUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        groupStubId = wireMockServer.stubFor(WireMock.any(WireMock.urlPathMatching("/v1/groups/.*"))
                .withQueryParam("action", WireMock.equalTo("COPY"))
                .willReturn(WireMock.ok()
                        .withBody(mapper.writeValueAsString(Optional.of(NetworkModificationResult.builder().build())))
                        .withHeader("Content-Type", "application/json"))).getId();

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        studyUuid, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());

        NetworkImpactsInfos expectedPayload = NetworkImpactsInfos.builder().build();
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkElementUpdatedMessageSent(studyUuid, userId);
        checkEquipmentMessagesReceived(studyUuid, List.of(nodeUuid1), expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);

        expectedList = List.of(modification1, modification2);
        expectedBody = mapper.writeValueAsString(expectedList);
        url = "/v1/groups/" + node1.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("COPY"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(node1.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID)),
                expectedBody);
    }

    @Test
    public void testDuplicateModificationErrorCase() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "New node 1", "userId");
        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        String modificationUuidListBody = mapper.writeValueAsString(Arrays.asList(modification1, modification2));

        // Random/bad studyId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        UUID.randomUUID(), rootNodeUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        // Random/bad nodeId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=COPY",
                        studyUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

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

        UUID groupStubId = wireMockServer.stubFor(WireMock.any(WireMock.urlPathMatching("/v1/groups/.*"))
                .withQueryParam("action", WireMock.equalTo("MOVE"))
                .willReturn(WireMock.ok()
                        .withBody(mapper.writeValueAsString(Optional.empty()))
                        .withHeader("Content-Type", "application/json"))).getId();

        // move 2 modifications within node 1
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, nodeUuid1, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);

        List<UUID> expectedList = List.of(modification1, modification2);
        String expectedBody = mapper.writeValueAsString(expectedList);
        String url = "/v1/groups/" + node1.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("MOVE"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(node1.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID),
                        "originGroupUuid", WireMock.equalTo(node1.getModificationGroupUuid().toString()),
                        "build", WireMock.equalTo("false")),
                expectedBody);

        // move 2 modifications from node1 to node2
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, nodeUuid2, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid2);
        checkEquipmentUpdatingMessagesReceived(studyUuid, nodeUuid1);
        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(nodeUuid2));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid2);
        checkNodesBuildStatusUpdatedMessageReceived(studyUuid, List.of(nodeUuid1));
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodeUuid1);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid2);
        checkEquipmentUpdatingFinishedMessagesReceived(studyUuid, nodeUuid1);
        checkElementUpdatedMessageSent(studyUuid, userId);
        checkElementUpdatedMessageSent(studyUuid, userId);

        expectedList = List.of(modification1, modification2);
        expectedBody = mapper.writeValueAsString(expectedList);
        url = "/v1/groups/" + node2.getModificationGroupUuid();
        wireMockUtils.verifyPutRequestWithUrlMatching(groupStubId, url, Map.of(
                        "action", WireMock.equalTo("MOVE"),
                        "networkUuid", WireMock.equalTo(NETWORK_UUID_STRING),
                        "reportUuid", WireMock.matching(".*"),
                        "reporterId", WireMock.equalTo(node2.getId().toString()),
                        "variantId", WireMock.equalTo(VARIANT_ID),
                        "originGroupUuid", WireMock.equalTo(node1.getModificationGroupUuid().toString()),
                        "build", WireMock.equalTo("true")),
                expectedBody);

        // move modification without defining originNodeUuid
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?action=MOVE",
                        studyUuid, nodeUuid1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCutAndPasteModificationErrorCase() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "New node 1", userId);
        createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "New node 2", userId);
        UUID modification1 = UUID.randomUUID();
        UUID modification2 = UUID.randomUUID();
        String modificationUuidListBody = mapper.writeValueAsString(Arrays.asList(modification1, modification2));

        // Random/bad studyId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        UUID.randomUUID(), rootNodeUuid, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());

        // Random/bad nodeId error case
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}?originNodeUuid={originNodeUuid}&action=MOVE",
                        studyUuid, UUID.randomUUID(), UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(modificationUuidListBody)
                        .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isNotFound());
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
        String bodyJson = mapper.writeValueAsString(body);

        // delete equipment on root node (not allowed)
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, rootNodeUuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isForbidden());

        // delete equipment on first modification node
        UUID stubPostId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID);

        // delete equipment on second modification node
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubPostId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID_2);

        // update equipment deletion
        UUID stubPutId = wireMockUtils.stubNetworkModificationPut(MODIFICATION_UUID);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPut(stubPutId, MODIFICATION_UUID, bodyJson);
    }

    @Test
    public void testNodesInvalidation() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1", BuildStatus.BUILT, userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2", BuildStatus.BUILT, userId);
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3", BuildStatus.BUILT, userId);
        UUID modificationNode3Uuid = modificationNode3.getId();

        modificationNode1.setReportUuid(UUID.randomUUID());
        modificationNode1.setSecurityAnalysisResultUuid(UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setSensitivityAnalysisResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setNonEvacuatedEnergyResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID));
        modificationNode1.setShortCircuitAnalysisResultUuid(UUID.fromString(SHORTCIRCUIT_ANALYSIS_RESULT_UUID));
        modificationNode1.setOneBusShortCircuitAnalysisResultUuid(UUID.fromString(ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID));
        modificationNode1.setVoltageInitResultUuid(UUID.fromString(VOLTAGE_INIT_RESULT_UUID));
        modificationNode1.setStateEstimationResultUuid(UUID.fromString(STATE_ESTIMATION_RESULT_UUID));

        modificationNode2.setReportUuid(UUID.randomUUID());
        modificationNode3.setReportUuid(UUID.randomUUID());

        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // Update a network modification on node 1 (already built) -> build invalidation of all nodes
        String generatorAttributesUpdated = "{\"type\":\"" + ModificationType.GENERATOR_MODIFICATION + "\",\"generatorId\":\"generatorId1\",\"generatorType\":\"FICTITIOUS\",\"activePower\":\"70.0\"}";
        UUID stubUuid = wireMockUtils.stubNetworkModificationPutWithBody(MODIFICATION_UUID, generatorAttributesUpdated);
        mockMvc.perform(put(URI_NETWORK_MODIF_WITH_ID, studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID)
                        .content(generatorAttributesUpdated).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid, modificationNode2Uuid, modificationNode3Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);

        wireMockUtils.verifyNetworkModificationPut(stubUuid, MODIFICATION_UUID, generatorAttributesUpdated);
        var requests = TestUtils.getRequestsWithBodyDone(17, server);
        assertEquals(3, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*")).count());
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SECURITY_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SENSITIVITY_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/non-evacuated-energy/results/" + SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + SHORTCIRCUIT_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/results/" + STATE_ESTIMATION_RESULT_UUID)));

        // Mark the node 3 status as built
        modificationNode3.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // Create a network modification on node 2 (not built) -> build invalidation of node 3
        Map<String, Object> createLoadInfos = Map.of("type", ModificationType.LOAD_CREATION, "equipmentId", "loadId");
        String jsonCreateLoadInfos = mapper.writeValueAsString(createLoadInfos);
        stubUuid = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode2Uuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode2Uuid, modificationNode3Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNode3Uuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPost(stubUuid, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertEquals(1, requests.stream().filter(r -> r.getPath().matches("/v1/reports/.*")).count());
    }

    @Test
    public void testRemoveLoadFlowComputationReport() throws Exception {
        String userId = "userId";
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1", BuildStatus.BUILT, userId);
        UUID modificationNode1Uuid = modificationNode1.getId();
        // In this node, let's say we have all computations results
        final UUID reportUuid = UUID.randomUUID();
        modificationNode1.setReportUuid(reportUuid);
        modificationNode1.setLoadFlowResultUuid(LOADFLOW_RESULT_UUID);
        modificationNode1.setSecurityAnalysisResultUuid(UUID.fromString(SECURITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setSensitivityAnalysisResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_RESULT_UUID));
        modificationNode1.setNonEvacuatedEnergyResultUuid(UUID.fromString(SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID));
        modificationNode1.setShortCircuitAnalysisResultUuid(UUID.fromString(SHORTCIRCUIT_ANALYSIS_RESULT_UUID));
        modificationNode1.setOneBusShortCircuitAnalysisResultUuid(UUID.fromString(ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID));
        modificationNode1.setVoltageInitResultUuid(UUID.fromString(VOLTAGE_INIT_RESULT_UUID));
        modificationNode1.setStateEstimationResultUuid(UUID.fromString(STATE_ESTIMATION_RESULT_UUID));

        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // A modification body
        Map<String, Object> body = Map.of(
                "type", ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION,
                "equipmentAttributeName", "open",
                "equipmentAttributeValue", true,
                "equipmentType", "SWITCH",
                "equipmentId", "switchId"
        );
        String bodyJson = mapper.writeValueAsString(body);

        // add this modification to the node => invalidate the LF
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNode1Uuid)
                        .content(bodyJson).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNode1Uuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, bodyJson, NETWORK_UUID_STRING, VARIANT_ID);

        var requests = TestUtils.getRequestsDone(24, server); // 3 x 8 computations
        List.of(LOADFLOW_RESULT_UUID, SECURITY_ANALYSIS_RESULT_UUID, SENSITIVITY_ANALYSIS_RESULT_UUID, SENSITIVITY_ANALYSIS_NON_EVACUATED_ENERGY_RESULT_UUID,
                SHORTCIRCUIT_ANALYSIS_RESULT_UUID, ONE_BUS_SHORTCIRCUIT_ANALYSIS_RESULT_UUID, VOLTAGE_INIT_RESULT_UUID, STATE_ESTIMATION_RESULT_UUID).forEach(uuid -> {
                    assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/results/" + uuid)));
                    assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/results/" + uuid + "/status")));
                });
        // requests for computation sub-report deletion
        List.of("LoadFlow", "SecurityAnalysis", "SensitivityAnalysis", "NonEvacuatedEnergyAnalysis", "AllBusesShortCircuitAnalysis", "OneBusShortCircuitAnalysis", "VoltageInit", "StateEstimation").forEach(reportType -> {
            assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/reports/" + reportUuid + "?errorOnReportNotFound=false&reportTypeFilter=" + reportType)));
        });
    }

    @SneakyThrows
    @Test
    public void testUpdateOfBuildStatus() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        String userId = "userId";
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();

        Map<String, Object> createLoadInfos = Map.of("type", ModificationType.LOAD_CREATION, "equipmentId", "loadId");
        String jsonCreateLoadInfos = mapper.writeValueAsString(createLoadInfos);

        // Create network modification on NOT_BUILT modification node
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING, VARIANT_ID);

        // Mark the node status as built
        modificationNode.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT));

        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode, userId);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        output.receive(TIMEOUT, studyUpdateDestination);

        // Create network modification on BUILT modification node
        Optional<NetworkModificationResult> networkModificationResult =
                createModificationResultWithElementImpact(SimpleImpactType.CREATION, IdentifiableType.LOAD, "loadId", Set.of("s1"));
        stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        NetworkImpactsInfos expectedPayload = NetworkImpactsInfos.builder().impactedSubstationsIds(ImmutableSet.of("s1")).deletedEquipments(ImmutableSet.of()).build();
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING, VARIANT_ID);

        // Built with warnings
        networkModificationResult.get().setApplicationStatus(NetworkModificationResult.ApplicationStatus.WITH_WARNINGS);
        stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertEquals(BuildStatus.BUILT_WITH_WARNING, networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        // Built with errors
        networkModificationResult.get().setApplicationStatus(NetworkModificationResult.ApplicationStatus.WITH_ERRORS);
        stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(networkModificationResult));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                        .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, expectedPayload);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertEquals(BuildStatus.BUILT_WITH_ERROR, networkModificationTreeService.getNodeBuildStatus(modificationNodeUuid).getGlobalBuildStatus());
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);
    }

    private void testBuildWithNodeUuid(UUID studyUuid, UUID nodeUuid, String userId, UUID profileStubId) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid)
                        .header(USER_ID_HEADER, userId))
                .andExpect(status().isOk());

        // Initial node update -> BUILDING
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // Successful ->  Node update -> BUILT
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_COMPLETED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        wireMockUtils.verifyGetRequest(profileStubId, "/v1/users/" + userId + "/profile/max-builds", Map.of());
        wireMockUtils.verifyPostRequest(buildOkStubId, "/v1/networks/" + NETWORK_UUID_STRING + "/build", Map.of(QUERY_PARAM_RECEIVER, WireMock.matching(".*")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(nodeUuid).getGlobalBuildStatus());
    }

    private void testBuildAndStopWithNodeUuid(UUID studyUuid, UUID nodeUuid, String userId, UUID profileStubId) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid)
                        .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        // Initial node update -> BUILDING
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // Successful ->  Node update -> BUILT
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_COMPLETED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        wireMockUtils.verifyPostRequest(buildOkStubId, "/v1/networks/" + NETWORK_UUID_STRING + "/build", Map.of(QUERY_PARAM_RECEIVER, WireMock.matching(".*")));
        wireMockUtils.verifyGetRequest(profileStubId, "/v1/users/" + userId + "/profile/max-builds", Map.of());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(nodeUuid).getGlobalBuildStatus());  // node is built

        networkModificationTreeService.updateNodeBuildStatus(nodeUuid, NodeBuildStatus.from(BuildStatus.BUILDING));

        // stop build
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build/stop", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT, studyUpdateDestination);
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_CANCELLED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(nodeUuid).getGlobalBuildStatus()); // node is not built

        wireMockUtils.verifyPutRequest(buildStopStubId, "/v1/build/stop", true, Map.of(QUERY_PARAM_RECEIVER, WireMock.matching(".*")), null);
    }

    // builds on network 2 will fail
    private void testBuildFailedWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid)
                        .header(USER_ID_HEADER, USER_ID))
            .andExpect(status().isOk());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // fail -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error message sent to frontend
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_FAILED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(errorMessage, buildStatusMessage.getHeaders().get(NotificationService.HEADER_ERROR));

        wireMockUtils.verifyPostRequest(buildFailedStubId, "/v1/networks/" + NETWORK_UUID_2_STRING + "/build", Map.of(QUERY_PARAM_RECEIVER, WireMock.matching(".*")));
        wireMockUtils.verifyGetRequest(userProfileQuotaStubId, "/v1/users/" + USER_ID + "/profile/max-builds", Map.of());

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(nodeUuid).getGlobalBuildStatus());  // node is not built
    }

    // builds on network 3 will throw an error on networkmodificationservice call
    private void testBuildErrorWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid)
                        .header(USER_ID_HEADER, USER_ID))
            .andExpect(status().isInternalServerError());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        wireMockUtils.verifyPostRequest(buildErrorStubId, "/v1/networks/" + NETWORK_UUID_3_STRING + "/build", Map.of(QUERY_PARAM_RECEIVER, WireMock.matching(".*")));
        wireMockUtils.verifyGetRequest(userProfileQuotaStubId, "/v1/users/" + USER_ID + "/profile/max-builds", Map.of());

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(nodeUuid).getGlobalBuildStatus());  // node is not built
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
    }

    private void checkNodesBuildStatusUpdatedMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateEquipmentModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
                                                NetworkImpactsInfos expectedPayload) throws Exception {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, List.of(nodeUuid), expectedPayload);
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, List<UUID> nodeUuids,
                                                       NetworkImpactsInfos expectedPayload) throws Exception {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        NetworkImpactsInfos actualPayload = mapper.readValue(new String(messageStudyUpdate.getPayload()), new TypeReference<NetworkImpactsInfos>() {
        });
        assertThat(expectedPayload, new MatcherJson<>(mapper, actualPayload));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuids.get(0), headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
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

    private void checkSwitchModificationMessagesReceived(UUID studyNameUserIdUuid, List<UUID> nodeUuids,
            NetworkImpactsInfos expectedPayload) throws Exception {
        assertFalse(nodeUuids.isEmpty());

        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuids, expectedPayload);

        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, nodeUuids);
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuids.get(0));
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
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkEquipmentFinishedMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.MODIFICATIONS_UPDATING_FINISHED);
    }

    private void checkEquipmentDeletingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkEquipmentFinishedMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.MODIFICATIONS_DELETING_FINISHED);
    }

    private void checkEquipmentFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(updateType, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentDeletedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, NetworkImpactsInfos expectedPayload) throws Exception {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        NetworkImpactsInfos actualPayload = mapper.readValue(new String(messageStudyUpdate.getPayload()), new TypeReference<NetworkImpactsInfos>() {
        });
        assertThat(expectedPayload, new MatcherJson<>(mapper, actualPayload));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));

        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));

        // assert that the broker message has been sent for updating load flow status
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkNodesInvalidationMessagesReceived(UUID studyNameUserIdUuid, List<UUID> invalidatedNodes) {
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, invalidatedNodes);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseFormat, UUID.randomUUID(), null, null, null, null);
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
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
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

    @SneakyThrows
    @Test
    public void testCreateModificationWithErrors() {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        String userId = "userId";
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
            UUID.randomUUID(), VARIANT_ID, "node 1", userId);
        UUID modificationNodeUuid = modificationNode.getId();

        Map<String, Object> createLoadInfos = Map.of("type", ModificationType.LOAD_CREATION, "equipmentId", "loadId");
        String jsonCreateLoadInfos = mapper.writeValueAsString(createLoadInfos);

        // Create network modification on first modification node
        UUID stubId = wireMockUtils.stubNetworkModificationPost(mapper.writeValueAsString(Optional.empty()));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkNodesInvalidationMessagesReceived(studyNameUserIdUuid, List.of(modificationNodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, userId);
        wireMockUtils.verifyNetworkModificationPostWithVariant(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING, VARIANT_ID);

        // String message error
        String contentErrorMessage = "Internal Server Error";
        stubId = wireMockUtils.stubNetworkModificationPostWithError(jsonCreateLoadInfos);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isBadRequest(), content().string(contentErrorMessage));
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        // Json message error
        stubId = wireMockUtils.stubNetworkModificationPostWithError(jsonCreateLoadInfos, String.format("{\"message\" : \"%s\"}", contentErrorMessage));
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isBadRequest(), content().string(contentErrorMessage));
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);

        // Bad json message error
        contentErrorMessage = String.format("{\"foo\" : \"%s\"}", contentErrorMessage);
        stubId = wireMockUtils.stubNetworkModificationPostWithError(jsonCreateLoadInfos, contentErrorMessage);
        mockMvc.perform(post(URI_NETWORK_MODIF, studyNameUserIdUuid, modificationNodeUuid)
                .content(jsonCreateLoadInfos).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isBadRequest(), content().string(contentErrorMessage));
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        wireMockUtils.verifyNetworkModificationPost(stubId, jsonCreateLoadInfos, NETWORK_UUID_STRING);
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
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
