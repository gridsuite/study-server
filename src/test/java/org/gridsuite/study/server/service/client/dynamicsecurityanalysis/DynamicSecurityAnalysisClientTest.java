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
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.utils.assertions.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;
import static org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
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
    private static final String PARAMETERS_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);
    private static final String RUN_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN);

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
        String expectedDefaultProvider = "Dynawo";

        // configure mock server response
        String url = buildEndPointUrl("", API_VERSION, null) + "/default-provider";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedDefaultProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String defaultProvider = dynamicSecurityAnalysisClient.getDefaultProvider();

        // check result
        assertThat(defaultProvider).isEqualTo(expectedDefaultProvider);
    }

    @Test
    void testGetProvider() {
        String expectedProvider = "Dynawo";

        // configure mock server response
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String provider = dynamicSecurityAnalysisClient.getProvider(PARAMETERS_UUID);

        // check result
        assertThat(provider).isEqualTo(expectedProvider);
    }

    @Test
    void testUpdateProvider() {
        String newProvider = "Dynawo2";

        // configure mock server response
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                        .withRequestBody(equalTo(newProvider))
                .willReturn(WireMock.ok()
                        .withBody(newProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.updateProvider(PARAMETERS_UUID, newProvider));
    }

    @Test
    void testGetParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();

        // configure mock server response
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;
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
    }

    @Test
    void testCreateParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();

        // configure mock server response
        String parameterJson = objectMapper.writeValueAsString(parameters);
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
    }

    @Test
    void testUpdateParameters() throws Exception {
        DynamicSecurityAnalysisParametersInfos parameters = DynamicSecurityAnalysisParametersInfos.builder()
                .provider("Dynawo")
                .scenarioDuration(50.0)
                .contingenciesStartTime(5.0)
                .build();

        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;
        // configure mock server response
        String parameterJson = objectMapper.writeValueAsString(parameters);
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicSecurityAnalysisClient.updateParameters(PARAMETERS_UUID, parameterJson));
    }

    @Test
    void testDuplicateParameters() throws Exception {
        UUID newParameterUuid = UUID.randomUUID();
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
        // configure mock server response
        String url = PARAMETERS_BASE_URL + "/default";
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(PARAMETERS_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultParametersUuid = dynamicSecurityAnalysisClient.createDefaultParameters();

        // check result
        assertThat(resultParametersUuid).isEqualTo(PARAMETERS_UUID);
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
    }

    @Test
    void testGetStatus() {

    }

    @Test
    void testInvalidateStatus() {

    }

    @Test
    void testDeleteResult() {

    }

    @Test
    void testDeleteResults() {

    }

    @Test
    void testResultsCount() {

    }

}
