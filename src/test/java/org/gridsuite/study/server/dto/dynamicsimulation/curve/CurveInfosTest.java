/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.dynamicsimulation.curve;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(JUnit4.class)
public class CurveInfosTest {

    public static final String CURVE_JSON_FILE_RESOURCE = "/dto/dynamicsimulation/curve/curve.json";
    static Logger LOGGER = LoggerFactory.getLogger(CurveInfosTest.class);

    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testParseAndToJson() throws IOException {
        // load json to parse
        String jsonToParse = new String(getClass().getResourceAsStream(CURVE_JSON_FILE_RESOURCE).readAllBytes());

        // call method to be tested
        List<CurveInfos> curves = CurveInfos.parseJson(jsonToParse);
        String resultCurvesJson = CurveInfos.toJson(curves);

        // check results
        LOGGER.info("Expect curve json = " + jsonToParse);
        LOGGER.info("Result curve json = " + resultCurvesJson);

        assertEquals(objectMapper.readTree(jsonToParse.getBytes()), objectMapper.readTree(resultCurvesJson.getBytes()));
    }
}
