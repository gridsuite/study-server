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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.network.NetworkInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.IdaSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SimSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SolverTypeInfos;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.client.dynamicsimulation.impl.DynamicSimulationClientImpl;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        parameters.setStartTime(0.0);
        parameters.setStopTime(500.0);
        parameters.setMapping(MAPPING_NAME_01);

        // solvers
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

        parameters.setSolverId(idaSolver.getId());
        parameters.setSolvers(solvers);

        // network
        NetworkInfos network = new NetworkInfos();
        network.setCapacitorNoReclosingDelay(300);
        network.setDanglingLineCurrentLimitMaxTimeOperation(90);
        network.setLineCurrentLimitMaxTimeOperation(90);
        network.setLoadTp(90);
        network.setLoadTq(90);
        network.setLoadAlpha(1);
        network.setLoadAlphaLong(0);
        network.setLoadBeta(2);
        network.setLoadBetaLong(0);
        network.setLoadIsControllable(false);
        network.setLoadIsRestorative(false);
        network.setLoadZPMax(100);
        network.setLoadZQMax(100);
        network.setReactanceNoReclosingDelay(0);
        network.setTransformerCurrentLimitMaxTimeOperation(90);
        network.setTransformerT1StHT(60);
        network.setTransformerT1StTHT(30);
        network.setTransformerTNextHT(10);
        network.setTransformerTNextTHT(10);
        network.setTransformerTolV(0.015);

        parameters.setNetwork(network);

        // configure mock server response for test case run - networks/{networkUuid}/run?
        String runEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);
        wireMockServer.stubFor(WireMock.post(WireMock.urlMatching(runEndPointUrl + NETWORK_UUID_STRING + DELIMITER + "run" + ".*"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(UUID.fromString(RESULT_UUID_STRING)))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        UUID resultUuid = dynamicSimulationClient.run("", "", UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, parameters);

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
    public void testInvalidateStatus() throws JsonProcessingException {

        // configure mock server response for test get status result - results/{resultUuid}/invalidate-status
        String resultEndPointUrl = UrlUtil.buildEndPointUrl("", API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        wireMockServer.stubFor(WireMock.put(WireMock.urlMatching(resultEndPointUrl + "invalidate-status" + ".*"))
                .withQueryParam("resultUuid", equalTo(RESULT_UUID_STRING))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(List.of(UUID.fromString(RESULT_UUID_STRING))))
                        .withHeader("Content-Type", "application/json; charset=utf-8")
                ));

        dynamicSimulationClient.invalidateStatus(List.of(UUID.fromString(RESULT_UUID_STRING)));

        // check result
        assertTrue(true);
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

        dynamicSimulationClient.deleteResult(UUID.fromString(RESULT_UUID_STRING));

        // check result
        assertTrue(true);
    }
}
