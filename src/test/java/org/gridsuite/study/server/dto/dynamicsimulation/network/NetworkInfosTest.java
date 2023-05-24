/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.dynamicsimulation.solver.SolverInfosTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(JUnit4.class)
public class NetworkInfosTest {

    static Logger LOGGER = LoggerFactory.getLogger(SolverInfosTest.class);

    static double DOUBLE_ERROR = 0.000001;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testToJsonParseJson() {
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

        String resultJson = NetworkInfos.toJson(network, objectMapper);
        LOGGER.info("result json = " + resultJson);

        assertTrue(!Strings.isBlank(resultJson));

        NetworkInfos outputNetwork = NetworkInfos.parseJson(resultJson, objectMapper);

        assertEquals(network.getCapacitorNoReclosingDelay(), outputNetwork.getCapacitorNoReclosingDelay(), DOUBLE_ERROR);
        assertEquals(network.getTransformerTolV(), outputNetwork.getTransformerTolV(), DOUBLE_ERROR);
    }

}
