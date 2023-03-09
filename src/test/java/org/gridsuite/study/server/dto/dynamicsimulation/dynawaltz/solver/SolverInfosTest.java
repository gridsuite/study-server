/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.dynawaltz.solver;

import org.apache.logging.log4j.util.Strings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class SolverInfosTest {

    static Logger LOGGER = LoggerFactory.getLogger(SolverInfosTest.class);

    @Test
    public void testToJson() {
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

        String resultJson = SolverInfos.toJson(solvers);
        LOGGER.info("result json = " + resultJson);

        assertTrue(!Strings.isBlank(resultJson));
    }

    @Test
    public void testParseJson() {
        String json = "[ {\n" +
                "  \"id\" : \"1\",\n" +
                "  \"type\" : \"IDA\",\n" +
                "  \"order\" : 1,\n" +
                "  \"initStep\" : 1.0E-6,\n" +
                "  \"minStep\" : 1.0E-6,\n" +
                "  \"maxStep\" : 10.0,\n" +
                "  \"absAccuracy\" : 1.0E-4,\n" +
                "  \"relAccuracy\" : 1.0E-4\n" +
                "}, {\n" +
                "  \"id\" : \"3\",\n" +
                "  \"type\" : \"SIM\",\n" +
                "  \"maxRootRestart\" : 3,\n" +
                "  \"maxNewtonTry\" : 10,\n" +
                "  \"linearSolverName\" : \"KLU\",\n" +
                "  \"recalculateStep\" : false,\n" +
                "  \"hMin\" : 1.0E-6,\n" +
                "  \"hMax\" : 1.0,\n" +
                "  \"kReduceStep\" : 0.5,\n" +
                "  \"nEff\" : 10.0,\n" +
                "  \"nDeadband\" : 2\n" +
                "} ]";

        List<SolverInfos> solvers = SolverInfos.parseJson(json);

        assertEquals(2, solvers.size());
    }
}
