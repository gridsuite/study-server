/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.powsybl.timeseries.IrregularTimeSeriesIndex;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import com.powsybl.timeseries.TimeSeriesIndex;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})

public class DynamicSimulationServiceTest {

    private static final String MAPPING_NAME_01 = "_01";

    private static final String VARIANT_1_ID = "variant_1";

    private static final int START_TIME = 0;

    private static final int STOP_TIME = 500;

    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";

    private static final String NODE_UUID_STRING = "22222222-0000-0000-0000-000000000000";

    public static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";

    public static final String TIME_SERIES_UUID_STRING = "77777777-0000-0000-0000-000000000000";
    public static final String TIME_LINE_UUID_STRING = "88888888-0000-0000-0000-000000000000";

    @MockBean
    private DynamicSimulationClient dynamicSimulationClient;

    @MockBean
    private TimeSeriesClient timeSeriesClient;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    DynamicSimulationService dynamicSimulationService;

    @Before
    public void setUp() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.run(UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, START_TIME, STOP_TIME, MAPPING_NAME_01)).willReturn(UUID.fromString(RESULT_UUID_STRING));
        given(dynamicSimulationClient.getTimeSeriesResult(UUID.fromString(RESULT_UUID_STRING))).willReturn(UUID.fromString(TIME_SERIES_UUID_STRING));
        given(dynamicSimulationClient.getTimeLineResult(UUID.fromString(RESULT_UUID_STRING))).willReturn(UUID.fromString(TIME_LINE_UUID_STRING));

        // setup timeSeriesClient mock
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));
        given(timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_SERIES_UUID_STRING))).willReturn(timeSeries);

        // timeline
        index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeLine = TimeSeries.createString("TimeLine", index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");
        given(timeSeriesClient.getTimeSeriesGroup(UUID.fromString(TIME_LINE_UUID_STRING))).willReturn(Arrays.asList(timeLine));

        // setup networkModificationTreeService mock
        given(networkModificationTreeService.getDynamicSimulationResultUuid(any())).willReturn(Optional.of(UUID.fromString(RESULT_UUID_STRING)));
    }

    @Test
    public void testRunDynamicSimulation() {
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation(UUID.fromString(NETWORK_UUID_STRING), VARIANT_1_ID, START_TIME, STOP_TIME, MAPPING_NAME_01);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
    }

    @Test
    public void testGetTimeSeriesResult() {
        List<TimeSeries> timeSeries = dynamicSimulationService.getTimeSeriesResult(UUID.fromString(NODE_UUID_STRING));

        // check result
        // must contain two elements
        assertEquals(2, timeSeries.size());
    }

    @Test
    public void testGetTimeLineResult() {
        List<TimeSeries> timeLine = dynamicSimulationService.getTimeLineResult(UUID.fromString(NODE_UUID_STRING));

        // check result
        // must contain only one
        assertEquals(1, timeLine.size());
    }

    @Test
    public void testGetStatus() {

    }

    @Test
    public void testDeleteResult() {

    }

    @Test
    public void testAssertDynamicSimulationNotRunning() {

    }
}
