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
import com.powsybl.network.store.client.NetworkStoreService;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitPredefinedConfiguration;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact;
import org.gridsuite.study.server.networkmodificationtree.dto.*;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.*;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.sensianalysis.SensitivityAnalysisParametersEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
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
import static org.gridsuite.study.server.dto.ComputationType.VOLTAGE_INITIALIZATION;
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

    private static final String VOLTAGE_INIT_PARAMETERS_JSON = "{\"uuid\":\"0c0f1efd-bd22-4a75-83d3-9e530245c7f4\",\"date\":\"2023-08-22T12:43:37.596944+02:00\",\"name\":null,\"voltageLimits\":[{\"priority\":0,\"lowVoltageLimit\":24,\"highVoltageLimit\":552,\"filters\":[{\"containerId\":\"6754396b-3791-4b80-9971-defbf5968fb7\",\"containerName\":\"testfp\",\"identifiableAttributes\":null,\"notFoundEquipments\":null}]}],\"constantQGenerators\":[{\"containerId\":\"ff915f2f-578c-4d8c-a267-0135a4323462\",\"containerName\":\"testf1\",\"identifiableAttributes\":null,\"notFoundEquipments\":null}],\"variableTwoWindingsTransformers\":[],\"variableShuntCompensators\":[]}]";

    private static final String VOLTAGE_INIT_EMPTY_PARAMETERS = "{}";

    private static final String VOLTAGE_INIT_PARAMETERS_UUID = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";

    private static final String WRONG_VOLTAGE_INIT_PARAMETERS_UUID = "0c0f1efd-bd22-1111-83d3-9e530245c7f4";

    private static final String VOLTAGE_INIT_RESULT_JSON = "{\"version\":\"1.0\"}";

    private static final String VOLTAGE_INIT_PREVIEW_MODIFICATION_LIST = "[{\"type\": \"VOLTAGE_INIT_MODIFICATION\",\"uuid\": \"254a3b85-14ad-4436-bf62-3e60af831dd1\",\"date\": \"2023-09-18T10:58:32.582239Z\",\"stashed\": false,\"generators\": [],\"transformers\": [],\"staticVarCompensators\": [],\"vscConverterStations\": [],\"shuntCompensators\": []}]";

    private static final String VOLTAGE_INIT_STATUS_JSON = "{\"status\":\"COMPLETED\"}";
    private static final String VARIANT_ID = "variant_1";

    private static final String VARIANT_ID_2 = "variant_2";

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

    @Autowired
    private NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

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
                String method = Objects.requireNonNull(request.getMethod());

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
                } else if (path.matches("/v1/groups/" + MODIFICATIONS_GROUP_UUID + "/network-modifications\\?errorOnGroupNotFound=false&onlyStashed=false&onlyMetadata=.*")) {
                    return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(VOLTAGE_INIT_PREVIEW_MODIFICATION_LIST))
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
                } else if (path.matches("/v1/results/invalidate-status.*")) {
                    return new MockResponse().setResponseCode(200);
                } else if (path.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID)) {
                    if (method.equals("PUT")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_INIT_PARAMETERS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    }
                } else if (path.matches("/v1/parameters/" + WRONG_VOLTAGE_INIT_PARAMETERS_UUID)) {
                    return new MockResponse().setResponseCode(404);
                } else if (path.matches("/v1/parameters")) {
                    return new MockResponse().setResponseCode(200).setBody(objectMapper.writeValueAsString(VOLTAGE_INIT_PARAMETERS_UUID))
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/results")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/supervision/results-count")) {
                    return new MockResponse().setResponseCode(200)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .setBody("1");
                } else if (path.matches("/v1/treereports")) {
                    return new MockResponse().setResponseCode(200)
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

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.ALL)
                        .content(parameters)).andExpect(
                status().isOk());

        Message<byte[]> voltageInitStatusMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyNameUserIdUuid, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, voltageInitStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        Message<byte[]> elementUpdateMessage = output.receive(TIMEOUT, elementUpdateDestination);
        assertEquals(studyNameUserIdUuid, elementUpdateMessage.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    public void testVoltageInitParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get initial voltage init parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(VOLTAGE_INIT_EMPTY_PARAMETERS, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, VOLTAGE_INIT_PARAMETERS_JSON);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters")));

        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(VOLTAGE_INIT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID)));

        //update voltage init parameters
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, VOLTAGE_INIT_PARAMETERS_JSON);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID)));

        // insert a study with a wrong voltage init parameters uuid
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(WRONG_VOLTAGE_INIT_PARAMETERS_UUID));

        // get voltage init parameters
        mockMvc.perform(get("/v1/studies/{studyUuid}/voltage-init/parameters", studyEntity2.getId())).andExpect(
            status().isNotFound());

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + WRONG_VOLTAGE_INIT_PARAMETERS_UUID)));

    }

    @Test
    public void testVoltageInit() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID));
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

        // run a voltage init on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, rootNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isForbidden());

        //run a voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

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

        mockMvc.perform(
                post("/v1/studies/{studyUuid}/voltage-init/parameters", studyNameUserIdUuid)
                        .header("userId", "userId")
                        .contentType(MediaType.ALL)
                        .content(VOLTAGE_INIT_PARAMETERS_JSON)).andExpect(
                status().isOk());

        assertTrue(TestUtils.getRequestsDone(2, server).stream().allMatch(r -> r.matches("/v1/parameters/" + VOLTAGE_INIT_PARAMETERS_UUID) || r.matches("/v1/results/invalidate-status.*")));
        //remove notif about study updating due to parameters changes
        output.receive(1000);
        output.receive(1000);

        // stop voltage init analysis
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/stop", studyNameUserIdUuid, modificationNode3Uuid)).andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/stop\\?receiver=.*nodeUuid.*")));

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/run", studyNameUserIdUuid, modificationNode2Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID)));

        //Test result count
        testResultCount();
        //Delete Voltage init results
        testDeleteResults(1);
    }

    @Test
    public void testCopyVoltageInitModifications() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID));
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

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save\\?receiver=.*&reportUuid=.*&reporterId=.*&variantId=" + VARIANT_ID_2)));

        // just retrieve modifications list from modificationNode3Uuid
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/modifications", studyNameUserIdUuid, modificationNode3Uuid)
                .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(2, server).stream().allMatch(r ->
                r.matches("/v1/groups/" + MODIFICATIONS_GROUP_UUID + "/network-modifications\\?errorOnGroupNotFound=false&onlyStashed=false&onlyMetadata=false") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid")
        ));

        // clone and copy modifications to modificationNode3Uuid
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/voltage-init/modifications", studyNameUserIdUuid, modificationNode3Uuid)
            .header("userId", "userId")).andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(4, server).stream().allMatch(r ->
            r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/modifications-group-uuid") ||
                r.matches("/v1/results/" + VOLTAGE_INIT_RESULT_UUID + "/status") ||
                r.matches("/v1/groups/.*/duplications.*")
        ));

        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid);
        checkNodesBuildStatusUpdatedMessageReceived(studyNameUserIdUuid, List.of(modificationNode3Uuid));
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_STUDY);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.NODE_BUILD_STATUS_UPDATED);
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid);
        checkElementUpdatedMessageSent(studyNameUserIdUuid, "userId");
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
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
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID));
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId());
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId()));

        // Set an uuid result in the database
        networkModificationTreeService.updateComputationResultUuid(modificationNode.getId(), resultUuid, VOLTAGE_INITIALIZATION);
        assertTrue(networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), VOLTAGE_INITIALIZATION).isPresent());
        assertEquals(resultUuid, networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), VOLTAGE_INITIALIZATION).get());

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
        assertEquals(VOLTAGE_INIT_ERROR_RESULT_UUID, networkModificationTreeService.getComputationResultUuid(modificationNode.getId(), VOLTAGE_INITIALIZATION).get().toString());

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

    private void testResultCount() throws Exception {
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(VOLTAGE_INITIALIZATION))
                .queryParam("dryRun", String.valueOf(true)))
            .andExpect(status().isOk());
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/supervision/results-count")));
    }

    private void testDeleteResults(int expectedInitialResultCount) throws Exception {
        assertEquals(expectedInitialResultCount, networkModificationNodeInfoRepository.findAllByVoltageInitResultUuidNotNull().size());
        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", String.valueOf(VOLTAGE_INITIALIZATION))
                .queryParam("dryRun", String.valueOf(false)))
            .andExpect(status().isOk());

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.contains("/v1/results"));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/treereports")));
        assertEquals(0, networkModificationNodeInfoRepository.findAllByVoltageInitResultUuidNotNull().size());
    }

    @Test
    public void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, UUID.fromString(VOLTAGE_INIT_PARAMETERS_UUID));
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

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID voltageInitParametersUuid) {
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters(), ShortCircuitPredefinedConfiguration.ICC_MAX_WITH_NOMINAL_VOLTAGE_MAP);
        SensitivityAnalysisParametersEntity defaultSensitivityParametersEntity = SensitivityAnalysisService.toEntity(SensitivityAnalysisService.getDefaultSensitivityAnalysisParametersValues());
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", "defaultLoadflowProvider",
                UUID.randomUUID(), defaultShortCircuitParametersEntity, voltageInitParametersUuid, null, defaultSensitivityParametersEntity,
                defaultNonEvacuatedEnergyParametersEntity);
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
