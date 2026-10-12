/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.extern.slf4j.Slf4j;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyAppConfig;
import org.gridsuite.study.server.config.DisableCloudStream;
import org.gridsuite.study.server.config.DisableJpa;
import org.gridsuite.study.server.dto.AboutInfo;
import org.gridsuite.study.server.dto.AboutInfo.ModuleType;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.exception.PartialResultException;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@DisableElasticsearch
@DisableCloudStream
@DisableJpa
@Import({StudyAppConfig.class, RemoteServicesProperties.class})
@RestClientTest({RemoteServicesInspector.class})
@ExtendWith({MockitoExtension.class, MockWebServerExtension.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class RemoteServicesInspectorTest implements WithAssertions {
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @Autowired
    private RemoteServicesInspector remoteServicesInspector;

    @Autowired
    private ObjectMapper objectMapper;

    private static final JsonNode EMPTY_OBJ = JsonNodeFactory.instance.objectNode();

    private volatile String healthResponseJson;
    private volatile int healthResponseDelay;
    private volatile int infoResponseDelay;
    private volatile Set<String> unavailableInfoServices = Set.of();
    private Set<String> expectedRequestServices = Set.of();
    private String expectedRequestEndpoint;

    @MockitoBean
    private InfoEndpoint infoEndpoint;

    @BeforeEach
    void prepareCleanEnv(MockWebServer server) {
        String baseUrl = server.url("").toString();
        String serviceUrlPrefix = baseUrl.substring(0, baseUrl.length() - 1);
        remoteServicesProperties.setServices(Arrays.stream(RemoteServiceName.values())
                .map(RemoteServiceName::serviceName)
                .map(name -> new RemoteServicesProperties.Service(name, serviceUrlPrefix + "/" + name, false))
                .toList());
        remoteServicesProperties.getRemoteServiceViewFilter().clear();
        remoteServicesProperties.setRemoteServiceViewDefault(EnumSet.allOf(RemoteServiceName.class));
        healthResponseJson = "{\"status\":\"UP\"}";
        healthResponseDelay = 0;
        infoResponseDelay = 0;
        unavailableInfoServices = Set.of();
        expectedRequestServices = Set.of();
        expectedRequestEndpoint = null;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (HttpMethod.GET.matches(request.getMethod()) && path != null && path.endsWith("/actuator/health")) {
                    waitForResponse(healthResponseDelay);
                    return jsonResponse(healthResponseJson);
                }
                if (HttpMethod.GET.matches(request.getMethod()) && path != null && path.endsWith("/actuator/info")) {
                    waitForResponse(infoResponseDelay);
                    String serviceName = path.substring(1, path.indexOf('/', 1));
                    if (unavailableInfoServices.contains(serviceName)) {
                        return new MockResponse.Builder().code(503).body("unavailable").build();
                    }
                    return jsonResponse("{}");
                }
                return new MockResponse.Builder().code(418)
                        .body("Unhandled method+path: " + request.getMethod() + " " + path)
                        .build();
            }
        });
    }

    @AfterEach
    void serverCheckup(MockWebServer server) throws InterruptedException {
        Set<String> expectedPaths = expectedRequestServices.stream()
                .map(service -> "/" + service + "/actuator/" + expectedRequestEndpoint)
                .collect(Collectors.toSet());
        Set<String> actualPaths = new HashSet<>();
        for (int i = 0; i < expectedPaths.size(); i++) {
            RecordedRequest request = server.takeRequest(100, TimeUnit.MILLISECONDS);
            assertThat(request).isNotNull();
            assertThat(request.getMethod()).isEqualTo(HttpMethod.GET.name());
            actualPaths.add(request.getPath());
        }
        assertThat(actualPaths).containsExactlyInAnyOrderElementsOf(expectedPaths);
        assertThat(server.takeRequest(100, TimeUnit.MILLISECONDS)).isNull();
    }

    private MockResponse jsonResponse(String json) {
        return new MockResponse(HttpStatus.OK.value(), Headers.of("Content-Type", MediaType.APPLICATION_JSON_VALUE), json);
    }

    private void waitForResponse(int delay) {
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ex) {
                log.error("wait before request interrupted", ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Test timeout (with a little range) for testing methods doing request in parallel
     */
    @Timeout(value = RemoteServicesInspector.REQUEST_TIMEOUT_IN_MS + 500L, unit = TimeUnit.MILLISECONDS)
    @interface TimeoutRemotes { }

    @Test
    void testOptionalServicesUp() {
        testOptionalServices("{\"status\":\"UP\"}", ServiceStatus.UP, 0);
    }

    @Test
    void testOptionalServicesDown() {
        testOptionalServices("{\"status\":\"DOWN\"}", ServiceStatus.DOWN, 0);
    }

    @Test
    void testOptionalServicesMalformedJson() {
        testOptionalServices("{\"malformed json\":", ServiceStatus.DOWN, 0);
    }

    @Test
    void testOptionalServicesUnexpectedJson() {
        testOptionalServices("{\"unexpected_property\":\"UP\"}", ServiceStatus.DOWN, 0);
    }

    @TimeoutRemotes
    @Test
    void testOptionalServicesWithLatency() {
        testOptionalServices("{\"status\":\"DOWN\"}", ServiceStatus.DOWN, 2000);
    }

    private void testOptionalServices(final String jsonResponse, final ServiceStatus statusTest, int delayResponse) {
        // select 3 services to be optional
        final List<String> optionalServices = List.of(RemoteServiceName.LOADFLOW_SERVER.serviceName(), RemoteServiceName.SECURITY_ANALYSIS_SERVER.serviceName(),
                RemoteServiceName.VOLTAGE_INIT_SERVER.serviceName());
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));
        healthResponseJson = jsonResponse;
        healthResponseDelay = delayResponse;
        expectedRequestServices = Set.copyOf(optionalServices);
        expectedRequestEndpoint = "health";

        // all services are supposed to be Up/Down
        assertThat(remoteServicesInspector.getOptionalServices()).containsExactlyInAnyOrder(
                new ServiceStatusInfos(RemoteServiceName.LOADFLOW_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.SECURITY_ANALYSIS_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.VOLTAGE_INIT_SERVER, statusTest)
        );
    }

    @Test
    void testServiceInfoGetData() throws Exception {
        testServiceInfo(0, (RemoteServiceName) null);
        assertThat(remoteServicesInspector.getServicesInfo(null)).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @TimeoutRemotes
    @Test
    void testServiceInfoRequestsParallelized() throws Exception {
        testServiceInfo((int) RemoteServicesInspector.REQUEST_TIMEOUT_IN_MS - 500, (RemoteServiceName) null);
        assertThat(remoteServicesInspector.getServicesInfo(null)).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @Test
    void testServiceInfoReturnPartialResultIfNotUnavailable() {
        testServiceInfo(0, RemoteServiceName.LOADFLOW_SERVER);
        expectedRequestServices = EnumSet.complementOf(EnumSet.of(RemoteServiceName.STUDY_SERVER))
                .stream()
                .map(RemoteServiceName::serviceName)
                .collect(Collectors.toSet());
        unavailableInfoServices = Set.of(RemoteServiceName.LOADFLOW_SERVER.serviceName());
        final Map<String, JsonNode> expected = Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ));
        expected.put(RemoteServiceName.LOADFLOW_SERVER.serviceName(), NullNode.instance);
        assertThatThrownBy(() -> remoteServicesInspector.getServicesInfo(null))
            .asInstanceOf(InstanceOfAssertFactories.throwable(PartialResultException.class))
            .extracting(PartialResultException::getResult, InstanceOfAssertFactories.map(String.class, JsonNode.class)).as("result")
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    void testServiceInfoExistingFilter() throws Exception {
        final EnumSet<RemoteServiceName> studyView = EnumSet.of(
            RemoteServiceName.STUDY_SERVER,
            RemoteServiceName.STUDY_NOTIFICATION_SERVER,
            RemoteServiceName.CONFIG_SERVER,
            RemoteServiceName.CONFIG_NOTIFICATION_SERVER
        );
        remoteServicesProperties.getRemoteServiceViewFilter().put(FrontService.STUDY, studyView);
        testServiceInfo(0, EnumSet.of(
                // STUDY_SERVER is done locally
                RemoteServiceName.STUDY_NOTIFICATION_SERVER,
                RemoteServiceName.CONFIG_SERVER,
                RemoteServiceName.CONFIG_NOTIFICATION_SERVER
        ));
        assertThat(remoteServicesInspector.getServicesInfo(FrontService.STUDY)).containsExactlyInAnyOrderEntriesOf(
            studyView.stream().collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ))
        );
    }

    @Test
    void testServiceInfoNonExistingFilter() throws Exception {
        testServiceInfo(0, (RemoteServiceName) null);
        assertThat(remoteServicesInspector.getServicesInfo(FrontService.STUDY)).containsExactlyInAnyOrderEntriesOf(
            Arrays.stream(RemoteServiceName.values())
                  .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @Test
    void testServiceInfoDefaultFilter() throws Exception {
        final EnumSet<RemoteServiceName> services = EnumSet.of(
                RemoteServiceName.SECURITY_ANALYSIS_SERVER,
                RemoteServiceName.SENSITIVITY_ANALYSIS_SERVER,
                RemoteServiceName.SHORTCIRCUIT_SERVER,
                RemoteServiceName.VOLTAGE_INIT_SERVER
        );
        remoteServicesProperties.setRemoteServiceViewDefault(services);
        testServiceInfo(0, services);
        assertThat(remoteServicesInspector.getServicesInfo(null)).containsExactlyInAnyOrderEntriesOf(
            services.stream().collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @Test
    void testServiceInfoDefaultIfNonExistingFilter() throws Exception {
        final EnumSet<RemoteServiceName> services = EnumSet.of(
                RemoteServiceName.CONFIG_SERVER,
                RemoteServiceName.EXPLORE_SERVER,
                RemoteServiceName.VOLTAGE_INIT_SERVER
        );
        remoteServicesProperties.setRemoteServiceViewDefault(services);
        testServiceInfo(0, services);
        assertThat(remoteServicesInspector.getServicesInfo(FrontService.STUDY)).containsExactlyInAnyOrderEntriesOf(
            services.stream().collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    private void testServiceInfo(final int delay, final RemoteServiceName skipSrv) {
        testServiceInfo(delay, EnumSet.complementOf(EnumSet.of(RemoteServiceName.STUDY_SERVER,
                skipSrv == null ? RemoteServiceName.STUDY_SERVER : skipSrv)));
    }

    private void testServiceInfo(final int delay, final EnumSet<RemoteServiceName> mockSrv) {
        Mockito.when(infoEndpoint.info()).thenReturn(Collections.emptyMap());
        infoResponseDelay = delay;
        expectedRequestServices = mockSrv.stream().map(RemoteServiceName::serviceName).collect(Collectors.toSet());
        expectedRequestEndpoint = "info";
    }

    @Test
    void testServiceAboutInfosNotNull() {
        assertThatNullPointerException().isThrownBy(() -> remoteServicesInspector.convertServicesInfoToAboutInfo(null))
                .withMessage("infos is marked non-null but is null");
    }

    @Test
    void testServiceAboutInfosWithEmpty() {
        assertThat(remoteServicesInspector.convertServicesInfoToAboutInfo(Map.of())).isEmpty();
    }

    @Test
    void testServiceAboutInfosWithNullJson() {
        assertThat(remoteServicesInspector.convertServicesInfoToAboutInfo(Map.of("test", NullNode.instance))).containsExactly(
                new AboutInfo(ModuleType.SERVER, "test", null, null)
        );
    }

    @Test
    void testServiceAboutInfosConvert() throws Exception {
        final Map<String, JsonNode> rawInfos = objectMapper.readValue(this.getClass().getClassLoader().getResource("servers_infos_short.json"), new TypeReference<>() { });
        assertThat(remoteServicesInspector.convertServicesInfoToAboutInfo(rawInfos)).containsExactlyInAnyOrder(
                new AboutInfo(ModuleType.SERVER, "Study Server", "1.0.0-SNAPSHOT", "v1.0.0-1"),
                new AboutInfo(ModuleType.SERVER, "Explore server", "1.0.0-SNAPSHOT", "v0.18.0"),
                new AboutInfo(ModuleType.SERVER, "Multiple tags", "1.0.0-SNAPSHOT", "v3")
        );
    }
}
