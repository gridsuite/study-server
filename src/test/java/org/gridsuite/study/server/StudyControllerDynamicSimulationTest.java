/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.DoubleTimeSeries;
import com.powsybl.timeseries.IrregularTimeSeriesIndex;
import com.powsybl.timeseries.TimeSeries;
import com.powsybl.timeseries.TimeSeriesIndex;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelVariableDefinitionInfos;
import org.gridsuite.study.server.dto.dynamicmapping.VariablesSetInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventPropertyInfos;
import org.gridsuite.study.server.dto.timeseries.TimeSeriesMetadataInfos;
import org.gridsuite.study.server.dto.timeseries.TimelineEventInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.utils.PropertyType;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class StudyControllerDynamicSimulationTest {

    private static final String API_VERSION = StudyApi.API_VERSION;
    private static final String DELIMITER = "/";
    private static final String STUDY_END_POINT = "studies";

    private static final String STUDY_BASE_URL = UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT);
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_RUN = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/run";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_PARAMETERS = "{studyUuid}/dynamic-simulation/parameters";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/result";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/status";

    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_MODELS = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/models";
    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_MAPPINGS = "{studyUuid}/dynamic-simulation/mappings";

    private static final String STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS = "{studyUuid}/nodes/{nodeUuid}/dynamic-simulation/events";

    private static final String HEADER_USER_ID_NAME = "userId";
    private static final String HEADER_USER_ID_VALUE = "userId";

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

    private static final int START_TIME = 0;

    private static final int STOP_TIME = 500;

    private static final String VARIANT_ID = "variant_1";

    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final UUID STUDY_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    public static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID NODE_NOT_DONE_UUID = UUID.randomUUID();
    private static final UUID NODE_NOT_RUN_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();

    private static final String PARAMETERS = String.format("{\"startTime\": %d, \"stopTime\": %d}", START_TIME, STOP_TIME);

    public static final String TIME_SERIES_NAME_1 = "NETWORK__BUS____2-BUS____5-1_AC_iSide2";
    public static final String TIME_SERIES_NAME_2 = "NETWORK__BUS____1_TN_Upu_value";

    // event data
    public static final String EQUIPMENT_ID = "_BUS____1-BUS____5-1_AC";
    public static final EventInfos EVENT = new EventInfos(null, NODE_UUID, EQUIPMENT_ID, "LINE", "Disconnect", List.of(
            new EventPropertyInfos(null, "staticId", EQUIPMENT_ID, PropertyType.STRING),
            new EventPropertyInfos(null, "startTime", "10", PropertyType.FLOAT),
            new EventPropertyInfos(null, "disconnectOnly", "TwoSides.ONE", PropertyType.ENUM)
    ));

    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc studyClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    StudyService studyService;

    @MockBean
    LoadFlowService loadFlowService;

    @SpyBean
    DynamicSimulationService dynamicSimulationService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    //output destinations
    private final String elementUpdateDestination = "element.update";
    private final String studyUpdateDestination = "study.update";
    private final String dsResultDestination = "ds.result";
    private final String dsStoppedDestination = "ds.stopped";
    private final String dsFailedDestination = "ds.failed";

    private Logger getLogger() {
        return LoggerFactory.getLogger(this.getClass());
    }

    @Before
    public void setup() {
    }

    @After
    public void tearDown() {
        cleanDB();
        List<String> destinations = List.of(studyUpdateDestination, dsFailedDestination, dsResultDestination, dsStoppedDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(studyClient.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", UUID.randomUUID(), null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
                modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectMapper.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        studyClient.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
                .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(mess).isNotNull();
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertThat(mess.getHeaders()).containsEntry(NotificationService.HEADER_INSERT_MODE, InsertMode.CHILD.name());
        return modificationNode;
    }

    @Test
    public void testRunDynamicSimulationGivenRegularNodeAndFailed() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        when(loadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED.name());
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> RESULT_UUID).when(dynamicSimulationService).runDynamicSimulation(any(), eq(studyUuid), eq(modificationNode1Uuid), any(), any(), any());

        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN,
                        studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getComputationResultUuid(modificationNode1Uuid, ComputationType.DYNAMIC_SIMULATION).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of failed
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), dsFailedDestination
        );

        // --- check async messages emitted by consumeDsFailed of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED);
        // resultUuid must be empty in database at this moment
        assertThat(networkModificationTreeService.getComputationResultUuid(modificationNode1Uuid, ComputationType.DYNAMIC_SIMULATION)).isEmpty();
    }

    @Test
    public void testRunDynamicSimulationGivenRootNode() throws Exception {
        // create a root node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        // --- call endpoint to be tested --- //
        // run on root node => forbidden
        studyClient.perform(post(UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT) + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                studyUuid, rootNodeUuid, MAPPING_NAME_01)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testRunDynamicSimulationGivenRegularNode() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        when(loadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED.name());
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> RESULT_UUID).when(dynamicSimulationService).runDynamicSimulation(any(), eq(studyUuid), eq(modificationNode1Uuid), any(), any(), any());

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN,
                        studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getComputationResultUuid(modificationNode1Uuid, ComputationType.DYNAMIC_SIMULATION).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of having the result
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), dsResultDestination
        );

        // --- check async messages emitted by consumeDsResult of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);

        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationResultMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationResultMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT);

        //Test result count
        Mockito.doAnswer(invocation -> 1).when(dynamicSimulationService).getResultsCount();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", String.valueOf(ComputationType.DYNAMIC_SIMULATION))
                        .queryParam("dryRun", String.valueOf(true)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");

        //Delete Dynamic result init results
        Mockito.doNothing().when(dynamicSimulationService).deleteResults();
        result = studyClient.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", String.valueOf(ComputationType.DYNAMIC_SIMULATION))
                        .queryParam("dryRun", String.valueOf(false)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");
    }

    @Test
    public void testRunDynamicSimulationGivenRegularNodeAndStopped() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        when(loadFlowService.getLoadFlowStatus(any())).thenReturn(LoadFlowStatus.CONVERGED.name());
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> RESULT_UUID).when(dynamicSimulationService).runDynamicSimulation(any(), eq(studyUuid), eq(modificationNode1Uuid), any(), any(), any());

        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN,
                        studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk());

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getComputationResultUuid(modificationNode1Uuid, ComputationType.DYNAMIC_SIMULATION).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertThat(actualResultUuid).isEqualTo(RESULT_UUID);

        // mock the notification from dynamic-simulation server in case of stop
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID.toString())
                .setHeader("receiver", receiver)
                .build(), dsStoppedDestination
        );

        // --- check async messages emitted by consumeDsStopped of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(dynamicSimulationStatusMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Test
    public void testGetDynamicSimulationTimeSeriesResultGivenNodeNotDone() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> null).when(dynamicSimulationService).getTimeSeriesResult(NODE_NOT_DONE_UUID, null);

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
        List<DoubleTimeSeries> timeSeries = new ArrayList<>(Arrays.asList(
                TimeSeries.createDouble(TIME_SERIES_NAME_1, index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble(TIME_SERIES_NAME_2, index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> timeSeries).when(dynamicSimulationService).getTimeSeriesResult(NODE_UUID, null);

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

        assertThat(objectMapper.readTree(timeSeriesResultJson)).isEqualTo(objectMapper.readTree(timeSeriesExpectedJson));
    }

    @Test
    public void testGetDynamicSimulationTimelineResultGivenNodeNotDone() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> null).when(dynamicSimulationService).getTimelineResult(NODE_NOT_DONE_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node not yet done
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeline",
                        STUDY_UUID, NODE_NOT_DONE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationTimelineResult() throws Exception {
        List<TimelineEventInfos> timelineEventInfosList = List.of(
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(102479, "_BUS____2-BUS____5-1_AC", "LINE : opening both sides"),
                new TimelineEventInfos(102479, "CLA_2_5", "CLA : order to change topology"),
                new TimelineEventInfos(104396, "CLA_2_4", "CLA : arming by over-current constraint")
        );

        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> timelineEventInfosList).when(dynamicSimulationService).getTimelineResult(NODE_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeline",
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();

        List<TimelineEventInfos> timelineEventInfosListResult = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });

        // --- check result --- //
        // must contain 4 timeline events
        assertThat(timelineEventInfosListResult).hasSize(4);
    }

    @Test
    public void testGetDynamicSimulationStatusResultGivenNodeNotRun() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> null).when(dynamicSimulationService).getStatus(NODE_NOT_RUN_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node not yet run
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS,
                        STUDY_UUID, NODE_NOT_RUN_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationTimeSeriesMetadata() throws Exception {
        // setup DynamicSimulationService mock
        // timeseries metadata
        List<TimeSeriesMetadataInfos> timeSeriesMetadataInfosList = List.of(new TimeSeriesMetadataInfos(TIME_SERIES_NAME_1), new TimeSeriesMetadataInfos(TIME_SERIES_NAME_2));

        Mockito.doAnswer(invocation -> timeSeriesMetadataInfosList).when(dynamicSimulationService).getTimeSeriesMetadataList(NODE_UUID);

        // --- call endpoint to be tested --- //
        // get timeseries metadata from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RESULT + DELIMITER + "timeseries/metadata",
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();

        List<TimeSeriesMetadataInfos> resultTimeSeriesMetadataInfosList = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });

        // --- check result --- //
        // metadata must be identical to expected
        String expectedTimeSeriesMetadataJson = objectMapper.writeValueAsString(timeSeriesMetadataInfosList);
        String resultTimeSeriesMetadataJson = objectMapper.writeValueAsString(resultTimeSeriesMetadataInfosList);
        assertThat(objectMapper.readTree(resultTimeSeriesMetadataJson)).isEqualTo(objectMapper.readTree(expectedTimeSeriesMetadataJson));
    }

    @Test
    public void testGetDynamicSimulationStatus() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> DynamicSimulationStatus.DIVERGED).when(dynamicSimulationService).getStatus(NODE_UUID);

        // --- call endpoint to be tested --- //
        // get status from a node done
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS,
                        STUDY_UUID, NODE_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        DynamicSimulationStatus statusResult = objectMapper.readValue(result.getResponse().getContentAsString(), DynamicSimulationStatus.class);

        // --- check result --- //
        DynamicSimulationStatus statusExpected = DynamicSimulationStatus.DIVERGED;
        getLogger().info("Status expected = " + statusExpected);
        getLogger().info("Status result = " + statusResult);
        assertThat(statusResult).isEqualTo(statusExpected);
    }

    @Test
    public void testGetDynamicSimulationMappings() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(invocation -> MAPPINGS).when(dynamicSimulationService).getMappings(STUDY_UUID);

        // --- call endpoint to be tested --- //
        // get all mapping infos
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_MAPPINGS, STUDY_UUID)
                .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();

        assertThat(content).isNotBlank();

        List<MappingInfos> mappingInfos = objectMapper.readValue(content, new TypeReference<>() { });

        // --- check result --- //
        getLogger().info("Mapping infos expected in Json = " + objectMapper.writeValueAsString(MAPPINGS));
        getLogger().info("Mapping infos result in Json = " + objectMapper.writeValueAsString(mappingInfos));
        assertThat(mappingInfos).hasSameSizeAs(MAPPINGS);

    }

    @Test
    public void testSetAndGetDynamicSimulationParameters() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // prepare request body
        DynamicSimulationParametersInfos defaultDynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();

        MvcResult result;

        // --- call endpoint to be tested --- //
        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultDynamicSimulationParameters)))
                    .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        // check notifications
        checkNotificationsAfterInjectingDynamicSimulationParameters(studyUuid);

        // get parameters
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

        String resultJson = result.getResponse().getContentAsString();
        String expectedJson = objectMapper.writeValueAsString(defaultDynamicSimulationParameters);

        // result parameters must be identical to persisted parameters
        getLogger().info("Parameters expected in Json = " + expectedJson);
        getLogger().info("Parameters result in Json = " + resultJson);
        assertThat(objectMapper.readTree(resultJson)).isEqualTo(objectMapper.readTree(expectedJson));

    }

    @Test
    public void testGetDynamicSimulationModels() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // setup DynamicSimulationService mock with a given mapping
        Mockito.doAnswer(invocation -> MODELS).when(dynamicSimulationService).getModels(MAPPING_NAME_01);

        // prepare request body with a mapping
        DynamicSimulationParametersInfos defaultDynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();
        defaultDynamicSimulationParameters.setMapping(MAPPING_NAME_01);

        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultDynamicSimulationParameters)))
                .andExpect(status().isOk()).andReturn();

        // check notifications
        checkNotificationsAfterInjectingDynamicSimulationParameters(studyUuid);

        MvcResult result;
        // --- call endpoint to be tested --- //
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_MODELS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        String resultJson = result.getResponse().getContentAsString();
        String expectedJson = objectMapper.writeValueAsString(MODELS);

        // result parameters must be identical to persisted parameters
        getLogger().info("Models expect in Json = " + expectedJson);
        getLogger().info("Models result in Json = " + resultJson);
        assertThat(objectMapper.readTree(resultJson)).isEqualTo(objectMapper.readTree(expectedJson));
    }

    @Test
    public void testGetDynamicSimulationModelsGivenEmptyMapping() throws Exception {
        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // setup DynamicSimulationService mock with a given mapping
        Mockito.doAnswer(invocation -> null).when(dynamicSimulationService).getModels("");

        // prepare request body without configure a mapping
        DynamicSimulationParametersInfos defaultDynamicSimulationParameters = DynamicSimulationService.getDefaultDynamicSimulationParameters();

        // set parameters
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_PARAMETERS, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(defaultDynamicSimulationParameters)))
                .andExpect(status().isOk()).andReturn();

        // check notifications
        checkNotificationsAfterInjectingDynamicSimulationParameters(studyUuid);

        // --- call endpoint to be tested --- //
        // must be no content status
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_MODELS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent()).andReturn();

    }

    private void checkNotificationsAfterInjectingDynamicSimulationParameters(UUID studyUuid) {
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS and UPDATE_TYPE_COMPUTATION_PARAMETERS from channel : studyUpdateDestination
        Message<byte[]> studyUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(studyUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        studyUpdateMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS, studyUpdateMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // must have message HEADER_USER_ID_VALUE from channel : elementUpdateDestination
        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, elementUpdateDestination);
        assertThat(elementUpdateMessage.getHeaders())
                .containsEntry(NotificationService.HEADER_ELEMENT_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_MODIFIED_BY, HEADER_USER_ID_VALUE);
    }

    // --- BEGIN Test event CRUD methods--- //

    private void checkNotificationsAfterInjectingDynamicSimulationEvent(UUID studyUuid, String crudType) {
        // must have message crudType from channel : studyUpdateDestination
        Message<byte[]> studyUpdateMessageBegin = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(studyUpdateMessageBegin.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, crudType);

        // must have message EVENTS_CRUD_FINISHED from channel : studyUpdateDestination
        Message<byte[]> elementUpdateMessageFinished = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(elementUpdateMessageFinished.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.EVENTS_CRUD_FINISHED);

        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]> studyUpdateMessageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertThat(studyUpdateMessageStatus.getHeaders())
                .containsEntry(NotificationService.HEADER_STUDY_UUID, studyUuid)
                .containsEntry(NotificationService.HEADER_UPDATE_TYPE, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    @Test
    public void testCrudDynamicSimulationEvents() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        MvcResult result;

        // --- call endpoint to be tested --- //
        // --- Post an event --- //
        studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(EVENT)))
                .andExpect(status().isOk()).andReturn();

        checkNotificationsAfterInjectingDynamicSimulationEvent(studyUuid, NotificationService.EVENTS_CRUD_CREATING_IN_PROGRESS);

        // --- Get the event --- //
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        // check result
        List<EventInfos> eventInfosList = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertThat(eventInfosList).hasSize(1);
        EventInfos eventInfosResult = eventInfosList.get(0);
        assertThat(eventInfosResult.getEventType()).isEqualTo(EVENT.getEventType());

        // --- Get event by node id and equipment id --- //
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .param("equipmentId", EQUIPMENT_ID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        // check result
        eventInfosResult = objectMapper.readValue(result.getResponse().getContentAsString(), EventInfos.class);
        assertThat(eventInfosResult.getEventType()).isEqualTo(EVENT.getEventType());

        // --- Update an event --- //
        Optional<EventPropertyInfos> startTimePropertyOpt = eventInfosResult.getProperties().stream().filter(elem -> elem.getName().equals("startTime")).findFirst();
        startTimePropertyOpt.ifPresent(elem -> elem.setValue("20"));

        // call update API
        studyClient.perform(put(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eventInfosResult)))
                .andExpect(status().isOk()).andReturn();

        checkNotificationsAfterInjectingDynamicSimulationEvent(studyUuid, NotificationService.EVENTS_CRUD_UPDATING_IN_PROGRESS);

        // check updated event
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .param("equipmentId", EQUIPMENT_ID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        EventInfos eventInfosUpdatedResult = objectMapper.readValue(result.getResponse().getContentAsString(), EventInfos.class);
        Optional<EventPropertyInfos> startTimePropertyUpdatedOpt = eventInfosUpdatedResult.getProperties().stream().filter(elem -> elem.getName().equals("startTime")).findFirst();
        assertThat(startTimePropertyUpdatedOpt.get().getValue()).isEqualTo("20");

        // --- Delete an event --- //
        studyClient.perform(delete(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .param("eventUuids", eventInfosUpdatedResult.getId().toString())
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        checkNotificationsAfterInjectingDynamicSimulationEvent(studyUuid, NotificationService.EVENTS_CRUD_DELETING_IN_PROGRESS);

        // check result => must empty
        result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_EVENTS, studyUuid, modificationNode1Uuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        // check result
        eventInfosList = objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() { });
        assertThat(eventInfosList).isEmpty();
    }

    // --- END Test event CRUD methods--- //
}
