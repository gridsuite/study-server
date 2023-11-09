/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(JUnit4.class)
public class SolverInfosTest {

    static Logger LOGGER = LoggerFactory.getLogger(SolverInfosTest.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testToJson() {
        IdaSolverInfos idaSolver = DynamicSimulationService.getDefaultIdaSolver();

        SimSolverInfos simSolver = DynamicSimulationService.getDefaultSimSolver();

        List<SolverInfos> solvers = List.of(idaSolver, simSolver);

        String resultJson = SolverInfos.toJson(solvers, objectMapper);
        LOGGER.info("result json = " + resultJson);

        assertTrue(!Strings.isBlank(resultJson));
    }

    @Test
    public void testParseJson() {
        String json = """
            [ {
              "id" : "IDA",
              "type" : "IDA",
              "order" : 2,
              "initStep" : 1.0E-7,
              "minStep" : 1.0E-7,
              "maxStep" : 10.0,
              "absAccuracy" : 1.0E-4,
              "relAccuracy" : 1.0E-4,
              "fnormtolAlg" : 1.0E-4,
              "initialaddtolAlg" : 1.0,
              "scsteptolAlg" : 1.0E-4,
              "mxnewtstepAlg" : 10000.0,
              "msbsetAlg" : 5,
              "mxiterAlg" : 30,
              "printflAlg" : 0,
              "fnormtolAlgJ" : 1.0E-4,
              "initialaddtolAlgJ" : 1.0,
              "scsteptolAlgJ" : 1.0E-4,
              "mxnewtstepAlgJ" : 10000.0,
              "msbsetAlgJ" : 1,
              "mxiterAlgJ" : 50,
              "printflAlgJ" : 0,
              "fnormtolAlgInit" : 1.0E-4,
              "initialaddtolAlgInit" : 1.0,
              "scsteptolAlgInit" : 1.0E-4,
              "mxnewtstepAlgInit" : 10000.0,
              "msbsetAlgInit" : 1,
              "mxiterAlgInit" : 50,
              "printflAlgInit" : 0,
              "maximumNumberSlowStepIncrease" : 40,
              "minimalAcceptableStep" : 1.0E-8
            }, {
              "id" : "SIM",
              "type" : "SIM",
              "maxNewtonTry" : 10,
              "linearSolverName" : "KLU",
              "fnormtol" : 0.001,
              "initialaddtol" : 1.0,
              "scsteptol" : 0.001,
              "mxnewtstep" : 10000.0,
              "msbset" : 0,
              "mxiter" : 15,
              "printfl" : 0,
              "optimizeAlgebraicResidualsEvaluations" : true,
              "skipNRIfInitialGuessOK" : true,
              "enableSilentZ" : true,
              "optimizeReinitAlgebraicResidualsEvaluations" : true,
              "minimumModeChangeTypeForAlgebraicRestoration" : "ALGEBRAIC_J_UPDATE",
              "minimumModeChangeTypeForAlgebraicRestorationInit" : "ALGEBRAIC_J_UPDATE",
              "fnormtolAlg" : 0.001,
              "initialaddtolAlg" : 1.0,
              "scsteptolAlg" : 0.001,
              "mxnewtstepAlg" : 10000.0,
              "msbsetAlg" : 5,
              "mxiterAlg" : 30,
              "printflAlg" : 0,
              "fnormtolAlgJ" : 0.001,
              "initialaddtolAlgJ" : 1.0,
              "scsteptolAlgJ" : 0.001,
              "mxnewtstepAlgJ" : 10000.0,
              "msbsetAlgJ" : 1,
              "mxiterAlgJ" : 50,
              "printflAlgJ" : 0,
              "fnormtolAlgInit" : 0.001,
              "initialaddtolAlgInit" : 1.0,
              "scsteptolAlgInit" : 0.001,
              "mxnewtstepAlgInit" : 10000.0,
              "msbsetAlgInit" : 1,
              "mxiterAlgInit" : 50,
              "printflAlgInit" : 0,
              "maximumNumberSlowStepIncrease" : 40,
              "minimalAcceptableStep" : 0.001,
              "hMin" : 0.001,
              "hMax" : 1.0,
              "kReduceStep" : 0.5
            } ]
            """;

        List<SolverInfos> solvers = SolverInfos.parseJson(json, objectMapper);

        assertEquals(2, solvers.size());
    }
}
