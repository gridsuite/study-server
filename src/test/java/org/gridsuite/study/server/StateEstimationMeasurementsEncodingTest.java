/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.stateestimation.StateEstimationRestService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real HTTP on both hops (random study-server port, mocked state-estimation server).
 * It proves forwarding and encoding of the opaque body, not the filtering done by the backend.
 *
 * @author Kamil MARUT {@literal <kamil.marut at rte-france.com>}
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StateEstimationMeasurementsEncodingTest {

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final UUID NETWORK_UUID = UUID.fromString("38400000-8cf0-11bd-b23e-10b96e4ef00d");
    private static final UUID RESULT_UUID = UUID.fromString("cf203721-6150-4203-8960-d61d815a9d16");
    private static final String VARIANT_ID = "variant 1&x";
    private static final String GLOBAL_FILTERS = "{\"label\":\"a+b %41 100% & c=d \\\"q\\\" \\\\ é 中\",\"countryCode\":[\"FR\"]}";
    private static final String MEASUREMENTS = "[{\"id\":\"Café\",\"value\":1.5},{\"id\":\"Café\",\"value\":1.5},{\"id\":\"é 中\",\"value\":0}]";

    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private StateEstimationRestService stateEstimationRestService;
    @Autowired
    private TestUtils studyTestUtils;
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private UUID studyUuid;
    private UUID rootNetworkUuid;
    private UUID nodeUuid;

    @BeforeEach
    void setUp(MockWebServer server) {
        stateEstimationRestService.setBaseUri(server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(study -> networkModificationTreeService.doDeleteTree(study.getId()));
        studyRepository.deleteAll();
        TestUtils.assertQueuesEmptyThenClear(List.of(STUDY_UPDATE_DESTINATION), output);
    }

    private void createStudyAndNode(UUID resultUuid) throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, "netId", UUID.randomUUID(), "", "", null,
            UUID.randomUUID(), null, null, null, null, null);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        studyUuid = studyEntity.getId();
        rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);

        NetworkModificationNode node = NetworkModificationNode.builder().name("node").description("description")
            .modificationGroupUuid(UUID.randomUUID()).variantId(VARIANT_ID).children(List.of()).build();
        // variant and modification group are not serialized by default
        JSONObject nodeJson = new JSONObject(objectMapper.writeValueAsString(node));
        nodeJson.put("variantId", VARIANT_ID);
        nodeJson.put("modificationGroupUuid", node.getModificationGroupUuid());
        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, rootNodeUuid)
                .content(nodeJson.toString()).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
            .andExpect(status().isOk());
        nodeUuid = UUID.fromString(String.valueOf(TestUtils.receiveStudyUpdate(output, STUDY_UPDATE_DESTINATION).getHeaders().get(NotificationService.HEADER_NEW_NODE)));
        rootNetworkNodeInfoService.updateRootNetworkNode(nodeUuid, rootNetworkUuid,
            RootNetworkNodeInfo.builder().variantId(VARIANT_ID).stateEstimationResultUuid(resultUuid).build());
    }

    private URI measurementsUri(UUID study, UUID node, String query) {
        return URI.create("http://localhost:" + port + "/v1/studies/" + study + "/root-networks/" + rootNetworkUuid + "/nodes/" + node
            + "/state-estimation/measurements" + query);
    }

    private static String browserQuery(String globalFilters) {
        // what URLSearchParams does: a single percent-encoding, with '+' for spaces
        return "?globalFilters=" + URLEncoder.encode(globalFilters, StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> get(URI uri) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse(code, Headers.of("Content-Type", "application/json"), body);
    }

    @Test
    void globalFiltersAndResponseSurviveBothHops(MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);
        server.enqueue(json(200, MEASUREMENTS));

        HttpResponse<byte[]> response = get(measurementsUri(studyUuid, nodeUuid, browserQuery(GLOBAL_FILTERS)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).isEqualTo(MEASUREMENTS.getBytes(StandardCharsets.UTF_8));

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/v1/results/" + RESULT_UUID + "/measurements");
        assertThat(request.getRequestUrl().queryParameter("globalFilters")).isEqualTo(GLOBAL_FILTERS);
        assertThat(request.getRequestUrl().queryParameter("networkUuid")).isEqualTo(NETWORK_UUID.toString());
        assertThat(request.getRequestUrl().queryParameter("variantId")).isEqualTo(VARIANT_ID);
        assertThat(request.getRequestUrl().querySize()).isEqualTo(3);
        assertThat(request.getRequestUrl().encodedQuery()).contains("%2B", "%2541", "100%25", "%26").doesNotContain("%252541", " ", "+");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void absentGlobalFiltersAreNotForwarded(MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);
        server.enqueue(json(200, "[]"));

        HttpResponse<byte[]> response = get(measurementsUri(studyUuid, nodeUuid, ""));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).isEqualTo("[]");
        assertThat(server.takeRequest(5, TimeUnit.SECONDS).getRequestUrl().queryParameterNames()).containsExactlyInAnyOrder("networkUuid", "variantId");
    }

    @Test
    void noStoredResultIsNoContentWithoutDownstreamCall(MockWebServer server) throws Exception {
        createStudyAndNode(null);
        HttpResponse<byte[]> response = get(measurementsUri(studyUuid, nodeUuid, browserQuery(GLOBAL_FILTERS)));

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500})
    void downstreamProblemDetailKeepsStatusAndDetail(int status, MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);
        PowsyblWsProblemDetail downstreamError = PowsyblWsProblemDetail.builder(HttpStatus.valueOf(status))
            .server("state-estimation-server").detail("downstream detail é").path("/v1/results/measurements").build();
        server.enqueue(new MockResponse(status, Headers.of("Content-Type", "application/problem+json"), objectMapper.writeValueAsString(downstreamError)));

        HttpResponse<byte[]> response = get(measurementsUri(studyUuid, nodeUuid, browserQuery(GLOBAL_FILTERS)));

        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("downstream detail é");
    }

    @Test
    void downstreamErrorWithoutProblemDetailKeepsStatus(MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);
        server.enqueue(new MockResponse(503, Headers.of("Content-Type", "text/plain"), "maintenance"));

        assertThat(get(measurementsUri(studyUuid, nodeUuid, "")).statusCode()).isEqualTo(503);
    }

    @ParameterizedTest
    @ValueSource(strings = {"filters", "sort"})
    void columnFiltersAndSortAreRejectedBeforeAnyWork(String parameter, MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);

        assertThat(get(measurementsUri(studyUuid, nodeUuid, "?" + parameter + "=%5B%5D")).statusCode()).isEqualTo(400);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void foreignOrUnknownNodeIsNotFoundBeforeAnyWork(MockWebServer server) throws Exception {
        createStudyAndNode(RESULT_UUID);

        assertThat(get(measurementsUri(UUID.randomUUID(), nodeUuid, "")).statusCode()).isEqualTo(404);
        assertThat(get(measurementsUri(studyUuid, UUID.randomUUID(), "")).statusCode()).isEqualTo(404);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void unreachableStateEstimationServerIsBadGateway() throws Exception {
        createStudyAndNode(RESULT_UUID);
        try (ServerSocket closedPort = new ServerSocket(0)) {
            stateEstimationRestService.setBaseUri("http://localhost:" + closedPort.getLocalPort());
        }

        assertThat(get(measurementsUri(studyUuid, nodeUuid, "")).statusCode()).isEqualTo(502);
    }

    @Test
    void slowStateEstimationServerIsGatewayTimeoutWithTheApplicationHttpClient(MockWebServer server) {
        StateEstimationRestService withTimeout = new StateEstimationRestService(
            remoteServicesProperties, objectMapper, new RestTemplateBuilder().readTimeout(Duration.ofMillis(300)).build());
        withTimeout.setBaseUri(server.url("").toString().replaceAll("/$", ""));
        server.enqueue(new MockResponse.Builder().code(200).body("[]").headersDelay(3, TimeUnit.SECONDS).build());

        assertThatThrownBy(() -> withTimeout.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, VARIANT_ID, GLOBAL_FILTERS))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }
}
