/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

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
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.DYNAMIC_SIMULATION;
import static org.gridsuite.study.server.notification.NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceDynamicSimulationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServiceDynamicSimulationTest.class);

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = List.of(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final String VARIANT_1_ID = "variant_1";

    private static final double START_TIME = 0.0;

    private static final double STOP_TIME = 500.0;

    private static final UUID STUDY_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOTNETWORK_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private DynamicSimulationService dynamicSimulationService;

    @Autowired
    private StudyService studyService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @MockBean
    private RootNetworkService rootNetworkService;

    @BeforeEach
    void setup() {
        // setup NetworkService mock
        given(rootNetworkService.getNetworkUuid(ROOTNETWORK_UUID)).willReturn(NETWORK_UUID);

        // setup NetworkModificationTreeService mock
        // suppose always having an existing result in a previous run
        given(rootNetworkNodeInfoService.getComputationResultUuid(any(UUID.class), any(UUID.class), eq(DYNAMIC_SIMULATION))).willReturn(RESULT_UUID);
        given(networkModificationTreeService.getVariantId(any(UUID.class), any(UUID.class))).willReturn(VARIANT_1_ID);
        willDoNothing().given(rootNetworkNodeInfoService).updateComputationResultUuid(NODE_UUID, ROOTNETWORK_UUID, RESULT_UUID, DYNAMIC_SIMULATION);

        // setup NotificationService mock
        willDoNothing().given(notificationService).emitStudyChanged(STUDY_UUID, NODE_UUID, ROOTNETWORK_UUID, UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Test
    void testRunDynamicSimulation() {
        // setup DynamicSimulationService mock
        given(dynamicSimulationService.runDynamicSimulation(any(), eq(NODE_UUID), eq(ROOTNETWORK_UUID), any(), any(), any(), any(), any(), isNull())).willReturn(RESULT_UUID);
        willDoNothing().given(dynamicSimulationService).deleteResults(anyList());
        given(rootNetworkNodeInfoService.getLoadFlowStatus(NODE_UUID, ROOTNETWORK_UUID)).willReturn(LoadFlowStatus.CONVERGED.name());

        // init parameters
        DynamicSimulationParametersInfos parameters = new DynamicSimulationParametersInfos();
        parameters.setStartTime(START_TIME);
        parameters.setStopTime(STOP_TIME);
        parameters.setMapping(MAPPING_NAME_01);

        // call method to be tested
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, UUID.randomUUID(), "caseName", "", UUID.randomUUID());
        studyRepository.save(studyEntity);
        UUID resultUuid = studyService.runDynamicSimulation(studyEntity.getId(), NODE_UUID, ROOTNETWORK_UUID, parameters, "testUserId", null);

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);
    }

    @Test
    void testGetDynamicSimulationTimeSeries() throws Exception {
        // setup
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<DoubleTimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        given(dynamicSimulationService.getTimeSeriesResult(RESULT_UUID, null)).willReturn(timeSeries);

        // call method to be tested
        String timeSeriesResultJson = TimeSeries.toJson(rootNetworkNodeInfoService.getDynamicSimulationTimeSeries(NODE_UUID, ROOTNETWORK_UUID, null));

        // --- check result --- //
        String timeSeriesExpectedJson = TimeSeries.toJson(timeSeries);
        LOGGER.info("Time series expected in Json = {}", timeSeriesExpectedJson);
        LOGGER.info("Time series result in Json = {}", timeSeriesResultJson);
        assertThat(objectMapper.readTree(timeSeriesResultJson)).isEqualTo(objectMapper.readTree(timeSeriesExpectedJson));
    }

    @Test
    void testGetDynamicSimulationTimeline() {
        // setup
        // timeline
        List<TimelineEventInfos> timelineEventInfosList = List.of(
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(102479, "_BUS____2-BUS____5-1_AC", "LINE : opening both sides"),
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(104396, "CLA_2_4", "CLA : arming by over-current constraint")
        );

        given(dynamicSimulationService.getTimelineResult(RESULT_UUID)).willReturn(timelineEventInfosList);

        // call method to be tested
        List<TimelineEventInfos> timelineEventInfosListResult = rootNetworkNodeInfoService.getDynamicSimulationTimeline(NODE_UUID, ROOTNETWORK_UUID);

        // --- check result --- //
        // must contain 4 timeline events
        assertThat(timelineEventInfosListResult).hasSize(4);
    }

    @Test
    void testGetDynamicSimulationStatus() {
        // setup
        given(dynamicSimulationService.getStatus(RESULT_UUID)).willReturn(DynamicSimulationStatus.CONVERGED);

        // call method to be tested
        DynamicSimulationStatus status = rootNetworkNodeInfoService.getDynamicSimulationStatus(NODE_UUID, ROOTNETWORK_UUID);

        // --- check result --- //
        LOGGER.info("Status expected = {}", DynamicSimulationStatus.CONVERGED.name());
        LOGGER.info("Status result = {}", status);
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test
    void testGetDynamicSimulationMappings() throws Exception {
        // setup
        given(dynamicSimulationService.getMappings(STUDY_UUID)).willReturn(MAPPINGS);

        // call method to be tested
        List<MappingInfos> mappingInfos = studyService.getDynamicSimulationMappings(STUDY_UUID);

        // --- check result --- //
        // must return 2 mappings
        LOGGER.info("Mapping infos expected in Json = {}", objectMapper.writeValueAsString(MAPPINGS));
        LOGGER.info("Mapping infos result in Json = {}", objectMapper.writeValueAsString(mappingInfos));
        assertThat(mappingInfos).hasSameSizeAs(MAPPINGS);
    }
}
