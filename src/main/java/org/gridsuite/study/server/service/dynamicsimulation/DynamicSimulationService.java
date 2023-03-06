/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.StringTimeSeries;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationExtension;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.DynaWaltzParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.IdaSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SimSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverTypeInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.repository.DynamicSimulationParametersEntity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_EXTENSION_NOT_SUPPORTED;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public interface DynamicSimulationService {

    static DynamicSimulationParametersEntity toEntity(DynamicSimulationParametersInfos parametersInfos) {
        Objects.requireNonNull(parametersInfos);
        DynamicSimulationParametersEntity entity = new DynamicSimulationParametersEntity();

        // basic parameters independent to extensions
        entity.setStartTime(parametersInfos.getStartTime());
        entity.setStopTime(parametersInfos.getStopTime());
        entity.setMapping(parametersInfos.getMapping());

        // parameters of extensions
        List<DynamicSimulationExtension> extensions = parametersInfos.getExtensions();
        for (DynamicSimulationExtension extension : extensions) {
            if (extension instanceof DynaWaltzParametersInfos) {
                DynaWaltzParametersInfos dynaWaltzExtension = (DynaWaltzParametersInfos) extension;
                entity.setSolverId(dynaWaltzExtension.getSolverId());
                entity.setSolvers(SolverInfos.toJson(dynaWaltzExtension.getSolvers()));
            } else {
                throw new StudyException(DYNAMIC_SIMULATION_EXTENSION_NOT_SUPPORTED, String.format("Dynamic simulation extension %s is not supported", extension.getClass().getSimpleName()));
            }
        }

        return entity;
    }

    static DynamicSimulationParametersInfos fromEntity(DynamicSimulationParametersEntity entity) {
        Objects.requireNonNull(entity);
        DynamicSimulationParametersInfos parametersInfos = new DynamicSimulationParametersInfos();

        // basic parameters independent to extensions
        parametersInfos.setStartTime(entity.getStartTime());
        parametersInfos.setStopTime(entity.getStopTime());
        parametersInfos.setMapping(entity.getMapping());

        // parameters of extensions
        // DynaWaltz extension
        String solversJson = entity.getSolvers();
        List<SolverInfos> solvers = SolverInfos.parseJson(solversJson);
        String solverId = entity.getSolverId();
        DynaWaltzParametersInfos dynaWaltzParametersExtension = new DynaWaltzParametersInfos();
        dynaWaltzParametersExtension.setSolverId(solverId);
        dynaWaltzParametersExtension.setSolvers(solvers);

        parametersInfos.setExtensions(List.of(dynaWaltzParametersExtension));

        return parametersInfos;
    }

    /**
     * get default dynamic simulation parameters
     * @return a default dynamic simulation parameters
     */
    static DynamicSimulationParametersInfos getDefaultDynamicSimulationParameters() {
        // these parameters are taken from solver.par file in dynamic simulation server
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
        return new DynamicSimulationParametersInfos(0, 500, "", List.of(new DynaWaltzParametersInfos(DynaWaltzParametersInfos.EXTENSION_NAME, solvers.get(0).getId(), solvers)));
    }

    /**
     * Run a dynamic simulation from a given network UUID and some configured parameters
     * @param networkUuid
     * @param variantId
     * @param startTime
     * @param stopTime
     * @param mappingName
     * @return the UUID of the dynamic simulation
     */
    UUID runDynamicSimulation(String receiver, UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName);

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
     * Delete result uuid
     * @param resultUuid a given result UUID
     */
    void deleteResult(UUID resultUuid);

    /**
     * @param nodeUuid a given node UUID
     * @return StudyException(DYNAMIC_SIMULATION_RUNNING) if ce node in RUNNING status
     */
    void assertDynamicSimulationNotRunning(UUID nodeUuid);

    /**
     * Get mapping names
     * @param nodeUuid a given node UUID
     * @return a list of mapping names
     */
    List<MappingInfos> getMappings(UUID nodeUuid);

    /**
     * Get list of timeseries metadata
     * @param nodeUuid a given node UUID
     * @return a list of timeseries metadata
     */
    List<TimeSeriesMetadataInfos> getTimeSeriesMetadataList(UUID nodeUuid);
}
