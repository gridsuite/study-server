/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicsimulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.*;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesMetadataRest;
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
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class DynamicSimulationServiceTest {

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = Arrays.asList(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final String VARIANT_1_ID = "variant_1";

    private static final String STUDY_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID STUDY_UUID = UUID.fromString(STUDY_UUID_STRING);

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

    public static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    public static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";
    public static final String TIME_LINE_NAME = "TimeLine";

    @MockBean
    private DynamicMappingClient dynamicMappingClient;

    @MockBean
    private DynamicSimulationClient dynamicSimulationClient;

    @MockBean
    private TimeSeriesClient timeSeriesClient;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private ObjectMapper objectMapper;

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
        given(dynamicSimulationClient.run(eq(""), eq(""), eq(NETWORK_UUID), eq(VARIANT_1_ID), any())).willReturn(RESULT_UUID);

        // call method to be tested
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation("", "", NETWORK_UUID, VARIANT_1_ID, null);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
    }

    @Test
    public void testGetTimeSeriesMetadataList() throws JsonProcessingException {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // timeseries metadata
        TimeSeriesGroupRest timeSeriesGroupMetadata = new TimeSeriesGroupRest();
        timeSeriesGroupMetadata.setId(TIME_SERIES_UUID);
        timeSeriesGroupMetadata.setMetadatas(List.of(new TimeSeriesMetadataRest(TIME_SERIES_NAME_1), new TimeSeriesMetadataRest(TIME_SERIES_NAME_2)));

        given(timeSeriesClient.getTimeSeriesGroupMetadata(TIME_SERIES_UUID)).willReturn(timeSeriesGroupMetadata);

        // call method to be tested
        List<TimeSeriesMetadataInfos> resultTimeSeriesMetadataList = dynamicSimulationService.getTimeSeriesMetadataList(NODE_UUID);

        // check result
        // metadata must be identical to expected
        List<TimeSeriesMetadataInfos> expectedTimeSeriesMetadataList = timeSeriesGroupMetadata.getMetadatas().stream().map(TimeSeriesMetadataInfos::fromRest).collect(Collectors.toUnmodifiableList());
        String expectedTimeSeriesMetadataListJson = objectMapper.writeValueAsString(expectedTimeSeriesMetadataList);
        String resultTimeSeriesMetadataListJson = objectMapper.writeValueAsString(resultTimeSeriesMetadataList);
        assertEquals(objectMapper.readTree(expectedTimeSeriesMetadataListJson), objectMapper.readTree(resultTimeSeriesMetadataListJson));
    }

    @Test
    public void testGetTimeSeriesResult() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble(TIME_SERIES_NAME_1, index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble(TIME_SERIES_NAME_2, index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));
        given(timeSeriesClient.getTimeSeriesGroup(TIME_SERIES_UUID, null)).willReturn(timeSeries);

        // call method to be tested
        List<DoubleTimeSeries> timeSeriesResult = dynamicSimulationService.getTimeSeriesResult(NODE_UUID, null);

        // check result
        // must contain two elements
        assertEquals(2, timeSeriesResult.size());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeSeriesResultGivenBadType() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // create a bad type timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        List<TimeSeries> timeSeries = List.of(TimeSeries.createString(TIME_LINE_NAME, index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint"));
        given(timeSeriesClient.getTimeSeriesGroup(TIME_SERIES_UUID, null)).willReturn(timeSeries);

        // call method to be tested
        dynamicSimulationService.getTimeSeriesResult(NODE_UUID, null);
    }

    @Test
    public void testGetTimeLineResult() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeLineResult(RESULT_UUID)).willReturn(TIME_LINE_UUID);

        // setup timeSeriesClient mock
        // timeline
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeLine = TimeSeries.createString(TIME_LINE_NAME, index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");
        given(timeSeriesClient.getTimeSeriesGroup(TIME_LINE_UUID, null)).willReturn(Arrays.asList(timeLine));

        // call method to be tested
        List<StringTimeSeries> timeLineResult = dynamicSimulationService.getTimeLineResult(NODE_UUID);

        // check result
        // must contain only one
        assertEquals(1, timeLineResult.size());
    }

    @Test(expected = StudyException.class)
    public void testGetTimeLineResultGivenBadType() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeLineResult(RESULT_UUID)).willReturn(TIME_LINE_UUID);

        // setup timeSeriesClient mock
        // create a bad type timeline
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        List<TimeSeries> timeLines = List.of(TimeSeries.createDouble(TIME_SERIES_NAME_1, index, 333.847331, 333.847321, 333.847300, 333.847259));

        given(timeSeriesClient.getTimeSeriesGroup(TIME_LINE_UUID, null)).willReturn(timeLines);

        // call method to be tested
        dynamicSimulationService.getTimeLineResult(NODE_UUID);
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
    public void testInvalidateStatus() {
        dynamicSimulationService.invalidateStatus(List.of(RESULT_UUID));
        assertTrue(true);
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
        List<MappingInfos> mappingInfos = dynamicSimulationService.getMappings(STUDY_UUID);

        // check result
        // must return 2 mappings
        assertEquals(MAPPINGS.size(), mappingInfos.size());
    }
}
