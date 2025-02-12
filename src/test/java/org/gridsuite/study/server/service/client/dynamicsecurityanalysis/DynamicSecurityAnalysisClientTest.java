/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicsecurityanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.utils.assertions.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;
import static org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.TestUtils.assertStudyException;
import static org.gridsuite.study.server.utils.assertions.Assertions.assertThat;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicSecurityAnalysisClientTest extends AbstractWireMockRestClientTest {
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID DYNAMIC_SIMULATION_RESULT_UUID = UUID.randomUUID();
    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();

    private static final String PARAMETERS_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);
    private static final String RUN_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN);
    private static final String RESULT_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

    private DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    public void setup() {
        // config client
        remoteServicesProperties.setServiceUri("dynamic-security-analysis-server", initMockWebServer());
        dynamicSecurityAnalysisClient = new DynamicSecurityAnalysisClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetDefaultProvider() {
        String url = buildEndPointUrl("", API_VERSION, null) + "/default-provider";

        // --- Success --- //
        String expectedDefaultProvider = "Dynawo";

        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedDefaultProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String defaultProvider = dynamicSecurityAnalysisClient.getDefaultProvider();

        // check result
        assertThat(defaultProvider).isEqualTo(expectedDefaultProvider);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getDefaultProvider(),
                DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getDefaultProvider(),
                GET_DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_FAILED, null);
    }

    @Test
    void testGetProvider() {
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";

        // --- Success --- //
        String expectedProvider = "Dynawo";

        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String provider = dynamicSecurityAnalysisClient.getProvider(PARAMETERS_UUID);

        // check result
        assertThat(provider).isEqualTo(expectedProvider);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getProvider(PARAMETERS_UUID),
                DYNAMIC_SECURITY_ANALYSIS_PROVIDER_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getProvider(PARAMETERS_UUID),
                GET_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED, null);
    }

    @Test
    void testUpdateProvider() {
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";

        // --- Success --- //
        String newProvider = "Dynawo2";

        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                        .withRequestBody(equalTo(newProvider))
                .willReturn(WireMock.ok()
                        .withBody(newProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.updateProvider(PARAMETERS_UUID, newProvider));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.updateProvider(PARAMETERS_UUID, newProvider),
                DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.updateProvider(PARAMETERS_UUID, newProvider),
                UPDATE_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED, null);
    }

    @Test
    void testGetParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();

        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(parameters))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        String resultParametersJson = dynamicSecurityAnalysisClient.getParameters(PARAMETERS_UUID);

        // check result
        DynamicSecurityAnalysisParametersInfos resultParameters = objectMapper.readValue(resultParametersJson, DynamicSecurityAnalysisParametersInfos.class);
        assertThat(resultParameters).recursivelyEquals(parameters);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getParameters(PARAMETERS_UUID),
                DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.getParameters(PARAMETERS_UUID),
                GET_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED, null);
    }

    @Test
    void testCreateParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();
        String parameterJson = objectMapper.writeValueAsString(parameters);

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(PARAMETERS_BASE_URL))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(PARAMETERS_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultParametersUuid = dynamicSecurityAnalysisClient.createParameters(parameterJson);

        // check result
        assertThat(resultParametersUuid).isEqualTo(PARAMETERS_UUID);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(PARAMETERS_BASE_URL))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.createParameters(parameterJson),
                CREATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED, null);
    }

    @Test
    void testUpdateParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();
        String parameterJson = objectMapper.writeValueAsString(parameters);

        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.updateParameters(PARAMETERS_UUID, parameterJson));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.updateParameters(PARAMETERS_UUID, parameterJson),
                DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.updateParameters(PARAMETERS_UUID, parameterJson),
                UPDATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED, null);
    }

    @Test
    void testDuplicateParameters() throws Exception {
        UUID newParameterUuid = UUID.randomUUID();

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(PARAMETERS_BASE_URL))
                    .withQueryParam("duplicateFrom", equalTo(PARAMETERS_UUID.toString()))
                    .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(newParameterUuid))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    ));
        // call service to test
        UUID resultParametersUuid = dynamicSecurityAnalysisClient.duplicateParameters(PARAMETERS_UUID);

        // check result
        assertThat(resultParametersUuid).isEqualTo(newParameterUuid);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(PARAMETERS_BASE_URL))
                .withQueryParam("duplicateFrom", equalTo(PARAMETERS_UUID.toString()))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.duplicateParameters(PARAMETERS_UUID),
                DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(PARAMETERS_BASE_URL))
                .withQueryParam("duplicateFrom", equalTo(PARAMETERS_UUID.toString()))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.duplicateParameters(PARAMETERS_UUID),
                DUPLICATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED, null);
    }

    @Test
    void testDeleteParameters() {
        // configure mock server response
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.deleteParameters(PARAMETERS_UUID));
    }

    @Test
    void testCreateDefaultParameters() throws JsonProcessingException {
        String url = PARAMETERS_BASE_URL + "/default";

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(PARAMETERS_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultParametersUuid = dynamicSecurityAnalysisClient.createDefaultParameters();

        // check result
        assertThat(resultParametersUuid).isEqualTo(PARAMETERS_UUID);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.createDefaultParameters(),
                CREATE_DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PARAMETERS_FAILED, null);
    }

    @Test
    void testRun() throws Exception {
        UUID expectedResultUuid = UUID.randomUUID();
        // configure mock server response
        String url = RUN_BASE_URL + DELIMITER + NETWORK_UUID + DELIMITER + "run";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, equalTo("variantId"))
                .withQueryParam("provider", equalTo("Dynawo"))
                .withQueryParam("dynamicSimulationResultUuid", equalTo(DYNAMIC_SIMULATION_RESULT_UUID.toString()))
                .withQueryParam("parametersUuid", equalTo(PARAMETERS_UUID.toString()))
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_SECURITY_ANALYSIS.reportKey))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(expectedResultUuid))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultUuid = dynamicSecurityAnalysisClient.run("Dynawo", "receiver", NETWORK_UUID,
               "variantId", new ReportInfos(REPORT_UUID, NODE_UUID), DYNAMIC_SIMULATION_RESULT_UUID, PARAMETERS_UUID, "userId");

        // check result
        assertThat(resultUuid).isEqualTo(expectedResultUuid);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, equalTo("variantId"))
                .withQueryParam("provider", equalTo("Dynawo"))
                .withQueryParam("dynamicSimulationResultUuid", equalTo(DYNAMIC_SIMULATION_RESULT_UUID.toString()))
                .withQueryParam("parametersUuid", equalTo(PARAMETERS_UUID.toString()))
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_SECURITY_ANALYSIS.reportKey))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.run("Dynawo", "receiver", NETWORK_UUID,
                "variantId", new ReportInfos(REPORT_UUID, NODE_UUID), DYNAMIC_SIMULATION_RESULT_UUID, PARAMETERS_UUID, "userId"),
                RUN_DYNAMIC_SECURITY_ANALYSIS_FAILED, null);
    }

    @Test
    void testGetStatus() throws Exception {
        // configure mock server response
        String url = RESULT_BASE_URL + DELIMITER + RESULT_UUID + "/status";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(DynamicSecurityAnalysisStatus.SUCCEED))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        DynamicSecurityAnalysisStatus status = dynamicSecurityAnalysisClient.getStatus(RESULT_UUID);

        // check result
        assertThat(status).isEqualTo(DynamicSecurityAnalysisStatus.SUCCEED);
    }

    @Test
    void testInvalidateStatus() {
        String url = RESULT_BASE_URL + "/invalidate-status";

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathTemplate(url))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.invalidateStatus(List.of(RESULT_UUID)));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathTemplate(url))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.notFound()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.invalidateStatus(List.of(RESULT_UUID)),
                DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND, null);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathTemplate(url))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSecurityAnalysisClient.invalidateStatus(List.of(RESULT_UUID)),
                INVALIDATE_DYNAMIC_SECURITY_ANALYSIS_FAILED, null);
    }

    @Test
    void testDeleteResult() {
        // configure mock server response
        String url = RESULT_BASE_URL + DELIMITER + RESULT_UUID;
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.deleteResult(RESULT_UUID));
    }

    @Test
    void testDeleteResults() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(RESULT_BASE_URL))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.deleteResults());
    }

    @Test
    void testResultsCount() throws Exception {
        Integer expectedResultCount = 10;
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(RESULT_BASE_URL))
                .willReturn(WireMock.ok()
                    .withBody(objectMapper.writeValueAsString(expectedResultCount))
                    .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        Integer resultCount = dynamicSecurityAnalysisClient.getResultsCount();
        assertThat(resultCount).isEqualTo(expectedResultCount);
    }
}
