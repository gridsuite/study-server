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
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.BDDMockito.given;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
public class DynamicSimulationServiceTest {

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = Arrays.asList(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final String VARIANT_1_ID = "variant_1";

    private static final int START_TIME = 0;

    private static final int STOP_TIME = 500;

    // converged node
    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    public static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);

    private static final String NODE_UUID_STRING = "22222222-0000-0000-0000-000000000000";
    public static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

    public static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    public static final UUID RESULT_UUID = UUID.fromString(RESULT_UUID_STRING);

    public static final String TIME_SERIES_UUID_STRING = "77777777-0000-0000-0000-000000000000";
    public static final UUID TIME_SERIES_UUID = UUID.fromString(TIME_SERIES_UUID_STRING);

    public static final String TIME_LINE_UUID_STRING = "88888888-0000-0000-0000-000000000000";
    public static final UUID TIME_LINE_UUID = UUID.fromString(TIME_LINE_UUID_STRING);

    // running node
    private static final String NODE_UUID_RUNNING_STRING = "22222222-1111-0000-0000-000000000000";
    public static final UUID NODE_UUID_RUNNING = UUID.fromString(NODE_UUID_RUNNING_STRING);

    public static final String RESULT_UUID_RUNNING_STRING = "99999999-1111-0000-0000-000000000000";
    public static final UUID RESULT_UUID_RUNNING = UUID.fromString(RESULT_UUID_RUNNING_STRING);

    @MockBean
    private DynamicMappingClient dynamicMappingClient;

    @MockBean
    private DynamicSimulationClient dynamicSimulationClient;

    @MockBean
    private TimeSeriesClient timeSeriesClient;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    DynamicSimulationService dynamicSimulationService;

    @Before
    public void setup() {
        // setup networkModificationTreeService mock in all normal cases
        given(networkModificationTreeService.getDynamicSimulationResultUuid(NODE_UUID)).willReturn(Optional.of(RESULT_UUID));
    }

    @Test
    public void testRunDynamicSimulation() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.run("", NETWORK_UUID, VARIANT_1_ID, START_TIME, STOP_TIME, MAPPING_NAME_01)).willReturn(RESULT_UUID);

        // call method to be tested
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation("", NETWORK_UUID, VARIANT_1_ID, START_TIME, STOP_TIME, MAPPING_NAME_01);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
    }

    @Test
    public void testGetTimeSeriesResult() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));
        given(timeSeriesClient.getTimeSeriesGroup(TIME_SERIES_UUID)).willReturn(timeSeries);

        // call method to be tested
        List<TimeSeries> timeSeriesResult = dynamicSimulationService.getTimeSeriesResult(NODE_UUID);

        // check result
        // must contain two elements
        assertEquals(2, timeSeriesResult.size());
    }

    @Test
    public void testGetTimeLineResult() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeLineResult(RESULT_UUID)).willReturn(TIME_LINE_UUID);

        // setup timeSeriesClient mock
        // timeline
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeLine = TimeSeries.createString("TimeLine", index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");
        given(timeSeriesClient.getTimeSeriesGroup(TIME_LINE_UUID)).willReturn(Arrays.asList(timeLine));

        // call method to be tested
        List<TimeSeries> timeLineResult = dynamicSimulationService.getTimeLineResult(NODE_UUID);

        // check result
        // must contain only one
        assertEquals(1, timeLineResult.size());
    }

    @Test
    public void testGetStatus() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getStatus(RESULT_UUID)).willReturn(DynamicSimulationStatus.CONVERGED);

        // call method to be tested
        DynamicSimulationStatus status = dynamicSimulationService.getStatus(NODE_UUID);

        // check result
        // status must be "CONVERGED"
        assertEquals(DynamicSimulationStatus.CONVERGED, status);
    }

    @Test
    public void testDeleteResult() {
        dynamicSimulationService.deleteResult(RESULT_UUID);
        assertTrue(true);
    }

    @Test
    public void testAssertDynamicSimulationNotRunning() {

        // test not running
        dynamicSimulationService.assertDynamicSimulationNotRunning(NODE_UUID);
        assertTrue(true);
    }

    @Test(expected = StudyException.class)
    public void testAssertDynamicSimulationRunning() {
        // setup for running node
        given(dynamicSimulationClient.getStatus(RESULT_UUID_RUNNING)).willReturn(DynamicSimulationStatus.RUNNING);
        given(networkModificationTreeService.getDynamicSimulationResultUuid(NODE_UUID_RUNNING)).willReturn(Optional.of(RESULT_UUID_RUNNING));

        // test running
        dynamicSimulationService.assertDynamicSimulationNotRunning(NODE_UUID_RUNNING);
    }

    @Test
    public void testGetMappings() {
        // setup DynamicSimulationClient mock
        given(dynamicMappingClient.getAllMappings()).willReturn(MAPPINGS);

        // call method to be tested
        List<MappingInfos> mappingInfos = dynamicSimulationService.getMappings(NODE_UUID);

        // check result
        // must return 2 mappings
        assertEquals(MAPPINGS.size(), mappingInfos.size());
    }
}
