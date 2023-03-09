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
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationExtension;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.DynaWaltzParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.IdaSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SimSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverTypeInfos;
import org.gridsuite.study.server.service.client.AbstractRestClientTest2;
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
public class DynamicSimulationClientTest extends AbstractRestClientTest2 {

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

    /**
     * @return TODO remove before merge
     */
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
    public void testRun() throws JsonProcessingException {

        // prepare parameters
        DynamicSimulationParametersInfos parameters = new DynamicSimulationParametersInfos();
        parameters.setStartTime(0);
        parameters.setStopTime(500);
        parameters.setMapping(MAPPING_NAME_01);

        IdaSolverInfos idaSolver = new IdaSolverInfos();
        idaSolver.setId("1");
        idaSolver.setType(SolverTypeInfos.IDA);
        idaSolver.setOrder(1);
        idaSolver.setInitStep(0.000001);
        idaSolver.setMinStep(0.000001);
        idaSolver.setMaxStep(10);
        idaSolver.setAbsAccuracy(0.0001);
        idaSolver.setRelAccuracy(0.0001);

        SimSolverInfos simSolver = new SimSolverInfos();
        simSolver.setId("3");
        simSolver.setType(SolverTypeInfos.SIM);
        simSolver.setHMin(0.000001);
        simSolver.setHMax(1);
        simSolver.setKReduceStep(0.5);
        simSolver.setNEff(10);
        simSolver.setNDeadband(2);
        simSolver.setMaxRootRestart(3);
        simSolver.setMaxNewtonTry(10);
        simSolver.setLinearSolverName("KLU");
        simSolver.setRecalculateStep(false);

        List<SolverInfos> solvers = List.of(idaSolver, simSolver);
        DynaWaltzParametersInfos dynaWaltzParametersInfos = new DynaWaltzParametersInfos(DynaWaltzParametersInfos.EXTENSION_NAME, solvers.get(0).getId(), solvers);
        List<DynamicSimulationExtension> extensions = List.of(dynaWaltzParametersInfos);

        parameters.setExtensions(extensions);

        // configure mock server response for test case run - networks/{networkUuid}/run?
        String runEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching(runEndPointUrl + NETWORK_UUID_STRING + DELIMITER + "run" + ".*"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(UUID.fromString(RESULT_UUID_STRING)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID resultUuid = dynamicSimulationClient.run("", UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, parameters);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
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
        assertEquals(TIME_SERIES_UUID_STRING, timeSeriesUuid.toString());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeSeriesResultGivenBadUuid() throws JsonProcessingException {

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

        UUID timeLineUuid = dynamicSimulationClient.getTimeLineResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertEquals(TIME_LINE_UUID_STRING, timeLineUuid.toString());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeLineResultGivenBadUuid() {

        // configure mock server response
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching(resultEndPointUrl + RESULT_NOT_FOUND_UUID_STRING + DELIMITER + "timeline"))
                .willReturn(WireMock.notFound()
                ));

        dynamicSimulationClient.getTimeLineResult(UUID.fromString(RESULT_NOT_FOUND_UUID_STRING));
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
        assertEquals(DynamicSimulationStatus.CONVERGED, status);
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
    public void testDeleteResult() {

        // configure mock server response for test delete result - results/{resultUuid}
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.delete(WireMock.urlMatching(resultEndPointUrl + RESULT_UUID_STRING))
                .willReturn(WireMock.ok()
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        dynamicSimulationClient.deleteResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertTrue(true);
    }
}
