/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsimulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.commons.PowsyblException;
import com.powsybl.timeseries.*;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelVariableDefinitionInfos;
import org.gridsuite.study.server.dto.dynamicmapping.VariablesSetInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesGroupRest;
import org.gridsuite.study.server.dto.timeseries.rest.TimeSeriesMetadataRest;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.gridsuite.study.server.service.client.timeseries.TimeSeriesClient;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class DynamicSimulationServiceTest {

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = Arrays.asList(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final List<ModelInfos> MODELS = List.of(
            // take from resources/data/loadAlphaBeta.json
            new ModelInfos("LoadAlphaBeta", "LOAD", List.of(
                    new ModelVariableDefinitionInfos("load_PPu", "MW"),
                    new ModelVariableDefinitionInfos("load_QPu", "MW")
            ), null),
            // take from resources/data/generatorSynchronousThreeWindingsProportionalRegulations.json
            new ModelInfos("GeneratorSynchronousThreeWindingsProportionalRegulations", "GENERATOR", null, List.of(
                    new VariablesSetInfos("Generator", List.of(
                            new ModelVariableDefinitionInfos("generator_omegaPu", "pu"),
                            new ModelVariableDefinitionInfos("generator_PGen", "MW")
                    )),
                    new VariablesSetInfos("VoltageRegulator", List.of(
                            new ModelVariableDefinitionInfos("voltageRegulator_EfdPu", "pu")
                    ))
            ))
    );

    private static final String VARIANT_1_ID = "variant_1";

    private static final UUID STUDY_UUID = UUID.randomUUID();

    // converged node
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOTNETWORK_UUID = UUID.randomUUID();
    public static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID TIME_SERIES_UUID = UUID.randomUUID();
    private static final UUID TIMELINE_UUID = UUID.randomUUID();

    // running node
    private static final UUID NODE_UUID_RUNNING = UUID.randomUUID();
    private static final UUID RESULT_UUID_RUNNING = UUID.randomUUID();

    private static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    private static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";
    private static final String TIMELINE_NAME = "Timeline";

    private static final UUID REPORT_UUID = UUID.randomUUID();

    @MockitoBean
    private DynamicMappingClient dynamicMappingClient;

    @MockitoBean
    private DynamicSimulationClient dynamicSimulationClient;

    @MockitoBean
    private TimeSeriesClient timeSeriesClient;

    @MockitoBean
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DynamicSimulationService dynamicSimulationService;

    @MockitoBean
    private RootNetworkService rootNetworkService;

    @MockitoBean
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;

    @BeforeEach
    void setup() {
        // setup networkModificationTreeService mock in all normal cases
        given(rootNetworkNodeInfoService.getComputationResultUuid(NODE_UUID, ROOTNETWORK_UUID, ComputationType.DYNAMIC_SIMULATION)).willReturn(RESULT_UUID);
    }

    @Test
    void testRunDynamicSimulation() {
        given(rootNetworkService.getNetworkUuid(ROOTNETWORK_UUID)).willReturn(NETWORK_UUID);
        given(networkModificationTreeService.getVariantId(NODE_UUID, ROOTNETWORK_UUID)).willReturn(VARIANT_1_ID);
        given(networkModificationTreeService.getReportUuid(NODE_UUID, ROOTNETWORK_UUID)).willReturn(Optional.of(REPORT_UUID));

        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.run(eq(""), any(), eq(NETWORK_UUID), eq(VARIANT_1_ID), eq(new ReportInfos(REPORT_UUID, NODE_UUID)), any(), any(), eq(false))).willReturn(RESULT_UUID);

        // call method to be tested
        UUID resultUuid = dynamicSimulationService.runDynamicSimulation("", NODE_UUID, ROOTNETWORK_UUID, NETWORK_UUID, VARIANT_1_ID, REPORT_UUID, null, "testUserId", false);

        // check result
        assertThat(resultUuid).isEqualTo(RESULT_UUID);
    }

    @Test
    void testGetTimeSeriesMetadataList() throws Exception {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // timeseries metadata
        TimeSeriesGroupRest timeSeriesGroupMetadata = new TimeSeriesGroupRest();
        timeSeriesGroupMetadata.setId(TIME_SERIES_UUID);
        timeSeriesGroupMetadata.setMetadatas(List.of(new TimeSeriesMetadataRest(TIME_SERIES_NAME_1), new TimeSeriesMetadataRest(TIME_SERIES_NAME_2)));

        given(timeSeriesClient.getTimeSeriesGroupMetadata(TIME_SERIES_UUID)).willReturn(timeSeriesGroupMetadata);

        // call method to be tested
        List<TimeSeriesMetadataInfos> resultTimeSeriesMetadataList = dynamicSimulationService.getTimeSeriesMetadataList(RESULT_UUID);

        // check result
        // metadata must be identical to expected
        List<TimeSeriesMetadataInfos> expectedTimeSeriesMetadataList = timeSeriesGroupMetadata.getMetadatas().stream().map(TimeSeriesMetadataInfos::fromRest).toList();
        String expectedTimeSeriesMetadataListJson = objectMapper.writeValueAsString(expectedTimeSeriesMetadataList);
        String resultTimeSeriesMetadataListJson = objectMapper.writeValueAsString(resultTimeSeriesMetadataList);
        assertThat(objectMapper.readTree(resultTimeSeriesMetadataListJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesMetadataListJson));
    }

    @Test
    void testGetTimeSeriesResult() {
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
        List<DoubleTimeSeries> timeSeriesResult = dynamicSimulationService.getTimeSeriesResult(RESULT_UUID, null);

        // check result
        // must contain two elements
        assertThat(timeSeriesResult).hasSize(2);
    }

    @Test
    void testGetTimeSeriesResultGivenBadType() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimeSeriesResult(RESULT_UUID)).willReturn(TIME_SERIES_UUID);

        // setup timeSeriesClient mock
        // create a bad type timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        List<TimeSeries> timeSeries = List.of(TimeSeries.createString(TIMELINE_NAME, index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint"));
        given(timeSeriesClient.getTimeSeriesGroup(TIME_SERIES_UUID, null)).willReturn(timeSeries);

        // call method to be tested
        assertThrows(StudyException.class, () -> dynamicSimulationService.getTimeSeriesResult(RESULT_UUID, null));
    }

    @Test
    void testGetTimelineResult() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimelineResult(RESULT_UUID)).willReturn(TIMELINE_UUID);

        // setup timeSeriesClient mock
        // timeline
        List<TimelineEventInfos> timelineEventInfosList = List.of(
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(102479, "_BUS____2-BUS____5-1_AC", "LINE : opening both sides"),
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(104396, "CLA_2_4", "CLA : arming by over-current constraint")
        );

        // convert timeline event list to StringTimeSeries
        long[] timelineIndexes = timelineEventInfosList.stream().mapToLong(event -> (long) event.time()).toArray();
        String[] timelineValues = timelineEventInfosList.stream().map(event -> {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new PowsyblException("Error while serializing timeline event: " + event.toString(), e);
            }
        }).toArray(String[]::new);
        List<TimeSeries> timelineSeries = List.of(TimeSeries.createString("timeline", new IrregularTimeSeriesIndex(timelineIndexes), timelineValues));

        given(timeSeriesClient.getTimeSeriesGroup(TIMELINE_UUID, null)).willReturn(timelineSeries);

        // call method to be tested
        List<TimelineEventInfos> timelineResult = dynamicSimulationService.getTimelineResult(RESULT_UUID);

        // check result
        // must contain 4 timeline events
        assertThat(timelineResult).hasSize(4);
    }

    @Test
    void testGetTimelineResultGivenBadType() throws Exception {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getTimelineResult(RESULT_UUID)).willReturn(TIMELINE_UUID);

        // setup timeSeriesClient mock
        // --- create a bad type series --- //
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        List<TimeSeries> timelines = List.of(TimeSeries.createDouble(TIME_SERIES_NAME_1, index, 333.847331, 333.847321, 333.847300, 333.847259));

        given(timeSeriesClient.getTimeSeriesGroup(TIMELINE_UUID, null)).willReturn(timelines);

        // call method to be tested
        assertThatExceptionOfType(StudyException.class).isThrownBy(() ->
            dynamicSimulationService.getTimelineResult(RESULT_UUID)
        ).withMessage("Timelines can not be a type: %s, expected type: %s",
                        timelines.get(0).getClass().getSimpleName(),
                        StringTimeSeries.class.getSimpleName());

        // --- create bad type timeline events --- //
        List<String> timelineEventInfosList = List.of(
                "CLA : order to change topology",
                "LINE : opening both sides",
                "CLA : order to change topology",
                "CLA : arming by over-current constraint"
        );

        // collect and convert timeline event list to StringTimeSeries
        long[] timelineIndexes = LongStream.range(0, timelineEventInfosList.size()).toArray();
        String[] timelineValues = timelineEventInfosList.stream().map(event -> {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException e) {
                throw new PowsyblException("Error while serializing timeline event: " + event, e);
            }
        }).toArray(String[]::new);
        timelines = List.of(TimeSeries.createString("timeline", new IrregularTimeSeriesIndex(timelineIndexes), timelineValues));

        given(timeSeriesClient.getTimeSeriesGroup(TIMELINE_UUID, null)).willReturn(timelines);

        // call method to be tested
        assertThatExceptionOfType(StudyException.class).isThrownBy(() ->
            dynamicSimulationService.getTimelineResult(RESULT_UUID)
        ).withMessage("Error while deserializing timeline event: %s", objectMapper.writeValueAsString(timelineEventInfosList.get(0)));
    }

    @Test
    void testGetStatus() {
        // setup DynamicSimulationClient mock
        given(dynamicSimulationClient.getStatus(RESULT_UUID)).willReturn(DynamicSimulationStatus.CONVERGED);

        // call method to be tested
        DynamicSimulationStatus status = dynamicSimulationService.getStatus(RESULT_UUID);

        // check result
        // status must be "CONVERGED"
        assertThat(status).isEqualTo(DynamicSimulationStatus.CONVERGED);
    }

    @Test
    void testInvalidateStatus() {
        assertDoesNotThrow(() -> dynamicSimulationService.invalidateStatus(List.of(RESULT_UUID)));
    }

    @Test
    void testDeleteResult() {
        assertDoesNotThrow(() -> dynamicSimulationService.deleteResults(List.of(RESULT_UUID)));
    }

    @Test
    void testAssertDynamicSimulationNotRunning() {

        // test not running
        assertDoesNotThrow(() -> dynamicSimulationService.assertDynamicSimulationNotRunning(RESULT_UUID));
    }

    @Test
    void testAssertDynamicSimulationRunning() {
        // setup for running node
        given(dynamicSimulationClient.getStatus(RESULT_UUID_RUNNING)).willReturn(DynamicSimulationStatus.RUNNING);
        given(rootNetworkNodeInfoService.getComputationResultUuid(NODE_UUID_RUNNING, ROOTNETWORK_UUID, ComputationType.DYNAMIC_SIMULATION)).willReturn(RESULT_UUID_RUNNING);

        // test running
        assertThrows(StudyException.class, () -> dynamicSimulationService.assertDynamicSimulationNotRunning(RESULT_UUID_RUNNING));
    }

    @Test
    void testGetMappings() {
        // setup DynamicSimulationClient mock
        given(dynamicMappingClient.getAllMappings()).willReturn(MAPPINGS);

        // call method to be tested
        List<MappingInfos> mappingInfos = dynamicSimulationService.getMappings(STUDY_UUID);

        // check result
        // must return 2 mappings
        assertThat(mappingInfos).hasSameSizeAs(MAPPINGS);
    }

    @Test
    void testGetModels() {
        // setup DynamicSimulationClient mock
        given(dynamicMappingClient.getModels(MAPPING_NAMES[0])).willReturn(MODELS);

        // call method to be tested
        List<ModelInfos> modelInfosList = dynamicSimulationService.getModels(MAPPING_NAMES[0]);

        // check result
        // must return 2 models
        assertThat(modelInfosList).hasSameSizeAs(MODELS);
    }
}
