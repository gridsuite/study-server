/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicsimulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.service.client.dynamicsimulation.impl.DynamicSimulationClientImpl;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyBusinessErrorCode.RUN_DYNAMIC_SIMULATION_FAILED;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.RestClient.DELIMITER;
import static org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.TestUtils.assertStudyException;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicSimulationClientTest extends AbstractWireMockRestClientTest {
    public static final String DYNAMIC_SIMUALTION_RUN_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
    public static final String DYNAMIC_SIMULATION_RESULT_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();

    private static final String VARIANT_ID = "variantId";
    private static final String MAPPING_NAME_01 = "_01";

    private static final UUID TIME_SERIES_UUID = UUID.randomUUID();
    private static final UUID TIMELINE_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID RESULT_NOT_FOUND_UUID = UUID.randomUUID();

    private DynamicSimulationClient dynamicSimulationClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        // config client
        remoteServicesProperties.setServiceUri("dynamic-simulation-server", initMockWebServer());
        dynamicSimulationClient = new DynamicSimulationClientImpl(remoteServicesProperties, restTemplate);
    }

    @Test
    void testRun() throws Exception {

        // prepare parameters
        DynamicSimulationParametersInfos parameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
        parameters.setStartTime(0.0);
        parameters.setStopTime(500.0);
        parameters.setMapping(MAPPING_NAME_01);

        // configure mock server response for test case run - networks/{networkUuid}/run?
        String url = DYNAMIC_SIMUALTION_RUN_BASE_URL + DELIMITER + NETWORK_UUID + DELIMITER + "run";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, equalTo(VARIANT_ID))
                .withQueryParam("provider", equalTo(DYNAWO_PROVIDER))
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_SIMULATION.reportKey))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(RESULT_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));

        // call service to test
        UUID resultUuid = dynamicSimulationClient.run(DYNAWO_PROVIDER, "receiver", NETWORK_UUID, VARIANT_ID,
                new ReportInfos(REPORT_UUID, NODE_UUID), parameters, "userId", false);

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);

        // --- Error --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(url))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, absent())
                .withQueryParam("provider", absent())
                .withQueryParam(QUERY_PARAM_RECEIVER, equalTo("receiver"))
                .withQueryParam(QUERY_PARAM_REPORT_UUID, equalTo(REPORT_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORTER_ID, equalTo(NODE_UUID.toString()))
                .withQueryParam(QUERY_PARAM_REPORT_TYPE, equalTo(StudyService.ReportType.DYNAMIC_SIMULATION.reportKey))
                .withHeader(QUERY_PARAM_DEBUG, equalTo("true"))
                .withHeader(HEADER_USER_ID, equalTo("userId"))
                .willReturn(WireMock.serverError()));

        // check result
        assertStudyException(() -> dynamicSimulationClient.run(null, "receiver", NETWORK_UUID, null,
                new ReportInfos(REPORT_UUID, NODE_UUID), parameters, "userId", true), RUN_DYNAMIC_SIMULATION_FAILED, null);

    }

    @Test
    void testGetTimeSeriesResult() throws Exception {

        // configure mock server response for test get timeseries result uuid - results/{resultUuid}/timeseries
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_UUID + DELIMITER + "timeseries"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(TIME_SERIES_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        ));

        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID);

        // check result
        assertThat(timeSeriesUuid).isEqualTo(TIME_SERIES_UUID);
    }

    @Test
    void testGetTimeSeriesResultGivenBadUuid() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_NOT_FOUND_UUID + DELIMITER + "timeseries"))
                .willReturn(WireMock.notFound()
                ));
        assertThrows(StudyException.class, () -> dynamicSimulationClient.getTimeSeriesResult(RESULT_NOT_FOUND_UUID));
    }

    @Test
    void testGetTimelineResult() throws Exception {
        // configure mock server response for test get timeline result - uuid results/{resultUuid}/timeline
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_UUID + DELIMITER + "timeline"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(TIMELINE_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        UUID timelineUuid = dynamicSimulationClient.getTimelineResult(RESULT_UUID);
        // check result
        assertThat(timelineUuid).isEqualTo(TIMELINE_UUID);
    }

    @Test
    void testGetTimelineResultGivenBadUuid() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_NOT_FOUND_UUID + DELIMITER + "timeline"))
                .willReturn(WireMock.notFound()
                ));
        assertThrows(StudyException.class, () -> dynamicSimulationClient.getTimelineResult(RESULT_NOT_FOUND_UUID));
    }

    @Test
    void testGetStatus() throws Exception {

        // configure mock server response for test get status result - results/{resultUuid}/status
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_UUID + DELIMITER + "status"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(DynamicSimulationStatus.CONVERGED))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        DynamicSimulationStatus status = dynamicSimulationClient.getStatus(RESULT_UUID);
        // check result
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test
    void testGetStatusGivenBadUuid() {
        // configure mock server response
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_NOT_FOUND_UUID + DELIMITER + "status"))
                .willReturn(WireMock.notFound()
                ));
        assertThrows(StudyException.class, () -> dynamicSimulationClient.getStatus(RESULT_NOT_FOUND_UUID));
    }

    @Test
    void testInvalidateStatus() throws Exception {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        wireMockServer.stubFor(WireMock.put(WireMock.urlMatching(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(List.of(RESULT_UUID)))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.invalidateStatus(List.of(RESULT_UUID)));
    }

    @Test
    void testInvalidateStatusGivenBadUuid() {
        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_NOT_FOUND_UUID.toString()))
                .willReturn(WireMock.notFound()
                ));
        assertThrows(StudyException.class, () -> dynamicSimulationClient.invalidateStatus(List.of(RESULT_NOT_FOUND_UUID)));
    }

    @Test
    void testDeleteResult() {
        // configure mock server response for test delete result.
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + "?resultsUuids=" + RESULT_UUID))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.deleteResults(List.of(RESULT_UUID)));
    }

    @Test
    void testDeleteResults() throws Exception {
        // configure mock server response for test delete all results - results/
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + "?resultsUuids"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));
        dynamicSimulationClient.deleteAllResults();

        // configure mock server response for test result count - supervision/results-count
        String resultCountEndPointUrl = buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(resultCountEndPointUrl))
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(0))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));
        Integer resultCount = dynamicSimulationClient.getResultsCount();

        // check result
        assertThat(resultCount).isZero();
    }
}
