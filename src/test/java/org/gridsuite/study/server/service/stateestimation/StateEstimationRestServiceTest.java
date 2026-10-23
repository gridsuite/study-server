/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.stateestimation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.gridsuite.study.server.StudyConstants.STATE_ESTIMATION_API_VERSION;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateEstimationRestServiceTest {

    private static final String BASE_URI = "http://state-estimation-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    @Mock
    private RestTemplate restTemplate;

    private StateEstimationRestService stateEstimationRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("state-estimation-server")).thenReturn(BASE_URI);
        stateEstimationRestService = new StateEstimationRestService(remoteServicesProperties, new ObjectMapper(), restTemplate);
    }

    @Test
    void testDownloadDebugFile() {
        UUID resultUuid = UUID.randomUUID();
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource("debug".getBytes()));
        String url = BASE_URI + "/" + STATE_ESTIMATION_API_VERSION + "/results/" + resultUuid + "/download-debug-file";
        when(restTemplate.exchange(url, HttpMethod.GET, null, Resource.class)).thenReturn(response);

        assertThat(stateEstimationRestService.downloadDebugFile(resultUuid)).isEqualTo(response);
    }

    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String RESULTS_PATH = "/" + STATE_ESTIMATION_API_VERSION + "/results/" + RESULT_UUID + "/measurements";
    private static final String GLOBAL_FILTERS = "{\"label\":\"a+b %41 100% & c=d \\\"q\\\" \\\\ é 中\"}";

    private URI requestedMeasurementsUri(String variantId, String globalFilters) {
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn("[]");
        assertThat(stateEstimationRestService.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, variantId, globalFilters)).isEqualTo("[]");
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate).getForObject(uri.capture(), eq(String.class));
        verifyNoMoreInteractions(restTemplate);
        return uri.getValue();
    }

    private static Map<String, String> decodedQuery(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&"))
            .map(parameter -> parameter.split("=", 2))
            .collect(Collectors.toMap(parameter -> parameter[0], parameter -> URLDecoder.decode(parameter[1], StandardCharsets.UTF_8)));
    }

    @Test
    void measurementsUriEncodesGlobalFiltersOnce() {
        URI uri = requestedMeasurementsUri("variant 1&x", GLOBAL_FILTERS);

        assertThat(uri.getScheme() + "://" + uri.getAuthority() + uri.getRawPath()).isEqualTo(BASE_URI + RESULTS_PATH);
        assertThat(uri.getRawQuery()).doesNotContain(" ", "+", "\"", "{")
            .contains("%2B", "%2541", "100%25", "%26", "%3D", "%5C", "%C3%A9");
        assertThat(decodedQuery(uri)).containsOnly(
            Map.entry("globalFilters", GLOBAL_FILTERS),
            Map.entry("networkUuid", NETWORK_UUID.toString()),
            Map.entry("variantId", "variant 1&x"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void measurementsUriOmitsBlankGlobalFiltersAndVariant(String blank) {
        URI uri = requestedMeasurementsUri(blank, blank);

        assertThat(uri.getRawQuery()).isEqualTo("networkUuid=" + NETWORK_UUID);
    }

    @Test
    void measurementsDownstreamErrorsAreNotTranslated() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad", new HttpHeaders(), "{}".getBytes(), null);
        HttpServerErrorException serverError = HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "ko", new HttpHeaders(), "{}".getBytes(), null);
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenThrow(badRequest).thenThrow(serverError);

        assertThatThrownBy(() -> stateEstimationRestService.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, "v", GLOBAL_FILTERS)).isSameAs(badRequest);
        assertThatThrownBy(() -> stateEstimationRestService.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, "v", GLOBAL_FILTERS)).isSameAs(serverError);
    }

    @Test
    void measurementsConnectionFailureIs502AndTimeoutIs504() {
        when(restTemplate.getForObject(any(URI.class), eq(String.class)))
            .thenThrow(new ResourceAccessException("down", new ConnectException()))
            .thenThrow(new ResourceAccessException("slow", new SocketTimeoutException()))
            .thenThrow(new ResourceAccessException("slow", new HttpConnectTimeoutException("slow")));

        assertThat(statusOfFailedMeasurementsRequest()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(statusOfFailedMeasurementsRequest()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(statusOfFailedMeasurementsRequest()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    private HttpStatus statusOfFailedMeasurementsRequest() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> stateEstimationRestService.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, "v", GLOBAL_FILTERS));
        assertThat(exception.getCause()).isInstanceOf(ResourceAccessException.class);
        return HttpStatus.valueOf(exception.getStatusCode().value());
    }
}
