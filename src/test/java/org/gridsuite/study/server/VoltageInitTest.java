/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.IdentifiableType;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact;
import org.gridsuite.study.server.dto.voltageinit.FilterEquipments;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitParametersInfos;
import org.gridsuite.study.server.dto.voltageinit.VoltageInitVoltageLimitsParameterInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.*;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.utils.ImpactUtils.createModificationResultWithElementImpact;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class VoltageInitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoltageInitTest.class);

    private static final String CASE_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String VOLTAGE_INIT_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String VOLTAGE_INIT_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String VOLTAGE_INIT_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final VoltageInitParametersInfos VOLTAGE_INIT_PARAMETERS = createVoltageInitParameters();
    private static final VoltageInitParametersInfos VOLTAGE_INIT_PARAMETERS2 = createVoltageInitParametersWithVariableAndConstanEquipments();
    private static final String FILTER_EQUIPMENT_JSON = "[{\"filterId\":\"cf399ef3-7f14-4884-8c82-1c90300da329\",\"identifiableAttributes\":[{\"id\":\"VL1\",\"type\":\"VOLTAGE_LEVEL\"}],\"notFoundEquipments\":[]}]";

    private static final String VOLTAGE_INIT_RESULT_JSON = "{\"version\":\"1.0\"}";

    private static final String VOLTAGE_INIT_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String VARIANT_ID = "variant_1";

    private static final String VARIANT_ID_2 = "variant_2";

    private static final String VARIANT_ID_3 = "variant_3";

    private static final long TIMEOUT = 1000;

    private static final String MODIFICATIONS_GROUP_UUID = "aaaaaaaa-bbbb-cccc-8c82-1c90300da329";

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private FilterService filterService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @Autowired
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ShortCircuitService shortCircuitService;

    @Autowired
    private VoltageInitService voltageInitService;

    @MockBean
    private NetworkStoreService networkStoreService;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    private final String voltageInitResultDestination = "voltageinit.result";
    private final String voltageInitStoppedDestination = "voltageinit.stopped";
    private final String voltageInitFailedDestination = "voltageinit.failed";
    private final String elementUpdateDestination = "element.update";

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        Network network = Network.create("test", "IIDM");
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        voltageInitService.setVoltageInitServerBaseUri(baseUrl);
        filterService.setFilterServerBaseUri(baseUrl);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);
        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(baseUrl);
        shortCircuitService.setShortCircuitServerBaseUri(baseUrl);

        String voltageInitResultUuidStr = objectMapper.writeValueAsString(VOLTAGE_INIT_RESULT_UUID);

        String voltageInitErrorResultUuidStr = objectMapper.writeValueAsString(VOLTAGE_INIT_ERROR_RESULT_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());

                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", VOLTAGE_INIT_RESULT_UUID)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), voltageInitResultDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(voltageInitResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)) {
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .setHeader("resultUuid", VOLTAGE_INIT_ERROR_RESULT_UUID)
                        .build(), voltageInitFailedDestination);
                    return new MockResponse().setResponseCode(200)
                            .setBody(voltageInitErrorResultUuidStr)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID)) {
                    return new MockResponse().setResponseCode(200).setBody(VOLTAGE_INIT_RESULT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status")) {
                    return new MockResponse().setResponseCode(200).setBody(VOLTAGE_INIT_STATUS_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid")) {
                    return new MockResponse().setResponseCode(200).setBody("\"" + MODIFICATIONS_GROUP_UUID + "\"")
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*/duplications.*")) {
                    Optional<NetworkModificationResult> networkModificationResult =
                        createModificationResultWithElementImpact(SimpleElementImpact.SimpleImpactType.MODIFICATION,
                            IdentifiableType.GENERATOR, "genId", Set.of("s1"));
                    return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(networkModificationResult))
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/stop.*")
                        || path.matches("/v1/results/" + VOLTAGE_INIT_OTHER_NODE_RESULT_UUID + "/stop.*")) {
                    String resultUuid = path.matches(".*variantId=" + VARIANT_ID_2 + ".*") ? VOLTAGE_INIT_OTHER_NODE_RESULT_UUID : VOLTAGE_INIT_RESULT_UUID;
                    input.send(MessageBuilder.withPayload("")
                            .setHeader("resultUuid", resultUuid)
                            .setHeader("receiver", "%7B%22nodeUuid%22%3A%22" + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                            .build(), voltageInitStoppedDestination);
                    return new MockResponse().setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/filters/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID_2 + "&ids=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(FILTER_EQUIPMENT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/filters/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID + "&ids=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(FILTER_EQUIPMENT_JSON)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/filters/export\\?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID_3 + "&ids=.*")) {
                    return new MockResponse().setResponseCode(500).setBody("Filter not found")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else {
                    LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                    return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }

        };

        server.setDispatcher(dispatcher);
    }

    private void initMockBeans(Network network) {
        when(networkStoreService.getNetwork(UUID.fromString(NETWORK_UUID_STRING))).thenReturn(network);
    }

    public static VoltageInitParametersInfos createVoltageInitParameters() {
        FilterEquipments equipments = new FilterEquipments(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da329"), "identifiable", null, null);
        VoltageInitVoltageLimitsParameterInfos voltageLimits = new VoltageInitVoltageLimitsParameterInfos(0, 15.0, 123.0, List.of(equipments));
        return new VoltageInitParametersInfos(List.of(voltageLimits), List.of(), List.of(), List.of());
    }

    public static VoltageInitParametersInfos createVoltageInitParametersWithVariableAndConstanEquipments() {
        FilterEquipments equipments = new FilterEquipments(UUID.fromString("cf399ef3-7f14-4884-8c82-1c90300da329"), "identifiable", null, null);
        VoltageInitVoltageLimitsParameterInfos voltageLimits = new VoltageInitVoltageLimitsParameterInfos(0, 15.0, 126.0, List.of(equipments));
        FilterEquipments generatorFilter = new FilterEquipments(UUID.fromString("cae7c0dc-9598-4f97-ae03-062207f36d2f"), "constantGenerators", null, null);
        FilterEquipments transfoFilter = new FilterEquipments(UUID.fromString("b5b4b3f4-27a7-4b27-be96-7a5fd1621849"), "variableTransfos", null, null);
        FilterEquipments shuntFilter = new FilterEquipments(UUID.fromString("d1bd319e-94cb-4522-84dd-e644763c947d"), "variableShunts", null, null);
        return new VoltageInitParametersInfos(List.of(voltageLimits), List.of(generatorFilter), List.of(transfoFilter), List.of(shuntFilter));
    }

    @Test
    public void testVoltageInitParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, createVoltageInitParameters());
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get initial voltage init parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS), mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        VoltageInitParametersInfos parameters = createVoltageInitParametersWithVariableAndConstanEquipments();
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parameters))).andExpect(
                status().isOk());

        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS2), mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    public void testVoltageInit() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, createVoltageInitParametersWithVariableAndConstanEquipments());
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 4");
        UUID modificationNode4Uuid = modificationNode4.getId();

        // run a voltage init on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run a voltage init analysis
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(TestUtils.getRequestsDone(4, server).stream().anyMatch(r -> r.matches("/v1/filters/export.*")));

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        resultAsString = mvcResult.getResponse().getContentAsString();
        UUID uuidResponse = objectMapper.readValue(resultAsString, UUID.class);
        assertEquals(uuidResponse, UUID.fromString(VOLTAGE_INIT_RESULT_UUID));

        // get voltage init result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/result", studyNameUserIdUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(VOLTAGE_INIT_RESULT_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID)));

        // get voltage init status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/status", studyNameUserIdUuid, modificationNode3Uuid)).andExpectAll(
                status().isOk(),
                content().string(VOLTAGE_INIT_STATUS_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status")));

        // stop voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, modificationNode3Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        // voltage init failed
        mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode2Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        uuidResponse = objectMapper.readValue(resultAsString, UUID.class);

        assertEquals(VOLTAGE_INIT_ERROR_RESULT_UUID, uuidResponse.toString());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(5, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode4Uuid)
                .header("userId", "userId"))
                .andExpect(status().is5xxServerError()).andReturn();
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/filters/export.*")));
    }

    @Test
    public void testCopyVoltageInitModifications() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, createVoltageInitParametersWithVariableAndConstanEquipments());
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run a voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode3Uuid)
                .header("userId", "userId"))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(4, server).stream().anyMatch(r -> r.matches("/v1/filters/export.*")));

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // clone and copy modifications to modificationNode3Uuid
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/modifications", studyNameUserIdUuid, modificationNode3Uuid)
            .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(4, server).stream().allMatch(r ->
            r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID) ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status") ||
                r.matches("/v1/groups/.*/duplications.*")
        ));

        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNode3Uuid));
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_STUDY);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.NODE_BUILD_STATUS_UPDATED);
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, "userId");
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
    }

    private void checkElementUpdatedMessageSent(UUID elementUuid, String userId) {
        Message<byte[]> message = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(elementUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        assertEquals(userId, message.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkNodesBuildStatusUpdatedMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentUpdatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    @SneakyThrows
    public void testNotResetedUuidResultWhenVoltageInitFailed() {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), createVoltageInitParameters());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateVoltageInitResultUuid(modificationNode.getId(), resultUuid);
        assertTrue(networkModificationTreeService.getVoltageInitResultUuid(modificationNode.getId()).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getVoltageInitResultUuid(modificationNode.getId()).get());

        StudyService studyService = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(
                MessageBuilder.withPayload("")
                    .setHeader(HEADER_RECEIVER, resultUuidJson)
                    .setHeader("resultUuid", VOLTAGE_INIT_ERROR_RESULT_UUID)
                .build(), voltageInitFailedDestination);
            return resultUuid;
        }).when(studyService).runVoltageInit(any(), any(), any());
        studyService.runVoltageInit(studyEntity.getId(), modificationNode.getId(), "");

        // Test doesn't reset uuid result in the database
        assertEquals(VOLTAGE_INIT_ERROR_RESULT_UUID, networkModificationTreeService.getVoltageInitResultUuid(modificationNode.getId()).get().toString());

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> voltageInitStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) voltageInitStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    @Test
    public void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, createVoltageInitParameters());
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No voltage init result
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/result", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No voltage init status
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/status", studyNameUserIdUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // stop non existing voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, modificationNode1Uuid)).andExpect(status().isOk());
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, VoltageInitParametersInfos voltageInitParameters) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());

        VoltageInitParametersEntity voltageInitParametersEntity = VoltageInitService.toEntity(voltageInitParameters);

        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", "defaultLoadflowProvider", defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, voltageInitParametersEntity, null);
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
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
                .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination, voltageInitResultDestination, voltageInitStoppedDestination, voltageInitFailedDestination);

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
