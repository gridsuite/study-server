/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation;

import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.network.NetworkInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.DynaWaltzParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.IdaSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SimSolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver.SolverTypeInfos;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class DynamicSimulationExtensionTest {

    static Logger LOGGER = LoggerFactory.getLogger(DynamicSimulationExtensionTest.class);

    static double DOUBLE_ERROR = 0.000001;

    @Test
    public void testToJsonParseJson() {

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

        DynaWaltzParametersInfos dynaWaltzParametersInfos = new DynaWaltzParametersInfos(DynaWaltzParametersInfos.EXTENSION_NAME, solvers.get(0).getId(), solvers, network);

        List<DynamicSimulationExtension> extensions = List.of(dynaWaltzParametersInfos);

        String resultJson = DynamicSimulationExtension.toJson(extensions);

        LOGGER.info("result json = " + resultJson);
        assertTrue(!Strings.isBlank(resultJson));

        List<DynamicSimulationExtension> outputExtensions = DynamicSimulationExtension.parseJson(resultJson);

        // must have a dynawaltz extension
        assertEquals(1, extensions.size());
        DynaWaltzParametersInfos outputDynaWaltzParametersInfos = (DynaWaltzParametersInfos) outputExtensions.get(0);
        assertEquals("1", outputDynaWaltzParametersInfos.getSolverId());
        // sanity check ida solver init step
        assertEquals(idaSolver.getInitStep(), ((IdaSolverInfos) outputDynaWaltzParametersInfos.getSolvers().get(0)).getInitStep(), DOUBLE_ERROR);

        // sanity check network transformer to LV
        assertEquals(network.getTransformerTolV(), outputDynaWaltzParametersInfos.getNetwork().getTransformerTolV(), DOUBLE_ERROR);
    }

}
