/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicmargincalculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.utils.assertions.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;
import static org.gridsuite.study.server.service.client.dynamicmargincalculation.DynamicMarginCalculationClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.assertions.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicMarginCalculationClientTest extends AbstractWireMockRestClientTest {
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();

    private static final String PARAMETERS_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);
    private static final String RUN_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN);
    private static final String RESULT_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);
    private static final String PARAMETERS_JSON = "parametersJson";
    private DynamicMarginCalculationClient dynamicMarginCalculationClient;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        // config client
        remoteServicesProperties.setServiceUri("dynamic-margin-calculation-server", initMockWebServer());
        dynamicMarginCalculationClient = new DynamicMarginCalculationClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetDefaultProvider() {
        String url = buildEndPointUrl("", API_VERSION, null) + "/default-provider";

        // --- Success --- //
        String expectedDefaultProvider = DYNAWO_PROVIDER;

        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedDefaultProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String defaultProvider = dynamicMarginCalculationClient.getDefaultProvider();

        // check result
        assertThat(defaultProvider).isEqualTo(expectedDefaultProvider);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.getDefaultProvider()
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.getDefaultProvider()
        );
    }

    @Test
    void testGetProvider() {
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";

        // --- Success --- //
        String expectedProvider = DYNAWO_PROVIDER;

        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(expectedProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        String provider = dynamicMarginCalculationClient.getProvider(PARAMETERS_UUID);

        // check result
        assertThat(provider).isEqualTo(expectedProvider);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.getProvider(PARAMETERS_UUID)
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.getProvider(PARAMETERS_UUID)
        );
    }

    @Test
    void testUpdateProvider() {
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID + "/provider";

        // --- Success --- //
        String newProvider = DYNAWO_PROVIDER + "_2";

        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                        .withRequestBody(equalTo(newProvider))
                .willReturn(WireMock.ok()
                        .withBody(newProvider)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                ));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.updateProvider(PARAMETERS_UUID, newProvider));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.updateProvider(PARAMETERS_UUID, newProvider)
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.updateProvider(PARAMETERS_UUID, newProvider)
        );
    }

    @Test
    void testGetParameters() {
        String parametersJson = PARAMETERS_JSON;

        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(parametersJson)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        String resultParametersJson = dynamicMarginCalculationClient.getParameters(PARAMETERS_UUID, "userId");

        // check result
        assertThat(resultParametersJson).isEqualTo(parametersJson);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.getParameters(PARAMETERS_UUID, "userId")
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.getParameters(PARAMETERS_UUID, "userId")
        );
    }

    @Test
    void testCreateParameters() throws Exception {
        String parameterJson = PARAMETERS_JSON;

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(PARAMETERS_BASE_URL))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(PARAMETERS_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultParametersUuid = dynamicMarginCalculationClient.createParameters(parameterJson);

        // check result
        assertThat(resultParametersUuid).isEqualTo(PARAMETERS_UUID);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(PARAMETERS_BASE_URL))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.createParameters(parameterJson)
        );
    }

    @Test
    void testUpdateParameters() {
        String parameterJson = PARAMETERS_JSON;

        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;

        // --- Success --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.updateParameters(PARAMETERS_UUID, parameterJson));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.updateParameters(PARAMETERS_UUID, parameterJson)
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
                .withRequestBody(equalTo(parameterJson))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.updateParameters(PARAMETERS_UUID, parameterJson)
        );
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
        UUID resultParametersUuid = dynamicMarginCalculationClient.duplicateParameters(PARAMETERS_UUID);

        // check result
        assertThat(resultParametersUuid).isEqualTo(newParameterUuid);

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(PARAMETERS_BASE_URL))
                .withQueryParam("duplicateFrom", equalTo(PARAMETERS_UUID.toString()))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.duplicateParameters(PARAMETERS_UUID)
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(PARAMETERS_BASE_URL))
                .withQueryParam("duplicateFrom", equalTo(PARAMETERS_UUID.toString()))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.duplicateParameters(PARAMETERS_UUID)
        );
    }

    @Test
    void testDeleteParameters() {
        // configure mock server response
        String url = PARAMETERS_BASE_URL + DELIMITER + PARAMETERS_UUID;
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.deleteParameters(PARAMETERS_UUID));
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
        UUID resultParametersUuid = dynamicMarginCalculationClient.createDefaultParameters();

        // check result
        assertThat(resultParametersUuid).isEqualTo(PARAMETERS_UUID);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(url))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.createDefaultParameters()
        );
    }

    @Test
    void testRun() throws Exception {
        UUID expectedResultUuid = UUID.randomUUID();
        // configure mock server response
        String url = RUN_BASE_URL + DELIMITER + NETWORK_UUID + DELIMITER + "run";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, equalTo("variantId"))
                .withQueryParam("provider", equalTo(DYNAWO_PROVIDER))
                .withQueryParam("dynamicSecurityAnalysisParametersUuid", equalTo(DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_UUID.toString()))
                .withQueryParam("parametersUuid", equalTo(PARAMETERS_UUID.toString()))
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_MARGIN_CALCULATION.reportKey))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(expectedResultUuid))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        UUID resultUuid = dynamicMarginCalculationClient.run(DYNAWO_PROVIDER, "receiver", NETWORK_UUID,
               "variantId", new ReportInfos(REPORT_UUID, NODE_UUID), DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_UUID, PARAMETERS_UUID, "", "userId", false);

        // check result
        assertThat(resultUuid).isEqualTo(expectedResultUuid);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, absent())
                .withQueryParam("provider", absent())
                .withQueryParam("dynamicSecurityAnalysisParametersUuid", equalTo(DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_UUID.toString()))
                .withQueryParam("parametersUuid", equalTo(PARAMETERS_UUID.toString()))
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_MARGIN_CALCULATION.reportKey))
                .withHeader(QUERY_PARAM_DEBUG, equalTo("true"))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.run(null, "receiver", NETWORK_UUID, null,
                new ReportInfos(REPORT_UUID, NODE_UUID), DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_UUID, PARAMETERS_UUID,
                "", "userId", true)
        );
    }

    @Test
    void testGetStatus() throws Exception {
        // configure mock server response
        String url = RESULT_BASE_URL + DELIMITER + RESULT_UUID + "/status";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(DynamicMarginCalculationStatus.SUCCEED))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // call service to test
        DynamicMarginCalculationStatus status = dynamicMarginCalculationClient.getStatus(RESULT_UUID);

        // check result
        assertThat(status).isEqualTo(DynamicMarginCalculationStatus.SUCCEED);
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
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.invalidateStatus(List.of(RESULT_UUID)));

        // --- Not Found --- //
        // configure mock server response
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathTemplate(url))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.notFound()));

        // check result
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMarginCalculationClient.invalidateStatus(List.of(RESULT_UUID))
        );

        // --- Error --- //
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathTemplate(url))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.serverError()));

        // check result
        assertThrows(
            HttpServerErrorException.class,
            () -> dynamicMarginCalculationClient.invalidateStatus(List.of(RESULT_UUID))
        );
    }

    @Test
    void testDeleteResult() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(RESULT_BASE_URL + "?resultsUuids=" + RESULT_UUID))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.deleteResults(List.of(RESULT_UUID)));
    }

    @Test
    void testDeleteResults() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(RESULT_BASE_URL + "?resultsUuids"))
                .willReturn(WireMock.ok()));
        // call service to test
        Assertions.assertThatNoException().isThrownBy(() -> dynamicMarginCalculationClient.deleteResults(null));
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
        Integer resultCount = dynamicMarginCalculationClient.getResultsCount();
        assertThat(resultCount).isEqualTo(expectedResultCount);
    }
}
