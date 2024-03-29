/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.service.client.dynamicsimulation.impl.DynamicSimulationClientImpl;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class DynamicSimulationClientTest extends AbstractWireMockRestClientTest {

    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";

    private static final String VARIANT_1_ID = "variant_1";

    public static final String MAPPING_NAME_01 = "_01";

    public static final String TIME_SERIES_UUID_STRING = "77777777-0000-0000-0000-000000000000";
    public static final String TIME_LINE_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    public static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    public static final String RESULT_NOT_FOUND_UUID_STRING = "99999999-1111-0000-0000-000000000000";

    private DynamicSimulationClient dynamicSimulationClient;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    RemoteServicesProperties remoteServicesProperties;

    @Override
    public void setup() {
        super.setup();

        // config client
        remoteServicesProperties.setServiceUri("dynamic-simulation-server", initMockWebServer());
        dynamicSimulationClient = new DynamicSimulationClientImpl(remoteServicesProperties, restTemplate);
    }

    @Test
    public void testRun() throws JsonProcessingException {

        // prepare parameters
        DynamicSimulationParametersInfos parameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
        parameters.setStartTime(0.0);
        parameters.setStopTime(500.0);
        parameters.setMapping(MAPPING_NAME_01);

        // configure mock server response for test case run - networks/{networkUuid}/run?
        String runEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching(runEndPointUrl + NETWORK_UUID_STRING + DELIMITER + "run" + ".*"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(UUID.fromString(RESULT_UUID_STRING)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID resultUuid = dynamicSimulationClient.run("", "", UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, parameters, "testUserId");

        // check result
        assertThat(resultUuid).hasToString(RESULT_UUID_STRING);
    }

    @Test
    public void testGetTimeSeriesResult() throws JsonProcessingException {

        // configure mock server response for test get timeseries result uuid - results/{resultUuid}/timeseries
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID_STRING + DELIMITER + "timeseries"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(UUID.fromString(TIME_SERIES_UUID_STRING)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        ));

        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertThat(timeSeriesUuid).hasToString(TIME_SERIES_UUID_STRING);
    }

    @Test(expected = StudyException.class)
    public void testGetTimeSeriesResultGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID_STRING + DELIMITER + "timeseries"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getTimeSeriesResult(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testGetTimeLineResult() throws JsonProcessingException {

        // configure mock server response for test get timeline result - uuid results/{resultUuid}/timeline
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID_STRING + DELIMITER + "timeline"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(UUID.fromString(TIME_LINE_UUID_STRING)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID timelineUuid = dynamicSimulationClient.getTimelineResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertThat(timelineUuid).hasToString(TIME_LINE_UUID_STRING);
    }

    @Test(expected = StudyException.class)
    public void testGetTimeLineResultGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID_STRING + DELIMITER + "timeline"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getTimelineResult(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testGetStatus() throws JsonProcessingException {

        // configure mock server response for test get status result - results/{resultUuid}/status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID_STRING + DELIMITER + "status"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(DynamicSimulationStatus.CONVERGED))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        DynamicSimulationStatus status = dynamicSimulationClient.getStatus(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test(expected = StudyException.class)
    public void testGetStatusGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID_STRING + DELIMITER + "status"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getStatus(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testInvalidateStatus() throws JsonProcessingException {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.put(WireMock.urlMatching(resultEndPointUrl + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID_STRING))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(List.of(UUID.fromString(RESULT_UUID_STRING))))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.invalidateStatus(List.of(UUID.fromString(RESULT_UUID_STRING))));
    }

    @Test(expected = StudyException.class)
    public void testInvalidateStatusGivenBadUuid() {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_NOT_FOUND_UUID_STRING))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.invalidateStatus(List.of(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING)));
    }

    @Test
    public void testDeleteResult() {

        // configure mock server response for test delete result - results/{resultUuid}
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID_STRING))
                .willReturn(WireMock.ok()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.deleteResult(UUID.fromString(RESULT_UUID_STRING)));
    }

    @Test
    public void testDeleteResults() throws JsonProcessingException {
        // configure mock server response for test delete all results - results/
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching(resultEndPointUrl))
            .willReturn(WireMock.ok()
                .withHeader("Content-Type", "application/json; charset=utf-8")
            ));
        dynamicSimulationClient.deleteResults();

        // configure mock server response for test result count - supervision/results-count
        String resultCountEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultCountEndPointUrl))
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(0))
                .withHeader("Content-Type", "application/json; charset=utf-8")
            ));
        Integer resultCount = dynamicSimulationClient.getResultsCount();

        // check result
        assertThat(resultCount).isZero();
    }
}
