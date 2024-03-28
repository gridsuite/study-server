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
import org.gridsuite.study.server.dto.ReportInfos;
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

    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final String VARIANT_1_ID = "variant_1";
    public static final String MAPPING_NAME_01 = "_01";

    public static final UUID TIME_SERIES_UUID = UUID.randomUUID();
    public static final UUID TIMELINE_UUID = UUID.randomUUID();
    public static final UUID RESULT_UUID = UUID.randomUUID();
    public static final UUID RESULT_NOT_FOUND_UUID = UUID.randomUUID();

    public static final UUID REPORT_UUID = UUID.randomUUID();
    public static final UUID REPORT_ID = UUID.randomUUID();

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
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching(runEndPointUrl + NETWORK_UUID + DELIMITER + "run" + ".*"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(RESULT_UUID))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID resultUuid = dynamicSimulationClient.run("", "", NETWORK_UUID, VARIANT_1_ID, new ReportInfos(REPORT_UUID, REPORT_ID.toString()), parameters, "testUserId");

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);
    }

    @Test
    public void testGetTimeSeriesResult() throws JsonProcessingException {

        // configure mock server response for test get timeseries result uuid - results/{resultUuid}/timeseries
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID + DELIMITER + "timeseries"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(TIME_SERIES_UUID))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                        ));

        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID);

        // check result
        assertThat(timeSeriesUuid).isEqualTo(TIME_SERIES_UUID);
    }

    @Test(expected = StudyException.class)
    public void testGetTimeSeriesResultGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID + DELIMITER + "timeseries"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getTimeSeriesResult(RESULT_NOT_FOUND_UUID);
    }

    @Test
    public void testGetTimeLineResult() throws JsonProcessingException {

        // configure mock server response for test get timeline result - uuid results/{resultUuid}/timeline
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID + DELIMITER + "timeline"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(TIMELINE_UUID))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID timelineUuid = dynamicSimulationClient.getTimelineResult(RESULT_UUID);

        // check result
        assertThat(timelineUuid).isEqualTo(TIMELINE_UUID);
    }

    @Test(expected = StudyException.class)
    public void testGetTimeLineResultGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID + DELIMITER + "timeline"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getTimelineResult(RESULT_NOT_FOUND_UUID);
    }

    @Test
    public void testGetStatus() throws JsonProcessingException {

        // configure mock server response for test get status result - results/{resultUuid}/status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID + DELIMITER + "status"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(DynamicSimulationStatus.CONVERGED))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        DynamicSimulationStatus status = dynamicSimulationClient.getStatus(RESULT_UUID);

        // check result
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test(expected = StudyException.class)
    public void testGetStatusGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID + DELIMITER + "status"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getStatus(RESULT_NOT_FOUND_UUID);
    }

    @Test
    public void testInvalidateStatus() throws JsonProcessingException {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.put(WireMock.urlMatching(resultEndPointUrl + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID.toString()))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(List.of(RESULT_UUID)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.invalidateStatus(List.of(RESULT_UUID)));
    }

    @Test(expected = StudyException.class)
    public void testInvalidateStatusGivenBadUuid() {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_NOT_FOUND_UUID.toString()))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.invalidateStatus(List.of(RESULT_NOT_FOUND_UUID));
    }

    @Test
    public void testDeleteResult() {

        // configure mock server response for test delete result - results/{resultUuid}
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID))
                .willReturn(WireMock.ok()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        // test service
        assertDoesNotThrow(() -> dynamicSimulationClient.deleteResult(RESULT_UUID));
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
