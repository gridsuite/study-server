/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicsecurityanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisParametersInfos;
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
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;
import static org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient.API_VERSION;
import static org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient.DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.assertions.Assertions.assertThat;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicSecurityAnalysisClientTest extends AbstractWireMockRestClientTest {
    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    public static final String PARAMETERS_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

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
        String baseUrl = buildEndPointUrl("", API_VERSION, null) + "/default-provider";
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(baseUrl))
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
        String baseUrl = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(baseUrl))
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
        String baseUrl = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(baseUrl))
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
        String baseUrl = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(baseUrl))
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
    void testCreateParameters() {

    }

    @Test
    void testUpdateParameters() {

    }

    @Test
    void testDuplicateParameters() {

    }

    @Test
    void testDeleteParameters() {

    }

    @Test
    void testCreateDefaultParameters() {

    }

    @Test
    void testRun() {

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
