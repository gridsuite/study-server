/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.IrregularTimeSeriesIndex;
import com.powsybl.timeseries.TimeSeries;
import com.powsybl.timeseries.TimeSeriesIndex;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class StudyServiceDynamicSimulationTest {

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = List.of(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final String VARIANT_1_ID = "variant_1";

    private static final double START_TIME = 0.0;

    private static final double STOP_TIME = 500.0;

    private static final String STUDY_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID STUDY_UUID = UUID.fromString(STUDY_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);

    private static final String NODE_UUID_STRING = "22222222-0000-0000-0000-000000000000";
    private static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

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

    @MockBean
    LoadFlowService loadFlowService;

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
        given(networkModificationTreeService.getComputationResultUuid(any(UUID.class), eq(DYNAMIC_SIMULATION))).willReturn(Optional.of(RESULT_UUID));
        given(networkModificationTreeService.getVariantId(any(UUID.class))).willReturn(VARIANT_1_ID);
        willDoNothing().given(networkModificationTreeService).updateComputationResultUuid(NODE_UUID, RESULT_UUID, DYNAMIC_SIMULATION);

        // setup NotificationService mock
        willDoNothing().given(notificationService).emitStudyChanged(STUDY_UUID, NODE_UUID, UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Test
    public void testRunDynamicSimulation() {
        // setup DynamicSimulationService mock
        given(dynamicSimulationService.runDynamicSimulation(eq(""), anyString(), eq(NETWORK_UUID), anyString(), any(), any())).willReturn(RESULT_UUID);
        willDoNothing().given(dynamicSimulationService).deleteResult(any(UUID.class));
        given(loadFlowService.getLoadFlowStatus(NODE_UUID)).willReturn(LoadFlowStatus.CONVERGED.name());

        // init parameters
        DynamicSimulationParametersInfos parameters = new DynamicSimulationParametersInfos();
        parameters.setStartTime(START_TIME);
        parameters.setStopTime(STOP_TIME);
        parameters.setMapping(MAPPING_NAME_01);

        // call method to be tested
        UUID resultUuid = studyService.runDynamicSimulation(STUDY_UUID, NODE_UUID, parameters, "testUserId");

        // check result
        assertThat(resultUuid).hasToString(RESULT_UUID_STRING);
    }

    @Test
    public void testGetDynamicSimulationTimeSeries() throws JsonProcessingException {
        // setup
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<DoubleTimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        given(dynamicSimulationService.getTimeSeriesResult(NODE_UUID, null)).willReturn(timeSeries);

        // call method to be tested
        String timeSeriesResultJson = TimeSeries.toJson(studyService.getDynamicSimulationTimeSeries(NODE_UUID, null));

        // --- check result --- //
        String timeSeriesExpectedJson = TimeSeries.toJson(timeSeries);
        getLogger().info("Time series expected in Json = " + timeSeriesExpectedJson);
        getLogger().info("Time series result in Json = " + timeSeriesResultJson);
        assertThat(objectMapper.readTree(timeSeriesResultJson)).isEqualTo(objectMapper.readTree(timeSeriesExpectedJson));
    }

    @Test
    public void testGetDynamicSimulationTimeLine() {
        // setup
        // timeline
        List<TimelineEventInfos> timelineEventInfosList = List.of(
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(102479, "_BUS____2-BUS____5-1_AC", "LINE : opening both sides"),
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(104396, "CLA_2_4", "CLA : arming by over-current constraint")
        );

        given(dynamicSimulationService.getTimelineResult(NODE_UUID)).willReturn(timelineEventInfosList);

        // call method to be tested
        List<TimelineEventInfos> timelineEventInfosListResult = studyService.getDynamicSimulationTimeline(NODE_UUID);

        // --- check result --- //
        // must contain 4 timeline events
        assertThat(timelineEventInfosListResult).hasSize(4);
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
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test
    public void testGetDynamicSimulationMappings() throws JsonProcessingException {
        // setup
        given(dynamicSimulationService.getMappings(STUDY_UUID)).willReturn(MAPPINGS);

        // call method to be tested
        List<MappingInfos> mappingInfos = studyService.getDynamicSimulationMappings(STUDY_UUID);

        // --- check result --- //
        // must return 2 mappings
        getLogger().info("Mapping infos expected in Json = " + objectMapper.writeValueAsString(MAPPINGS));
        getLogger().info("Mapping infos result in Json = " + objectMapper.writeValueAsString(mappingInfos));
        assertThat(mappingInfos).hasSameSizeAs(MAPPINGS);
    }
}
