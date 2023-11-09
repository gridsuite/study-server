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
              "initialAddTolAlg" : 1.0,
              "scStepTolAlg" : 1.0E-4,
              "mxNewTStepAlg" : 10000.0,
              "msbsetAlg" : 5,
              "mxIterAlg" : 30,
              "printFlAlg" : 0,
              "initialAddTolAlgJ" : 1.0,
              "scStepTolAlgJ" : 1.0E-4,
              "mxNewTStepAlgJ" : 10000.0,
              "msbsetAlgJ" : 1,
              "mxIterAlgJ" : 50,
              "printFlAlgJ" : 0,
              "initialAddTolAlgInit" : 1.0,
              "scStepTolAlgInit" : 1.0E-4,
              "mxNewTStepAlgInit" : 10000.0,
              "msbsetAlgInit" : 1,
              "mxIterAlgInit" : 50,
              "printFlAlgInit" : 0,
              "maximumNumberSlowStepIncrease" : 40,
              "minimalAcceptableStep" : 1.0E-8,
              "order" : 2,
              "initStep" : 1.0E-7,
              "minStep" : 1.0E-7,
              "maxStep" : 10.0,
              "absAccuracy" : 1.0E-4,
              "relAccuracy" : 1.0E-4,
              "fNormTolAlg" : 1.0E-4,
              "fNormTolAlgJ" : 1.0E-4,
              "fNormTolAlgInit" : 1.0E-4
            }, {
              "id" : "SIM",
              "type" : "SIM",
              "initialAddTolAlg" : 1.0,
              "scStepTolAlg" : 0.001,
              "mxNewTStepAlg" : 10000.0,
              "msbsetAlg" : 5,
              "mxIterAlg" : 30,
              "printFlAlg" : 0,
              "initialAddTolAlgJ" : 1.0,
              "scStepTolAlgJ" : 0.001,
              "mxNewTStepAlgJ" : 10000.0,
              "msbsetAlgJ" : 1,
              "mxIterAlgJ" : 50,
              "printFlAlgJ" : 0,
              "initialAddTolAlgInit" : 1.0,
              "scStepTolAlgInit" : 0.001,
              "mxNewTStepAlgInit" : 10000.0,
              "msbsetAlgInit" : 1,
              "mxIterAlgInit" : 50,
              "printFlAlgInit" : 0,
              "maximumNumberSlowStepIncrease" : 40,
              "minimalAcceptableStep" : 0.001,
              "maxNewtonTry" : 10,
              "linearSolverName" : "KLU",
              "initialAddTol" : 1.0,
              "scStepTol" : 0.001,
              "mxNewTStep" : 10000.0,
              "msbset" : 0,
              "mxIter" : 15,
              "printFl" : 0,
              "optimizeAlgebraicResidualsEvaluations" : true,
              "skipNRIfInitialGuessOK" : true,
              "enableSilentZ" : true,
              "optimizeReInitAlgebraicResidualsEvaluations" : true,
              "minimumModeChangeTypeForAlgebraicRestoration" : "ALGEBRAIC_J_UPDATE",
              "minimumModeChangeTypeForAlgebraicRestorationInit" : "ALGEBRAIC_J_UPDATE",
              "fNormTolAlg" : 0.001,
              "fNormTolAlgJ" : 0.001,
              "fNormTolAlgInit" : 0.001,
              "hMin" : 0.001,
              "hMax" : 1.0,
              "kReduceStep" : 0.5,
              "fNormTol" : 0.001
            } ]
            """;

        List<SolverInfos> solvers = SolverInfos.parseJson(json, objectMapper);

        assertEquals(2, solvers.size());
    }
}
