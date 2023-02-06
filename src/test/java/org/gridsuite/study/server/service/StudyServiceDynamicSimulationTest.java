/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.*;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class StudyServiceDynamicSimulationTest {

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = List.of(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final String VARIANT_1_ID = "variant_1";

    private static final int START_TIME = 0;

    private static final int STOP_TIME = 500;

    private static final String STUDY_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID STUDY_UUID = UUID.fromString(STUDY_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);

    private static final String NODE_UUID_STRING = "22222222-0000-0000-0000-000000000000";
    private static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

    private static final String PARAMETERS = String.format("{\"startTime\": %d, \"stopTime\": %d}", START_TIME, STOP_TIME);

    private static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    private static final UUID RESULT_UUID = UUID.fromString(RESULT_UUID_STRING);

    @MockBean
    NetworkService networkService;

    @MockBean
    NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    NotificationService notificationService;

    @MockBean
    DynamicSimulationService dynamicSimulationService;

    @Autowired
    StudyService studyService;

    @Autowired
    ObjectMapper objectMapper;

    public final Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    @Before
    public void setup() {
        // setup NetworkService mock
        given(networkService.getNetworkUuid(STUDY_UUID)).willReturn(NETWORK_UUID);

        // setup NetworkModificationTreeService mock
        // suppose always having an existing result in a previous run
        given(networkModificationTreeService.getDynamicSimulationResultUuid(any(UUID.class))).willReturn(Optional.of(RESULT_UUID));
        given(networkModificationTreeService.getVariantId(any(UUID.class))).willReturn(VARIANT_1_ID);
        willDoNothing().given(networkModificationTreeService).updateDynamicSimulationResultUuid(NODE_UUID, RESULT_UUID);

        // setup NotificationService mock
        willDoNothing().given(notificationService).emitStudyChanged(STUDY_UUID, NODE_UUID, UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Test
    public void testRunDynamicSimulation() {
        // setup DynamicSimulationService mock
        given(dynamicSimulationService.runDynamicSimulation(anyString(), eq(NETWORK_UUID), anyString(), eq(START_TIME), eq(STOP_TIME), eq(MAPPING_NAME_01))).willReturn(RESULT_UUID);
        willDoNothing().given(dynamicSimulationService).deleteResult(any(UUID.class));
        given(networkModificationTreeService.getLoadFlowStatus(NODE_UUID)).willReturn(Optional.of(LoadFlowStatus.CONVERGED));
        // init parameters
        DynamicSimulationParametersInfos parameters = new DynamicSimulationParametersInfos();
        parameters.setStartTime(START_TIME);
        parameters.setStopTime(STOP_TIME);

        // call method to be tested
        UUID resultUuid = studyService.runDynamicSimulation(STUDY_UUID, NODE_UUID, parameters, MAPPING_NAME_01);

        // check result
        assertEquals(RESULT_UUID_STRING, resultUuid.toString());
    }

    @Test
    public void testGetDynamicSimulationTimeSeries() throws JsonProcessingException {
        // setup
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<StoredDoubleTimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        given(dynamicSimulationService.getTimeSeriesResult(NODE_UUID)).willReturn(timeSeries);

        // call method to be tested
        String timeSeriesResultJson = TimeSeries.toJson(studyService.getDynamicSimulationTimeSeries(NODE_UUID));

        // --- check result --- //
        String timeSeriesExpectedJson = TimeSeries.toJson(timeSeries);
        getLogger().info("Time series expected in Json = " + timeSeriesExpectedJson);
        getLogger().info("Time series result in Json = " + timeSeriesResultJson);
        assertEquals(objectMapper.readTree(timeSeriesExpectedJson), objectMapper.readTree(timeSeriesResultJson));
    }

    @Test
    public void testGetDynamicSimulationTimeLine() throws JsonProcessingException {
        // setup
        // timeline
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeLine = TimeSeries.createString("TimeLine", index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");

        given(dynamicSimulationService.getTimeLineResult(NODE_UUID)).willReturn(Arrays.asList(timeLine));

        // call method to be tested
        String timeLineResultJson = TimeSeries.toJson(studyService.getDynamicSimulationTimeLine(NODE_UUID));

        // --- check result --- //
        String timeLineExpectedJson = TimeSeries.toJson(Arrays.asList(timeLine));
        getLogger().info("Time line expected in Json = " + timeLineExpectedJson);
        getLogger().info("Time line result in Json = " + timeLineResultJson);
        assertEquals(objectMapper.readTree(timeLineExpectedJson), objectMapper.readTree(timeLineResultJson));
    }

    @Test
    public void testGetDynamicSimulationStatus() {
        // setup
        given(dynamicSimulationService.getStatus(NODE_UUID)).willReturn(DynamicSimulationStatus.CONVERGED);

        // call method to be tested
        DynamicSimulationStatus status = studyService.getDynamicSimulationStatus(NODE_UUID);

        // --- check result --- //
        getLogger().info("Status expected = " + DynamicSimulationStatus.CONVERGED.name());
        getLogger().info("Status result = " + status);
        assertEquals(DynamicSimulationStatus.CONVERGED, status);
    }

    @Test
    public void testGetDynamicSimulationMappings() throws JsonProcessingException {
        // setup
        given(dynamicSimulationService.getMappings(any(UUID.class))).willReturn(MAPPINGS);

        // call method to be tested
        List<MappingInfos> mappingInfos = studyService.getDynamicSimulationMappings(NODE_UUID);

        // --- check result --- //
        // must return 2 mappings
        getLogger().info("Mapping infos expected in Json = " + objectMapper.writeValueAsString(MAPPINGS));
        getLogger().info("Mapping infos result in Json = " + objectMapper.writeValueAsString(mappingInfos));
        assertEquals(MAPPINGS.size(), mappingInfos.size());
    }
}
