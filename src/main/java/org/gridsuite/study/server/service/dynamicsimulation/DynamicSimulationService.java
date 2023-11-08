/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.curve.CurveInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.network.NetworkInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.IdaSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SimSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SolverTypeInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.repository.DynamicSimulationParametersEntity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationService {

    static DynamicSimulationParametersEntity toEntity(DynamicSimulationParametersInfos parametersInfos, ObjectMapper objectMapper) {
        Objects.requireNonNull(parametersInfos);
        DynamicSimulationParametersEntity entity = new DynamicSimulationParametersEntity();

        // basic parameters independent to extensions
        entity.setStartTime(parametersInfos.getStartTime());
        entity.setStopTime(parametersInfos.getStopTime());
        entity.setMapping(parametersInfos.getMapping());

        // solvers parameter
        entity.setSolverId(parametersInfos.getSolverId());
        entity.setSolvers(SolverInfos.toJson(parametersInfos.getSolvers(), objectMapper));

        // network parameter
        entity.setNetwork(NetworkInfos.toJson(parametersInfos.getNetwork(), objectMapper));

        // curves parameter
        entity.setCurves(CurveInfos.toJson(parametersInfos.getCurves(), objectMapper));

        return entity;
    }

    static DynamicSimulationParametersInfos fromEntity(DynamicSimulationParametersEntity entity, ObjectMapper objectMapper) {
        Objects.requireNonNull(entity);
        DynamicSimulationParametersInfos parametersInfos = new DynamicSimulationParametersInfos();

        // basic parameters independent to extensions
        parametersInfos.setStartTime(entity.getStartTime());
        parametersInfos.setStopTime(entity.getStopTime());
        parametersInfos.setMapping(entity.getMapping());

        // solvers parameter
        String solversJson = entity.getSolvers();
        List<SolverInfos> solvers = SolverInfos.parseJson(solversJson, objectMapper);
        String solverId = entity.getSolverId();

        parametersInfos.setSolverId(solverId);
        parametersInfos.setSolvers(solvers);

        // network parameter
        String networkJson = entity.getNetwork();
        NetworkInfos network = networkJson != null ? NetworkInfos.parseJson(networkJson, objectMapper) : getDefaultNetwork();
        parametersInfos.setNetwork(network);

        // curves parameter
        String curvesJson = entity.getCurves();
        List<CurveInfos> curves = curvesJson != null ? CurveInfos.parseJson(curvesJson, objectMapper) : Collections.emptyList();
        parametersInfos.setCurves(curves);

        return parametersInfos;
    }

    /**
     * get default dynamic simulation parameters
     * @return a default dynamic simulation parameters
     */
    static DynamicSimulationParametersInfos getDefaultDynamicSimulationParameters() {
        // these parameters are taken from solver.par file in dynamic simulation server
        IdaSolverInfos idaSolver = new IdaSolverInfos();
        idaSolver.setId("IDA");
        idaSolver.setType(SolverTypeInfos.IDA);
        idaSolver.setOrder(2);
        idaSolver.setInitStep(1.e-7);
        idaSolver.setMinStep(1.e-7);
        idaSolver.setMaxStep(10);
        idaSolver.setAbsAccuracy(1.e-4);
        idaSolver.setRelAccuracy(1.e-4);
        idaSolver.setFnormtolAlg(1.e-4);
        idaSolver.setInitialaddtolAlg(1);
        idaSolver.setScsteptolAlg(1.e-4);
        idaSolver.setMxnewtstepAlg(10000);
        idaSolver.setMsbsetAlg(5);
        idaSolver.setMxiterAlg(30);
        idaSolver.setPrintflAlg(0);
        idaSolver.setFnormtolAlgJ(1.e-4);
        idaSolver.setInitialaddtolAlgJ(1);
        idaSolver.setScsteptolAlgJ(1.e-4);
        idaSolver.setMxnewtstepAlgJ(10000);
        idaSolver.setMsbsetAlgJ(1);
        idaSolver.setMxiterAlgJ(50);
        idaSolver.setPrintflAlgJ(0);
        idaSolver.setFnormtolAlgInit(1.e-4);
        idaSolver.setInitialaddtolAlgInit(1);
        idaSolver.setScsteptolAlgInit(1.e-4);
        idaSolver.setMxnewtstepAlgInit(10000);
        idaSolver.setMsbsetAlgInit(1);
        idaSolver.setMxiterAlgInit(50);
        idaSolver.setPrintflAlgInit(0);
        idaSolver.setMinimalAcceptableStep(1.e-8);
        idaSolver.setMaximumNumberSlowStepIncrease(40);

        SimSolverInfos simSolver = new SimSolverInfos();
        simSolver.setId("SIM");
        simSolver.setType(SolverTypeInfos.SIM);
        simSolver.setHMin(0.001);
        simSolver.setHMax(1);
        simSolver.setKReduceStep(0.5);
        simSolver.setMaxNewtonTry(10);
        simSolver.setLinearSolverName("KLU");
        simSolver.setFnormtol(1.e-3);
        simSolver.setInitialaddtol(1);
        simSolver.setScsteptol(1.e-3);
        simSolver.setMxnewtstep(10000);
        simSolver.setMsbset(0);
        simSolver.setMxiter(15);
        simSolver.setPrintfl(0);
        simSolver.setOptimizeAlgebraicResidualsEvaluations(true);
        simSolver.setSkipNRIfInitialGuessOK(true);
        simSolver.setEnableSilentZ(true);
        simSolver.setOptimizeReinitAlgebraicResidualsEvaluations(true);
        simSolver.setMinimumModeChangeTypeForAlgebraicRestoration("ALGEBRAIC_J_UPDATE");
        simSolver.setMinimumModeChangeTypeForAlgebraicRestorationInit("ALGEBRAIC_J_UPDATE");
        simSolver.setFnormtolAlg(1.e-3);
        simSolver.setInitialaddtolAlg(1);
        simSolver.setScsteptolAlg(1.e-3);
        simSolver.setMxnewtstepAlg(10000);
        simSolver.setMsbsetAlg(5);
        simSolver.setMxiterAlg(30);
        simSolver.setPrintflAlg(0);
        simSolver.setFnormtolAlgJ(1.e-3);
        simSolver.setInitialaddtolAlgJ(1);
        simSolver.setScsteptolAlgJ(1.e-3);
        simSolver.setMxnewtstepAlgJ(10000);
        simSolver.setMsbsetAlgJ(1);
        simSolver.setMxiterAlgJ(50);
        simSolver.setPrintflAlgJ(0);
        simSolver.setFnormtolAlgInit(1.e-3);
        simSolver.setInitialaddtolAlgInit(1);
        simSolver.setScsteptolAlgInit(1.e-3);
        simSolver.setMxnewtstepAlgInit(10000);
        simSolver.setMsbsetAlgInit(1);
        simSolver.setMxiterAlgInit(50);
        simSolver.setPrintflAlgInit(0);
        simSolver.setMinimalAcceptableStep(1.e-3);
        simSolver.setMaximumNumberSlowStepIncrease(40);

        List<SolverInfos> solvers = List.of(idaSolver, simSolver);

        NetworkInfos network = getDefaultNetwork();
        return new DynamicSimulationParametersInfos(0.0, 500.0, "", idaSolver.getId(), solvers, network, null, null);
    }

    static NetworkInfos getDefaultNetwork() {
        // these parameters are taken from network.par file in dynamic simulation server
        NetworkInfos network = new NetworkInfos();
        network.setCapacitorNoReclosingDelay(300);
        network.setDanglingLineCurrentLimitMaxTimeOperation(240);
        network.setLineCurrentLimitMaxTimeOperation(240);
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
        network.setTransformerCurrentLimitMaxTimeOperation(240);
        network.setTransformerT1StHT(60);
        network.setTransformerT1StTHT(30);
        network.setTransformerTNextHT(10);
        network.setTransformerTNextTHT(10);
        network.setTransformerTolV(0.015);

        return network;
    }

    /**
     * Run a dynamic simulation from a given network UUID and some configured parameters
     * @param provider
     * @param receiver
     * @param networkUuid
     * @param variantId
     * @param parameters
     * @return the UUID of the dynamic simulation
     */
    UUID runDynamicSimulation(String provider, String receiver, UUID networkUuid, String variantId, DynamicSimulationParametersInfos parameters);

    /**
     * Get a list of curves from a given node UUID
     *
     * @param nodeUuid a given node UUID
     * @param timeSeriesNames a given list of time-series names
     * @return a list of curves
     */
    List<DoubleTimeSeries> getTimeSeriesResult(UUID nodeUuid, List<String> timeSeriesNames);

    /**
     * Get timeline from a given node UUID
     *
     * @param nodeUuid a given node UUID
     * @return a list of timeline (only one element)
     */
    List<StringTimeSeries> getTimeLineResult(UUID nodeUuid);

    /**
     * Get the current status of the simulation
     * @param nodeUuid a given node UUID
     * @return the status of the dynamic simulation
     */
    DynamicSimulationStatus getStatus(UUID nodeUuid);

    /**
     * invalidate status of the simulation results
     * @param resultUuids a given list of result UUIDs
     */
    void invalidateStatus(List<UUID> resultUuids);

    /**
     * Delete result uuid
     * @param resultUuid a given result UUID
     */
    void deleteResult(UUID resultUuid);

    /**
     * Delete all results
     */
    void deleteResults();

    /**
     * Get results count
     */
    Integer getResultsCount();

    /**
     * @param nodeUuid a given node UUID
     * @return StudyException(DYNAMIC_SIMULATION_RUNNING) if ce node in RUNNING status
     */
    void assertDynamicSimulationNotRunning(UUID nodeUuid);

    /**
     * Get mapping names
     * @param studyUuid a given study UUID
     * @return a list of mapping names
     */
    List<MappingInfos> getMappings(UUID studyUuid);

    /**
     * Get list of timeseries metadata
     * @param nodeUuid a given node UUID
     * @return a list of timeseries metadata
     */
    List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID nodeUuid);

    /**
     * Get models used in the given mapping
     * @param mapping
     * @return a list of rich models (i.e. including parameter set with parameters)
     */
    List<ModelInfos> getModels(String mapping);
}
