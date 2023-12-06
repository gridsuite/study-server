/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyAppConfig;
import org.gridsuite.study.server.config.DisableCloudStream;
import org.gridsuite.study.server.config.DisableJpa;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

import java.net.SocketException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Slf4j
@DisableElasticsearch
@DisableCloudStream
@DisableJpa
@Import({StudyAppConfig.class, RemoteServicesProperties.class})
@RestClientTest({Inspector.class})
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class InspectorTest implements WithAssertions {
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @Autowired
    private Inspector inspector;

    private static final JsonNode EMPTY_OBJ = JsonNodeFactory.instance.objectNode();

    @MockBean
    private InfoEndpoint infoEndpoint;

    private MockRestServiceServer server;

    @TestConfiguration
    public static class TestConfig {
        static final List<RestTemplate> REST_TEMPLATES = Collections.synchronizedList(new ArrayList<>(1));

        @Bean
        public RestTemplateCustomizer testRestTemplateCustomizer() {
            return REST_TEMPLATES::add;
        }
    }

    @BeforeAll
    void setup(@NonNull @Autowired @Lazy final RestTemplate restTemplate) {
        server = MockRestServiceServer.bindTo(TestConfig.REST_TEMPLATES.get(0)).ignoreExpectOrder(true).build();
        remoteServicesProperties.setServices(Arrays.stream(RemoteServiceName.values())
                .map(RemoteServiceName::serviceName)
                .map(name -> new RemoteServicesProperties.Service(name, "http://" + name + "/", false))
                .toList());
    }

    @AfterEach
    public void serverCheckup() {
        try {
            server.verify();
        } finally {
            server.reset();
        }
    }

    /**
     * Test timeout (with a little range) for testing methods doing request in parallel
     */
    @Timeout(value = Inspector.REQUEST_TIMEOUT_IN_MS + 500L, unit = TimeUnit.MILLISECONDS)
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
        final List<String> optionalServices = List.of(RemoteServiceName.LOADFLOW_SERVER.serviceName(), RemoteServiceName.SECURITY_ANALYSIS_SERVER.serviceName(), RemoteServiceName.VOLTAGE_INIT_SERVER.serviceName());
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        // any optional service will be mocked with JSON to respond
        optionalServices.forEach(name -> server.expect(requestToUriTemplate("http://{service}/actuator/health", name))
                .andExpect(method(HttpMethod.GET)).andRespond(request -> {
                    try {
                        Thread.sleep(delayResponse);
                    } catch (InterruptedException ex) {
                        log.error("wait before request interrupted", ex);
                    }
                    return withSuccess(jsonResponse, MediaType.APPLICATION_JSON).createResponse(request);
                }));

        // all services are supposed to be Up/Down
        assertThat(inspector.getOptionalServices()).containsExactlyInAnyOrder(
                new ServiceStatusInfos(RemoteServiceName.LOADFLOW_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.SECURITY_ANALYSIS_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.VOLTAGE_INIT_SERVER, statusTest)
        );
    }

    @Test
    void testServiceInfoGetData() {
        testServiceInfo(0, null);
        assertThat(inspector.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @TimeoutRemotes
    @Test
    void testServiceInfoRequestsParallelized() {
        testServiceInfo((int) Inspector.REQUEST_TIMEOUT_IN_MS, null);
        assertThat(inspector.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
    }

    @Test
    void testServiceInfoNotThrowIfNotUnavailable() {
        testServiceInfo(0, RemoteServiceName.LOADFLOW_SERVER);
        serverExpectInfo(RemoteServiceName.LOADFLOW_SERVER).andRespond(withException(new SocketException("Test no server")));
        final Map<String, JsonNode> expected = Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ));
        expected.put(RemoteServiceName.LOADFLOW_SERVER.serviceName(), NullNode.instance);
        assertThat(inspector.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private void testServiceInfo(final int delay, final RemoteServiceName skipSrv) {
        Mockito.when(infoEndpoint.info()).thenReturn(Collections.emptyMap());
        for (final RemoteServiceName service : RemoteServiceName.values()) {
            if (service == RemoteServiceName.STUDY_SERVER || service == skipSrv) {
                continue;
            }
            serverExpectInfo(service).andRespond(request -> {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ex) {
                    log.error("wait before request interrupted", ex);
                }
                return withSuccess("{}", MediaType.APPLICATION_JSON).createResponse(request);
            });
        }
    }

    private ResponseActions serverExpectInfo(@NonNull final RemoteServiceName service) {
        return server.expect(requestToUriTemplate("http://{service}/actuator/info", service.serviceName()))
                .andExpect(method(HttpMethod.GET));
    }
}
