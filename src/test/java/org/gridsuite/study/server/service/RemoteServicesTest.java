package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import okhttp3.MediaType;
import org.assertj.core.api.WithAssertions;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.config.DisableAmqp;
import org.gridsuite.study.server.config.DisableJpa;
import org.gridsuite.study.server.dto.ServiceStatusInfos;
import org.gridsuite.study.server.dto.ServiceStatusInfos.ServiceStatus;
import org.gridsuite.study.server.service.client.RemoteServiceName;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@DisableElasticsearch
@DisableAmqp
@DisableJpa
@SpringBootTest(classes = StudyApplication.class)
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class RemoteServicesTest implements WithAssertions {
    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @Autowired
    private RemoteServices remoteServices;

    @Autowired
    private ObjectMapper objectMapper;
    private static final JsonNode EMPTY_OBJ = JsonNodeFactory.instance.objectNode();

    @MockBean
    private InfoEndpoint infoEndpoint;

    @BeforeAll
    void setup() throws JsonProcessingException {

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);
        wireMockServer.start();

        remoteServicesProperties.setServices(Arrays.stream(RemoteServiceName.values())
                .map(RemoteServiceName::serviceName)
                .map(name -> new RemoteServicesProperties.Service(name, wireMockServer.url(name), false))
                .toList());
    }

    @AfterAll
    public void setDown() {
        wireMockServer.stop();
    }

    @AfterEach
    public void serverCheckup() {
        try {
            wireMockServer.checkForUnmatchedRequests(); // requests no matched ? (it returns an exception if a request was not matched by wireMock, but does not complain if it was not verified by 'verify')
        } finally {
            wireMockServer.resetAll();
        }
    }

    /**
     * Test timeout (with a little range) for testing methods doing request in parallel
     */
    @Timeout(value = RemoteServices.REQUEST_TIMEOUT_IN_MS+500L, unit = TimeUnit.MILLISECONDS)
    @interface TimeoutRemotes {}

    @TimeoutRemotes
    @Test
    void testOptionalServicesUp() {
        testOptionalServices("{\"status\":\"UP\"}", ServiceStatus.UP);
    }

    @TimeoutRemotes
    @Test
    void testOptionalServicesDown() {
        testOptionalServices("{\"status\":\"DOWN\"}", ServiceStatus.DOWN);
    }

    @TimeoutRemotes
    @Test
    void testOptionalServicesMalformedJson() {
        testOptionalServices("{\"malformed json\":", ServiceStatus.DOWN);
    }

    @TimeoutRemotes
    @Test
    void testOptionalServicesUnexpectedJson() {
        testOptionalServices("{\"unexpected_property\":\"UP\"}", ServiceStatus.DOWN);
    }

    private void testOptionalServices(final String jsonResponse, final ServiceStatus statusTest) {
        // select 3 services to be optional
        final List<String> optionalServices = List.of(RemoteServiceName.LOADFLOW_SERVER.serviceName(), RemoteServiceName.SECURITY_ANALYSIS_SERVER.serviceName(), RemoteServiceName.VOLTAGE_INIT_SERVER.serviceName());
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        // any optional service will be mocked with JSON to respond
        final Map<String, UUID> mocks = new HashMap<>(optionalServices.size());
        optionalServices.forEach(name -> mocks.put(name, wireMockServer.stubFor(WireMock
                .get(WireMock.urlPathEqualTo("/"+name+"/actuator/health"))
                .willReturn(WireMock.ok().withBody(jsonResponse))
            ).getId()));

        // all services are supposed to be Up/Down
        assertThat(remoteServices.getOptionalServices()).containsExactlyInAnyOrder(
                new ServiceStatusInfos(RemoteServiceName.LOADFLOW_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.SECURITY_ANALYSIS_SERVER, statusTest),
                new ServiceStatusInfos(RemoteServiceName.VOLTAGE_INIT_SERVER, statusTest)
        );
        mocks.forEach((name, stubUuid) -> wireMockUtils.verifyActuatorHealth(name, stubUuid, 1));
    }

    @TimeoutRemotes
    @Test
    void testServiceInfoGetData() {
        final Map<RemoteServiceName, UUID> mocks = testServiceInfo(0);
        assertThat(remoteServices.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
        for(final RemoteServiceName srv : mocks.keySet()) {
            wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/"+srv.serviceName()+"/actuator/info")));
        }
    }

    @TimeoutRemotes
    @Test
    void testServiceInfoRequestsParallelized() {
        final Map<RemoteServiceName, UUID> mocks = testServiceInfo((int) RemoteServices.REQUEST_TIMEOUT_IN_MS);
        assertThat(remoteServices.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ)));
        for(final RemoteServiceName srv : mocks.keySet()) {
            wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/"+srv.serviceName()+"/actuator/info")));
        }
    }

    @TimeoutRemotes
    @Test
    void testServiceInfoNotThrowIfNotUnavailable() {
        final Map<RemoteServiceName, UUID> mocks = testServiceInfo(0);
        wireMockServer.removeStubMapping(mocks.remove(RemoteServiceName.valueOfServiceName(remoteServicesProperties.getServices().get(3).getName())));
        remoteServicesProperties.getServices().get(3).setBaseUri("http://127.1.0.1/not-exist/");
        final Map<String, JsonNode> expected = Arrays.stream(RemoteServiceName.values())
                .collect(Collectors.toMap(RemoteServiceName::serviceName, srv -> EMPTY_OBJ));
        expected.put(remoteServicesProperties.getServices().get(3).getName(), NullNode.instance);
        assertThat(remoteServices.getServicesInfo()).containsExactlyInAnyOrderEntriesOf(expected);
        for(final RemoteServiceName srv : mocks.keySet()) {
            wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/"+srv.serviceName()+"/actuator/info")));
        }
    }

    private Map<RemoteServiceName, UUID> testServiceInfo(final int delay) {
        Mockito.when(infoEndpoint.info()).thenReturn(Collections.emptyMap());
        final Map<RemoteServiceName, UUID> mocks = new EnumMap<>(RemoteServiceName.class);
        for(final RemoteServiceName service : RemoteServiceName.values()) {
            if (service == RemoteServiceName.STUDY_SERVER) continue;
            mocks.put(service, wireMockServer.stubFor(WireMock
                    .get(WireMock.urlPathEqualTo("/"+service.serviceName()+"/actuator/info"))
                    .willReturn(WireMock.okJson("{}").withFixedDelay(delay))
            ).getId());
        }
        return mocks;
    }
}
