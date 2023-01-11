/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.IrregularTimeSeriesIndex;
import com.powsybl.timeseries.StringTimeSeries;
import com.powsybl.timeseries.TimeSeries;
import com.powsybl.timeseries.TimeSeriesIndex;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.NOT_ALLOWED;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class StudyControllerDynamicSimulationTest {

    private static final String API_VERSION = StudyApi.API_VERSION;
    private static final String DELIMITER = "/";
    private static final String STUDY_END_POINT = "studies";

    private static final String STUDY_BASE_URL = UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT);
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_RUN = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/run";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/status";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_MAPPINGS = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/mappings";

    private static final String HEADER_USER_ID_NAME = "userId";
    private static final String HEADER_USER_ID_VALUE = "userId";

    private static final String MAPPING_NAME_01 = "_01";
    private static final String MAPPING_NAME_02 = "_02";

    // all mappings
    private static final String[] MAPPING_NAMES = {MAPPING_NAME_01, MAPPING_NAME_02};

    private static final List<MappingInfos> MAPPINGS = Arrays.asList(new MappingInfos(MAPPING_NAMES[0]),
            new MappingInfos(MAPPING_NAMES[1]));

    private static final int START_TIME = 0;

    private static final int STOP_TIME = 500;

    private static final String STUDY_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID STUDY_UUID = UUID.fromString(STUDY_UUID_STRING);

    private static final String ROOT_NODE_UUID_STRING = "22222222-0000-0000-0000-000000000000";
    private static final UUID ROOT_NODE_UUID = UUID.fromString(ROOT_NODE_UUID_STRING);

    private static final String NODE_UUID_STRING = "22222222-1111-0000-0000-000000000000";
    public static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

    private static final String NODE_NOT_DONE_UUID_STRING = "22222222-2222-0000-0000-000000000000";
    private static final UUID NODE_NOT_DONE_UUID = UUID.fromString(NODE_NOT_DONE_UUID_STRING);

    private static final String NODE_NOT_RUN_UUID_STRING = "22222222-3333-0000-0000-000000000000";
    private static final UUID NODE_NOT_RUN_UUID = UUID.fromString(NODE_NOT_RUN_UUID_STRING);

    private static final String PARAMETERS = String.format("{\"startTime\": %d, \"stopTime\": %d}", START_TIME, STOP_TIME);

    private static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    private static final UUID RESULT_UUID = UUID.fromString(RESULT_UUID_STRING);

    @Autowired
    private MockMvc studyClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    StudyService studyService;

    private Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    @Before
    public void setup() {
        // setup StudyService mock
        // setup root node as read only
        willThrow(new StudyException(NOT_ALLOWED)).given(studyService).assertIsNodeNotReadOnly(ROOT_NODE_UUID);
    }

    @Test
    public void testRunDynamicSimulationGivenRootNode() throws Exception {
        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT) + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                STUDY_UUID, ROOT_NODE_UUID, MAPPING_NAME_01)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testRunDynamicSimulationGivenRegularNode() throws Exception {
        // setup StudyService mock
        given(studyService.runDynamicSimulation(STUDY_UUID, NODE_UUID, PARAMETERS, MAPPING_NAME_01)).willReturn(RESULT_UUID);

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        result = studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                        STUDY_UUID, NODE_UUID, MAPPING_NAME_01)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        String resultUuidJson = result.getResponse().getContentAsString();
        UUID resultUuid = objectMapper.readValue(resultUuidJson, UUID.class);

        assertEquals(RESULT_UUID, resultUuid);
    }

    @Test
    public void testGetDynamicSimulationTimeSeriesResultGivenNodeNotDone() throws Exception {
        // setup StudyService mock
        given(studyService.getDynamicSimulationTimeSeries(NODE_NOT_DONE_UUID)).willReturn(null);

        // --- call endpoint to be tested --- //
        // get result from a node not yet done
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeseries",
                STUDY_UUID, NODE_NOT_DONE_UUID)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationTimeSeriesResult() throws Exception {
        // timeseries
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{32, 64, 128, 256});
        List<TimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));
        // setup StudyService mock
        given(studyService.getDynamicSimulationTimeSeries(NODE_UUID)).willReturn(TimeSeries.toJson(timeSeries));

        // --- call endpoint to be tested --- //
        // get result from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeseries",
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();

        String timeSeriesResultJson = result.getResponse().getContentAsString();

        // --- check result --- //
        String timeSeriesExpectedJson = TimeSeries.toJson(timeSeries);
        getLogger().info("Time series expected Json = " + timeSeriesExpectedJson);
        getLogger().info("Time series result Json = " + timeSeriesResultJson);

        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(objectMapper.readTree(timeSeriesExpectedJson), objectMapper.readTree(timeSeriesResultJson));
    }

    @Test
    public void testGetDynamicSimulationTimeLineResultGivenNodeNotDone() throws Exception {
        // setup StudyService mock
        given(studyService.getDynamicSimulationTimeLine(NODE_NOT_DONE_UUID)).willReturn(null);

        // --- call endpoint to be tested --- //
        // get result from a node not yet done
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeline",
                        STUDY_UUID, NODE_NOT_DONE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationTimeLineResult() throws Exception {
        TimeSeriesIndex index = new IrregularTimeSeriesIndex(new long[]{102479, 102479, 102479, 104396});
        StringTimeSeries timeLine = TimeSeries.createString("TimeLine", index,
                "CLA_2_5 - CLA : order to change topology",
                "_BUS____2-BUS____5-1_AC - LINE : opening both sides",
                "CLA_2_5 - CLA : order to change topology",
                "CLA_2_4 - CLA : arming by over-current constraint");

        // setup StudyService mock
        given(studyService.getDynamicSimulationTimeLine(NODE_UUID)).willReturn(TimeSeries.toJson(Arrays.asList(timeLine)));

        // --- call endpoint to be tested --- //
        // get result from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeline",
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();

        String timeLineResultJson = result.getResponse().getContentAsString();

        // --- check result --- //
        String timeLineExpectedJson = TimeSeries.toJson(Arrays.asList(timeLine));
        getLogger().info("Time line expected Json = " + timeLineExpectedJson);
        getLogger().info("Time line result Json = " + timeLineResultJson);

        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(objectMapper.readTree(timeLineExpectedJson), objectMapper.readTree(timeLineResultJson));
    }

    @Test
    public void testGetDynamicSimulationStatusResultGivenNodeNotRun() throws Exception {
        // setup StudyService mock
        given(studyService.getDynamicSimulationStatus(NODE_NOT_RUN_UUID)).willReturn(null);

        // --- call endpoint to be tested --- //
        // get result from a node not yet run
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS,
                        STUDY_UUID, NODE_NOT_RUN_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationStatusResult() throws Exception {
        // setup StudyService mock
        given(studyService.getDynamicSimulationStatus(NODE_UUID)).willReturn(DynamicSimulationStatus.DIVERGED.name());

        // --- call endpoint to be tested --- //
        // get status from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS,
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        String statusResult = result.getResponse().getContentAsString();

        // --- check result --- //
        String statusExpected = DynamicSimulationStatus.DIVERGED.name();
        getLogger().info("Status expected = " + statusExpected);
        getLogger().info("Status result = " + statusResult);
        assertEquals(statusExpected, statusResult);
    }

    @Test
    public void testGetDynamicSimulationMappings() throws Exception {
        // setup StudyService mock
        given(studyService.getDynamicSimulationMappings(NODE_UUID)).willReturn(MAPPINGS);

        // --- call endpoint to be tested --- //
        // get all mapping infos
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_MAPPINGS, STUDY_UUID, NODE_UUID)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        assertFalse("Content not null or empty", Strings.isBlank(content));

        List<MappingInfos> mappingInfos = objectMapper.readValue(content, new TypeReference<List<MappingInfos>>() { });

        // --- check result --- //
        getLogger().info("Mapping infos expected in Json = " + objectMapper.writeValueAsString(MAPPINGS));
        getLogger().info("Mapping infos result in Json = " + objectMapper.writeValueAsString(mappingInfos));
        assertEquals(MAPPINGS.size(), mappingInfos.size());

    }
}
