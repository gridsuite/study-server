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

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicSimulationClientTest extends AbstractWireMockRestClientTest {
    public static final String DYNAMIC_SIMUALTION_RUN_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
    public static final String DYNAMIC_SIMULATION_RESULT_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final String VARIANT_1_ID = "variant_1";
    private static final String MAPPING_NAME_01 = "_01";

    private static final UUID TIME_SERIES_UUID = UUID.randomUUID();
    private static final UUID TIMELINE_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID RESULT_NOT_FOUND_UUID = UUID.randomUUID();

    private static final UUID REPORT_UUID = UUID.randomUUID();
    private static final UUID REPORTER_ID = UUID.randomUUID();

    private DynamicSimulationClient dynamicSimulationClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    public void setup() {
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
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching(DYNAMIC_SIMUALTION_RUN_BASE_URL + DELIMITER + NETWORK_UUID + DELIMITER + "run" + ".*"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(RESULT_UUID))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));

        UUID resultUuid = dynamicSimulationClient.run("", "", NETWORK_UUID, VARIANT_1_ID, new ReportInfos(REPORT_UUID, REPORTER_ID), parameters, "testUserId");

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);
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
        // configure mock server response for test delete result: "results/{resultUuid}"
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL + DELIMITER + RESULT_UUID))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.deleteResults(List.of(RESULT_UUID)));
    }

    @Test
    void testDeleteResults() throws Exception {
        // configure mock server response for test delete all results - results/
        wireMockServer.stubFor(WireMock.delete(WireMock.urlEqualTo(DYNAMIC_SIMULATION_RESULT_BASE_URL))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));
        dynamicSimulationClient.deleteResults(null);

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
