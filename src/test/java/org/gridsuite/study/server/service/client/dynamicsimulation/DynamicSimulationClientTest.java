/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.client.AbstractRestClientTest;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.client.dynamicsimulation.impl.DynamicSimulationClientImpl;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.util.*;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class DynamicSimulationClientTest extends AbstractRestClientTest {

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

    @Override
    @NotNull
    protected Dispatcher getDispatcher() {
        return new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                String path = Objects.requireNonNull(recordedRequest.getPath());
                String runEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
                String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
                String method = recordedRequest.getMethod();
                MockResponse response = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                List<String> pathSegments = emptyIfNull(recordedRequest.getRequestUrl().pathSegments());

                if ("POST".equals(method)
                        && path.matches(runEndPointUrl + ".*")) {

                    String pathEnding = pathSegments.get(pathSegments.size() - 1);

                    if ("run".equals(pathEnding)) { // test case run - networks/{networkUuid}/run?
                        // take {networkUuid} before the last item
                        String networkUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");

                        if (NETWORK_UUID_STRING.equals(networkUuid)) {
                            try {
                                response = new MockResponse()
                                        .setResponseCode(HttpStatus.OK.value())
                                        .addHeader("Content-Type", "application/json; charset=utf-8")
                                        .setBody(objectMapper.writeValueAsString(UUID.fromString(RESULT_UUID_STRING)));
                            } catch (JsonProcessingException e) {
                                return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                            }
                        }
                    }
                } else if ("GET".equals(method)
                        && path.matches(resultEndPointUrl + ".*")) { // test all result cases

                    String pathEnding = pathSegments.get(pathSegments.size() - 1);

                    if ("timeseries".equals(pathEnding)) { // test get timeseries result uuid - results/{resultUuid}/timeseries
                        String resultUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");
                        try {
                            if (RESULT_UUID_STRING.equals(resultUuid)) {
                                response = new MockResponse()
                                        .setResponseCode(HttpStatus.OK.value())
                                        .addHeader("Content-Type", "application/json; charset=utf-8")
                                        .setBody(objectMapper.writeValueAsString(UUID.fromString(TIME_SERIES_UUID_STRING)));
                            }
                        } catch (JsonProcessingException e) {
                            return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                        }
                    } else if ("timeline".equals(pathEnding)) { // test get timeline result - uuid results/{resultUuid}/timeline
                        String resultUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");
                        try {
                            if (RESULT_UUID_STRING.equals(resultUuid)) {
                                response = new MockResponse()
                                        .setResponseCode(HttpStatus.OK.value())
                                        .addHeader("Content-Type", "application/json; charset=utf-8")
                                        .setBody(objectMapper.writeValueAsString(UUID.fromString(TIME_LINE_UUID_STRING)));
                            }
                        } catch (JsonProcessingException e) {
                            return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                        }
                    } else if ("status".equals(pathEnding)) { // test get status result - results/{resultUuid}/status
                        String resultUuid = pathSegments.stream().limit(pathSegments.size() - 1).reduce((first, second) -> second).orElse("");
                        try {
                            if (RESULT_UUID_STRING.equals(resultUuid)) {
                                    response = new MockResponse()
                                            .setResponseCode(HttpStatus.OK.value())
                                            .addHeader("Content-Type", "application/json; charset=utf-8")
                                            .setBody(objectMapper.writeValueAsString(DynamicSimulationStatus.CONVERGED));
                            }
                        } catch (JsonProcessingException e) {
                            return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
                        }

                    }
                } else if ("DELETE".equals(method)
                        && path.matches(resultEndPointUrl + ".*")) { // test delete result - results/{resultUuid}
                    // take {resultUuid} at the last item
                    String resultUuid = pathSegments.stream().reduce((first, second) -> second).orElse("");
                    if (RESULT_UUID_STRING.equals(resultUuid)) {
                        response = new MockResponse()
                                .setResponseCode(HttpStatus.OK.value())
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                }

                return response;
            }
        };
    }

    @Override
    public void setup() {
        super.setup();

        // config client
        dynamicSimulationClient = new DynamicSimulationClientImpl(initMockWebServer(), restTemplate);
    }

    @Test
    public void testRun() {
        UUID resultUuid = dynamicSimulationClient.run("", UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, 0, 500, MAPPING_NAME_01);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
    }

    @Test
    public void testGetTimeSeriesResult() {
        UUID timeSeriesUuid = dynamicSimulationClient.getTimeSeriesResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertEquals(TIME_SERIES_UUID_STRING, timeSeriesUuid.toString());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeSeriesResultGivenBadUuid() {
        dynamicSimulationClient.getTimeSeriesResult(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testGetTimeLineResult() {
        UUID timeLineUuid = dynamicSimulationClient.getTimeLineResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertEquals(TIME_LINE_UUID_STRING, timeLineUuid.toString());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeLineResultGivenBadUuid() {
        dynamicSimulationClient.getTimeLineResult(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testGetStatus() {
        DynamicSimulationStatus status = dynamicSimulationClient.getStatus(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertEquals(DynamicSimulationStatus.CONVERGED, status);
    }

    @Test(expected = StudyException.class)
    public void testGetStatusGivenBadUuid() {
        dynamicSimulationClient.getStatus(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
    }

    @Test
    public void testDeleteResult() {
        dynamicSimulationClient.deleteResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertTrue(true);
    }
}
