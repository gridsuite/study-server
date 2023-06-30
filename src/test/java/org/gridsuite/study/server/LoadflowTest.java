/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/*
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.Branch;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.security.LimitViolationType;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.MatcherLoadFlowInfos;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import com.powsybl.security.LimitViolation;
import com.powsybl.security.LimitViolations;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.gridsuite.study.server.StudyException.Type.LOADFLOW_NOT_RUNNABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class LoadflowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadflowTest.class);

    private static final String CASE_LOADFLOW_ERROR_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_ERROR_UUID = UUID.fromString(CASE_LOADFLOW_ERROR_UUID_STRING);
    private static final String NETWORK_LOADFLOW_ERROR_UUID_STRING = "7845000f-5af0-14be-bc3e-10b96e4ef00d";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID_ID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String CASE_LOADFLOW_EXCEPTION_UUID_STRING = "aaaaaaaa-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_EXCEPTION_UUID = UUID.fromString(CASE_LOADFLOW_EXCEPTION_UUID_STRING);
    private static final String NETWORK_LOADFLOW_EXCEPTION_UUID_STRING = "aaaaaaaa-5af0-14be-bc3e-10b96e4ef00d";

    public static final String LOAD_PARAMETERS_JSON = "{\"commonParameters\":{\"version\":\"1.9\",\"voltageInitMode\":\"UNIFORM_VALUES\",\"transformerVoltageControlOn\":false,\"phaseShifterRegulationOn\":false,\"useReactiveLimits\":true,\"twtSplitShuntAdmittance\":false,\"shuntCompensatorVoltageControlOn\":false,\"readSlackBus\":true,\"writeSlackBus\":false,\"dc\":false,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_GENERATION_P_MAX\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\",\"hvdcAcEmulation\":true,\"dcPowerFactor\":1.0},\"specificParametersPerProvider\":{}}";
    public static final String LOAD_PARAMETERS_JSON2 = "{\"commonParameters\":{\"version\":\"1.9\",\"voltageInitMode\":\"DC_VALUES\",\"transformerVoltageControlOn\":true,\"phaseShifterRegulationOn\":true,\"useReactiveLimits\":true,\"twtSplitShuntAdmittance\":false,\"shuntCompensatorVoltageControlOn\":true,\"readSlackBus\":false,\"writeSlackBus\":true,\"dc\":true,\"distributedSlack\":true,\"balanceType\":\"PROPORTIONAL_TO_CONFORM_LOAD\",\"dcUseTransformerRatio\":true,\"countriesToBalance\":[],\"connectedComponentMode\":\"MAIN\",\"hvdcAcEmulation\":true,\"dcPowerFactor\":1.0},\"specificParametersPerProvider\":{}}";

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final long TIMEOUT = 1000;

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private LoadflowService loadflowService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NetworkStoreService networkStoreService;

    private Network network;

    //output destinations
    private final String studyUpdateDestination = "study.update";

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        when(networkStoreService.getNetwork(NETWORK_UUID_ID, PreloadingStrategy.COLLECTION)).then((Answer<Network>) invocation -> {
            network = EurostagTutorialExample1Factory.createWithFixedCurrentLimits();
            return network;
        });

        // Start the server.
        server.start();

     // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        loadflowService.setLoadFlowServerBaseUri(baseUrl);

        // results definitions
        LoadFlowResult loadFlowError = new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs",
                List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.MAX_ITERATION_REACHED, 10, "bus_1", 5., 1),
                    new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.CONVERGED, 20, "bus_2", 10., 4)));
        String loadFlowErrorString = mapper.writeValueAsString(loadFlowError);

        LoadFlowResult loadFlowOK = new LoadFlowResultImpl(true, Map.of("key_1", "metric_1", "key_2", "metric_2"), "logs",
                List.of(new LoadFlowResultImpl.ComponentResultImpl(0, 0, LoadFlowResult.ComponentResult.Status.CONVERGED, 10, "bus_1", 5., 3.5),
                    new LoadFlowResultImpl.ComponentResultImpl(1, 1, LoadFlowResult.ComponentResult.Status.FAILED, 20, "bus_2", 10., 2.78)));
        String loadFlowOKString = mapper.writeValueAsString(loadFlowOK);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=(Hades2|OpenLoadFlow)&variantId=.*")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody(loadFlowOKString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=(Hades2|OpenLoadFlow)&variantId=.*")) {
                    return new MockResponse().setResponseCode(200)
                            .setBody(loadFlowErrorString)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_LOADFLOW_EXCEPTION_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=(Hades2|OpenLoadFlow)&variantId=.*")) {
                    return new MockResponse().setResponseCode(500)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    @Test
    public void testLoadFlowError() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_LOADFLOW_ERROR_UUID_STRING), CASE_LOADFLOW_ERROR_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        // run loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNodeUuid))
            .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_LOADFLOW_ERROR_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // check load flow status
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid,
                        modificationNodeUuid)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowInfos lfInfos = mapper.readValue(resultAsString, LoadFlowInfos.class);

        assertThat(lfInfos, new MatcherLoadFlowInfos(
                LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.DIVERGED).build()));
    }

    @Test
    public void testLoadFlowWithException() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_LOADFLOW_EXCEPTION_UUID_STRING), CASE_LOADFLOW_EXCEPTION_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node");
        UUID modificationNodeUuid = modificationNode.getId();

        // run loadflow : internal server error
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, modificationNodeUuid))
                .andExpect(status().isInternalServerError());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_LOADFLOW_EXCEPTION_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid,
                        modificationNodeUuid)).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowInfos loadFlowInfos = mapper.readValue(resultAsString, LoadFlowInfos.class);

        assertThat(loadFlowInfos, new MatcherLoadFlowInfos(LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.DIVERGED).build()));
    }

    @Test
    public void testLoadFlow() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_ERROR_UUID, false);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run a loadflow on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, rootNodeUuid))
            .andExpect(status().isForbidden());

        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid,
                modificationNode2Uuid)).andExpect(
                        status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, NotificationService.UPDATE_TYPE_LOADFLOW);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // check load flow status
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid,
                        modificationNode2Uuid)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowInfos loadFlowInfos = mapper.readValue(resultAsString, LoadFlowInfos.class);

        assertThat(loadFlowInfos, new MatcherLoadFlowInfos(
                        LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.CONVERGED).build()));

        //try to run a another loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid,
                        modificationNode2Uuid)).andExpectAll(
                                status().isForbidden(),
                                jsonPath("$", is(LOADFLOW_NOT_RUNNABLE.name())));

        // get default LoadFlowParameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(LOAD_PARAMETERS_JSON));

        // setting loadFlow Parameters
        LoadFlowParametersValues lfParamsValues = LoadFlowParametersValues.builder()
                .commonParameters(new LoadFlowParameters()
                        .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES)
                        .setTransformerVoltageControlOn(true)
                        .setUseReactiveLimits(true)
                        .setPhaseShifterRegulationOn(true)
                        .setTwtSplitShuntAdmittance(false)
                        .setShuntCompensatorVoltageControlOn(true)
                        .setReadSlackBus(false)
                        .setWriteSlackBus(true)
                        .setDc(true)
                        .setDistributedSlack(true)
                        .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                        .setDcUseTransformerRatio(true)
                        .setCountriesToBalance(EnumSet.noneOf(Country.class))
                        .setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                        .setHvdcAcEmulation(true))
                .specificParametersPerProvider(null)
                .build();
        String lfpBodyJson = objectWriter.writeValueAsString(lfParamsValues);

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
            .header("userId", "userId")
            .contentType(MediaType.APPLICATION_JSON)
                    .content(lfpBodyJson)).andExpect(
                            status().isOk());
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, null);

        // getting setted values
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(LOAD_PARAMETERS_JSON2));

        // run loadflow with new parameters
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid,
                modificationNode2Uuid)).andExpect(
                        status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, NotificationService.UPDATE_TYPE_LOADFLOW);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID)));

        // get default load flow provider
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(defaultLoadflowProvider));

        // set load flow provider
        mockMvc.perform(post("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid).header("userId", "userId").contentType(MediaType.TEXT_PLAIN).content("Hades2"))
            .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        // get load flow provider
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)).andExpectAll(
                    status().isOk(),
                    content().string("Hades2"));

        // reset load flow provider to default one
        mockMvc.perform(post("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid).header("userId", "userId"))
            .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, null, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        // get default load flow provider again
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/provider", studyNameUserIdUuid)).andExpectAll(
                status().isOk(),
                content().string(defaultLoadflowProvider));

        //run a loadflow on another node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid,
                modificationNode3Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid, NotificationService.UPDATE_TYPE_LOADFLOW);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run\\?reportId=.*&reportName=.*&provider=" + defaultLoadflowProvider + "&variantId=" + VARIANT_ID_3)));

        // check load flow status
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/loadflow/infos", studyNameUserIdUuid,
                        modificationNode3Uuid)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        LoadFlowInfos lfInfos = mapper.readValue(resultAsString, LoadFlowInfos.class);

        assertThat(lfInfos, new MatcherLoadFlowInfos(
                        LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.CONVERGED).build()));

        // setting loadFlow Parameters with specific params
        lfParamsValues = LoadFlowParametersValues.builder()
                .commonParameters(new LoadFlowParameters()
                .setVoltageInitMode(LoadFlowParameters.VoltageInitMode.DC_VALUES)
                .setTransformerVoltageControlOn(true)
                .setUseReactiveLimits(true)
                .setPhaseShifterRegulationOn(true)
                .setTwtSplitShuntAdmittance(false)
                .setShuntCompensatorVoltageControlOn(true)
                .setReadSlackBus(false)
                .setWriteSlackBus(true)
                .setDc(true)
                .setDistributedSlack(true)
                .setBalanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_CONFORM_LOAD)
                .setDcUseTransformerRatio(true)
                .setCountriesToBalance(EnumSet.noneOf(Country.class))
                .setConnectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .setHvdcAcEmulation(true))
                .specificParametersPerProvider(Map.of("OpenLoadFlow", Map.of("transformerVoltageControlMode", "WITH_GENERATOR_VOLTAGE_CONTROL")))
                .build();
        lfpBodyJson = objectWriter.writeValueAsString(lfParamsValues);
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lfpBodyJson)).andExpect(
                status().isOk());
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, null);

        // setting loadFlow Parameters with no data
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)).andExpect(
                status().isOk());
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, null);
    }

    @SneakyThrows
    public List<LimitViolationInfos> getLimitViolations(boolean dcMode) {
        // create a study and a node
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_ERROR_UUID, dcMode);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VariantManagerConstants.INITIAL_VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // retrieve current and voltage violations
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/limit-violations?limitReduction=1.0",
                studyNameUserIdUuid,
                modificationNode1Uuid)).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();
        String result = mvcResult.getResponse().getContentAsString();
        return mapper.readValue(result, new TypeReference<List<LimitViolationInfos>>() { });
    }

    @Test
    public void testLimitViolations() {
        List<LimitViolationInfos> violations = getLimitViolations(false);
        // the mocked network lines/terminals have no computed 'i' => no overload can be detected
        assertEquals(0, violations.size());
    }

    @Test
    public void testLimitViolationsDcMode() {
        List<LimitViolationInfos> violations = getLimitViolations(true);
        // in DC mode, we use power values => one overload is detected
        assertEquals(1, violations.size());
        assertEquals("NHV1_NHV2_1", violations.get(0).getSubjectId());
        assertEquals("permanent", violations.get(0).getLimitName());
    }

    @Test
    public void testLimitViolationInfos() {
        LimitViolation violation = LimitViolations.current()
                .subject("id")
                .duration(1, TimeUnit.MINUTES)
                .limit(1500.0)
                .limitName("limit")
                .value(2000.0)
                .side(Branch.Side.ONE)
                .build();
        LimitViolationInfos violationInfos = StudyService.toLimitViolationInfos(violation);
        assertTrue(violationInfos.getSubjectId().equalsIgnoreCase("id") &&
                violationInfos.getAcceptableDuration() == 60 &&
                violationInfos.getLimit() == 1500.0 &&
                violationInfos.getLimitName().equalsIgnoreCase("limit") &&
                violationInfos.getValue() == 2000.0 &&
                violationInfos.getSide().equalsIgnoreCase("ONE") &&
                violationInfos.getLimitType() == LimitViolationType.CURRENT);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, boolean dcMode) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .dcPowerFactor(1.0)
                .dc(dcMode)
                .useReactiveLimits(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {

        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
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
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).header("userId", "userId").content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);

        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
