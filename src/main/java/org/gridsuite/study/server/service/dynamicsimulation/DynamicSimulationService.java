/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import org.gridsuite.study.server.StudyException;
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
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
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
        IdaSolverInfos idaSolver = getDefaultIdaSolver();
        SimSolverInfos simSolver = getDefaultSimSolver();
        List<SolverInfos> solvers = List.of(idaSolver, simSolver);

        NetworkInfos network = getDefaultNetwork();
        return new DynamicSimulationParametersInfos(0.0, 500.0, "", idaSolver.getId(), solvers, network, null, null);
    }

    static IdaSolverInfos getDefaultIdaSolver() {
        IdaSolverInfos idaSolver = new IdaSolverInfos();

        // these parameters are taken from solver.par file in dynamic simulation server
        idaSolver.setId("IDA");
        idaSolver.setType(SolverTypeInfos.IDA);
        idaSolver.setOrder(2);
        idaSolver.setInitStep(1.e-7);
        idaSolver.setMinStep(1.e-7);
        idaSolver.setMaxStep(10);
        idaSolver.setAbsAccuracy(1.e-4);
        idaSolver.setRelAccuracy(1.e-4);

        idaSolver.setFNormTolAlg(1.e-4);
        idaSolver.setInitialAddTolAlg(1);
        idaSolver.setScStepTolAlg(1.e-4);
        idaSolver.setMxNewTStepAlg(10000);
        idaSolver.setMsbsetAlg(5);
        idaSolver.setMxIterAlg(30);
        idaSolver.setPrintFlAlg(0);
        idaSolver.setFNormTolAlgJ(1.e-4);
        idaSolver.setInitialAddTolAlgJ(1);
        idaSolver.setScStepTolAlgJ(1.e-4);
        idaSolver.setMxNewTStepAlgJ(10000);
        idaSolver.setMsbsetAlgJ(1);
        idaSolver.setMxIterAlgJ(50);
        idaSolver.setPrintFlAlgJ(0);
        idaSolver.setFNormTolAlgInit(1.e-4);
        idaSolver.setInitialAddTolAlgInit(1);
        idaSolver.setScStepTolAlgInit(1.e-4);
        idaSolver.setMxNewTStepAlgInit(10000);
        idaSolver.setMsbsetAlgInit(1);
        idaSolver.setMxIterAlgInit(50);
        idaSolver.setPrintFlAlgInit(0);
        idaSolver.setMinimalAcceptableStep(1.e-8);
        idaSolver.setMaximumNumberSlowStepIncrease(40);

        return idaSolver;
    }

    static SimSolverInfos getDefaultSimSolver() {
        SimSolverInfos simSolver = new SimSolverInfos();

        // these parameters are taken from solver.par file in dynamic simulation server
        simSolver.setId("SIM");
        simSolver.setType(SolverTypeInfos.SIM);
        simSolver.setHMin(0.001);
        simSolver.setHMax(1);
        simSolver.setKReduceStep(0.5);
        simSolver.setMaxNewtonTry(10);
        simSolver.setLinearSolverName("KLU");

        simSolver.setFNormTol(1.e-3);
        simSolver.setInitialAddTol(1);
        simSolver.setScStepTol(1.e-3);
        simSolver.setMxNewTStep(10000);
        simSolver.setMsbset(0);
        simSolver.setMxIter(15);
        simSolver.setPrintFl(0);
        simSolver.setOptimizeAlgebraicResidualsEvaluations(true);
        simSolver.setSkipNRIfInitialGuessOK(true);
        simSolver.setEnableSilentZ(true);
        simSolver.setOptimizeReInitAlgebraicResidualsEvaluations(true);
        simSolver.setMinimumModeChangeTypeForAlgebraicRestoration("ALGEBRAIC_J_UPDATE");
        simSolver.setMinimumModeChangeTypeForAlgebraicRestorationInit("ALGEBRAIC_J_UPDATE");

        simSolver.setFNormTolAlg(1.e-3);
        simSolver.setInitialAddTolAlg(1);
        simSolver.setScStepTolAlg(1.e-3);
        simSolver.setMxNewTStepAlg(10000);
        simSolver.setMsbsetAlg(5);
        simSolver.setMxIterAlg(30);
        simSolver.setPrintFlAlg(0);
        simSolver.setFNormTolAlgJ(1.e-3);
        simSolver.setInitialAddTolAlgJ(1);
        simSolver.setScStepTolAlgJ(1.e-3);
        simSolver.setMxNewTStepAlgJ(10000);
        simSolver.setMsbsetAlgJ(1);
        simSolver.setMxIterAlgJ(50);
        simSolver.setPrintFlAlgJ(0);
        simSolver.setFNormTolAlgInit(1.e-3);
        simSolver.setInitialAddTolAlgInit(1);
        simSolver.setScStepTolAlgInit(1.e-3);
        simSolver.setMxNewTStepAlgInit(10000);
        simSolver.setMsbsetAlgInit(1);
        simSolver.setMxIterAlgInit(50);
        simSolver.setPrintFlAlgInit(0);
        simSolver.setMinimalAcceptableStep(1.e-3);
        simSolver.setMaximumNumberSlowStepIncrease(40);

        return simSolver;
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
     * Run a dynamic simulation from a given study, node UUID and some configured parameters
     * @param provider name of the dynamic simulation provider, e.g. Dynawo
     * @param nodeUuid node uuid
     * @param rootNetworkUuid root network uuid
     * @param networkUuid network uuid
     * @param variantId variant id
     * @param reportUuid report uuid
     * @param parameters parameters of dynamic simulation
     * @param userId id of user
     * @return the UUID of the dynamic simulation
     */
    UUID runDynamicSimulation(String provider, UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId, UUID reportUuid, DynamicSimulationParametersInfos parameters, String userId);

    /**
     * Get a list of curves from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @param timeSeriesNames a given list of time-series names
     * @return a list of curves
     */
    List<DoubleTimeSeries> getTimeSeriesResult(UUID resultUuid, List<String> timeSeriesNames);

    /**
     * Get timeline from a given result UUID
     *
     * @param resultUuid a given result UUID
     * @return a list of {@link TimelineEventInfos}
     */
    List<TimelineEventInfos> getTimelineResult(UUID resultUuid);

    /**
     * Get the current status of the simulation
     * @param resultUuid a given result UUID
     * @return the status of the dynamic simulation
     */
    DynamicSimulationStatus getStatus(UUID resultUuid);

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
     * @param resultUuid a given result UUID
     * @throws StudyException with type DYNAMIC_SIMULATION_RUNNING if this node is in RUNNING status
     */
    void assertDynamicSimulationNotRunning(UUID resultUuid);

    /**
     * Get mapping names
     * @param studyUuid a given study UUID
     * @return a list of mapping names
     */
    List<MappingInfos> getMappings(UUID studyUuid);

    /**
     * Get list of time-series metadata
     * @param resultUuid a given result UUID
     * @return a list of time-series metadata
     */
    List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID resultUuid);

    /**
     * Get models used in the given mapping
     * @param mapping name of given mapping
     * @return a list of rich models (i.e. including parameter set with parameters)
     */
    List<ModelInfos> getModels(String mapping);
}
