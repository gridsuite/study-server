/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.timeseries.*;
import org.apache.logging.log4j.util.Strings;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.utils.TestUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

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

    private static final String VARIANT_ID = "variant_1";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    private static final String STUDY_UUID_STRING = "00000000-0000-0000-0000-000000000000";
    private static final UUID STUDY_UUID = UUID.fromString(STUDY_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "11111111-0000-0000-0000-000000000000";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);

    private static final String NODE_UUID_STRING = "22222222-1111-0000-0000-000000000000";
    public static final UUID NODE_UUID = UUID.fromString(NODE_UUID_STRING);

    private static final String NODE_NOT_DONE_UUID_STRING = "22222222-2222-0000-0000-000000000000";
    private static final UUID NODE_NOT_DONE_UUID = UUID.fromString(NODE_NOT_DONE_UUID_STRING);

    private static final String NODE_NOT_RUN_UUID_STRING = "22222222-3333-0000-0000-000000000000";
    private static final UUID NODE_NOT_RUN_UUID = UUID.fromString(NODE_NOT_RUN_UUID_STRING);

    private static final String PARAMETERS = String.format("{\"startTime\": %d, \"stopTime\": %d}", START_TIME, STOP_TIME);

    private static final String RESULT_UUID_STRING = "99999999-0000-0000-0000-000000000000";
    private static final UUID RESULT_UUID = UUID.fromString(RESULT_UUID_STRING);

    private static final long TIMEOUT = 1000;

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc studyClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    StudyService studyService;

    @SpyBean
    DynamicSimulationService dynamicSimulationService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    //output destinations
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
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder().build();
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, null);
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
                .loadFlowStatus(LoadFlowStatus.CONVERGED).buildStatus(buildStatus)
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
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
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

        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public UUID answer(InvocationOnMock invocation) {
                return RESULT_UUID;
            }
        }).when(dynamicSimulationService).runDynamicSimulation(any(), eq(NETWORK_UUID), any(), eq(START_TIME), eq(STOP_TIME), eq(MAPPING_NAME_01));

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        result = studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                        studyUuid, modificationNode1Uuid, MAPPING_NAME_01)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        String resultUuidJson = result.getResponse().getContentAsString();
        UUID resultUuid = objectMapper.readValue(resultUuidJson, UUID.class);

        assertEquals(RESULT_UUID, resultUuid);

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]>  dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getDynamicSimulationResultUuid(modificationNode1Uuid).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertEquals(RESULT_UUID, actualResultUuid);

        // mock the notification from dynamic-simulation server in case of failed
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID_STRING)
                .setHeader("receiver", receiver)
                .build(), dsFailedDestination
        );

        // --- check async messages emitted by consumeDsFailed of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        // resultUuid must be empty in database at this moment
        assertTrue(networkModificationTreeService.getDynamicSimulationResultUuid(modificationNode1Uuid).isEmpty());
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

        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public UUID answer(InvocationOnMock invocation) {
                return RESULT_UUID;
            }
        }).when(dynamicSimulationService).runDynamicSimulation(any(), eq(NETWORK_UUID), any(), eq(START_TIME), eq(STOP_TIME), eq(MAPPING_NAME_01));

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        result = studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                        studyUuid, modificationNode1Uuid, MAPPING_NAME_01)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        String resultUuidJson = result.getResponse().getContentAsString();
        UUID resultUuid = objectMapper.readValue(resultUuidJson, UUID.class);

        assertEquals(RESULT_UUID, resultUuid);

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]>  dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getDynamicSimulationResultUuid(modificationNode1Uuid).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertEquals(RESULT_UUID, actualResultUuid);

        // mock the notification from dynamic-simulation server in case of having the result
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID_STRING)
                .setHeader("receiver", receiver)
                .build(), dsResultDestination
        );

        // --- check async messages emitted by consumeDsResult of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT from channel : studyUpdateDestination
        Message<byte[]> dynamicSimulationResultMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationResultMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT, dynamicSimulationResultMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    public void testRunDynamicSimulationGivenRegularNodeAndStopped() throws Exception {

        // create a node in the db
        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public UUID answer(InvocationOnMock invocation) {
                return RESULT_UUID;
            }
        }).when(dynamicSimulationService).runDynamicSimulation(any(), eq(NETWORK_UUID), any(), eq(START_TIME), eq(STOP_TIME), eq(MAPPING_NAME_01));

        MvcResult result;
        // --- call endpoint to be tested --- //
        // run on a regular node which allows a run
        result = studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_RUN + "?mappingName={mappingName}",
                        studyUuid, modificationNode1Uuid, MAPPING_NAME_01)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARAMETERS))
                .andExpect(status().isOk()).andReturn();

        // --- check result --- //
        String resultUuidJson = result.getResponse().getContentAsString();
        UUID resultUuid = objectMapper.readValue(resultUuidJson, UUID.class);

        assertEquals(RESULT_UUID, resultUuid);

        // --- check async messages emitted by runDynamicSimulation of StudyService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        Message<byte[]>  dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        // resultUuid must be present in database at this moment
        UUID actualResultUuid = networkModificationTreeService.getDynamicSimulationResultUuid(modificationNode1Uuid).get();
        getLogger().info("Actual result uuid in the database = " + actualResultUuid);
        assertEquals(RESULT_UUID, actualResultUuid);

        // mock the notification from dynamic-simulation server in case of stop
        String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid)),
                StandardCharsets.UTF_8);
        input.send(MessageBuilder.withPayload("")
                .setHeader("resultUuid", RESULT_UUID_STRING)
                .setHeader("receiver", receiver)
                .build(), dsStoppedDestination
        );

        // --- check async messages emitted by consumeDsStopped of ConsumerService --- //
        // must have message UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS from channel : studyUpdateDestination
        dynamicSimulationStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, dynamicSimulationStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    public void testGetDynamicSimulationTimeSeriesResultGivenNodeNotDone() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public List<DoubleTimeSeries> answer(InvocationOnMock invocation) {
                return null;
            }
        }).when(dynamicSimulationService).getTimeSeriesResult(NODE_NOT_DONE_UUID);

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
                TimeSeries.createDouble("NETWORK__BUS____2-BUS____5-1_AC_iSide2", index, 333.847331, 333.847321, 333.847300, 333.847259),
                TimeSeries.createDouble("NETWORK__BUS____1_TN_Upu_value", index, 1.059970, 1.059970, 1.059970, 1.059970)
        ));

        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public List<DoubleTimeSeries> answer(InvocationOnMock invocation) {
                return timeSeries;
            }
        }).when(dynamicSimulationService).getTimeSeriesResult(NODE_UUID);

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

        assertEquals(objectMapper.readTree(timeSeriesExpectedJson), objectMapper.readTree(timeSeriesResultJson));
    }

    @Test
    public void testGetDynamicSimulationTimeLineResultGivenNodeNotDone() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public List<TimeSeries> answer(InvocationOnMock invocation) {
                return null;
            }
        }).when(dynamicSimulationService).getTimeLineResult(NODE_NOT_DONE_UUID);

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

        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public List<TimeSeries> answer(InvocationOnMock invocation) {
                return List.of(timeLine);
            }
        }).when(dynamicSimulationService).getTimeLineResult(NODE_UUID);

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

        assertEquals(objectMapper.readTree(timeLineExpectedJson), objectMapper.readTree(timeLineResultJson));
    }

    @Test
    public void testGetDynamicSimulationStatusResultGivenNodeNotRun() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public DynamicSimulationStatus answer(InvocationOnMock invocation) {
                return null;
            }
        }).when(dynamicSimulationService).getStatus(NODE_NOT_RUN_UUID);

        // --- call endpoint to be tested --- //
        // get result from a node not yet run
        studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_DYNAMIC_SIMULATION_END_POINT_STATUS,
                        STUDY_UUID, NODE_NOT_RUN_UUID)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isNoContent());
    }

    @Test
    public void testGetDynamicSimulationStatus() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public DynamicSimulationStatus answer(InvocationOnMock invocation) {
                return DynamicSimulationStatus.DIVERGED;
            }
        }).when(dynamicSimulationService).getStatus(NODE_UUID);

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
        assertEquals(statusExpected, statusResult);
    }

    @Test
    public void testGetDynamicSimulationMappings() throws Exception {
        // setup DynamicSimulationService mock
        Mockito.doAnswer(new Answer() {
            @Override
            public List<MappingInfos> answer(InvocationOnMock invocation) {
                return MAPPINGS;
            }
        }).
        when(dynamicSimulationService).getMappings(NODE_UUID);

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
