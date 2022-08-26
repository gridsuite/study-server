/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.gridsuite.study.server.dto.BuildInfos;
import org.gridsuite.study.server.dto.CreatedStudyBasicInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NotificationService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.SecurityAnalysisService;
import org.gridsuite.study.server.utils.RequestWithBody;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.commons.reporter.ReporterModel;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.xml.XMLImporter;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;

import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class NetworkModificationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkModificationTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";

    private static final String POST = "POST";

    private static final String MODIFICATION_UUID = "796719f5-bd31-48be-be46-ef7b96951e32";

    private static final ReporterModel REPORT_TEST = new ReporterModel("test", "test");

    private static final String TEST_FILE = "testCase.xiidm";

    private static final long TIMEOUT = 1000;

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private ObjectMapper mapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private NetworkModificationService networkModificationService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private SecurityAnalysisService securityAnalysisService;

    @MockBean
    private NetworkStoreService networkStoreService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    //output destinations
    private String studyUpdateDestination = "study.update";

    @Before
    public void setup() throws IOException {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase",
                new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);

        initMockBeans(network);

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

     // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkModificationService.setNetworkModificationServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);
        securityAnalysisService.setSecurityAnalysisServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                Buffer body = request.getBody();

                if (path.matches("/v1/reports/.*")) {
                    return new MockResponse().setResponseCode(200)
                        .setBody(mapper.writeValueAsString(REPORT_TEST))
                        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // variant build
                    input.send(MessageBuilder.withPayload("s1,s2").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.result");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // failed build
                    input.send(MessageBuilder.withPayload("").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.failed");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/build.*") && request.getMethod().equals("POST")) {
                    // failed build
                    return new MockResponse().setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
                } else if (path.matches("/v1/build/stop.*")) {
                    // stop variant build
                    input.send(MessageBuilder.withPayload("").setHeader("receiver", "%7B%22nodeUuid%22%3A%22"
                            + request.getPath().split("%")[5].substring(4) + "%22%2C%22userId%22%3A%22userId%22%7D")
                        .build(), "build.stopped");
                    return new MockResponse().setResponseCode(200).addHeader("Content-Type",
                            "application/json; charset=utf-8");
                } else if (path.matches("/v1/groups/.*") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true") ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID) ||
                    path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)) {
                        JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s1", "s2", "s3")));
                        return new MockResponse().setResponseCode(200)
                            .setBody(new JSONArray(List.of(jsonObject)).toString())
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s4", "s5", "s6", "s7")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators[?]group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.startsWith("/v1/modifications/" + MODIFICATION_UUID + "/")) {
                    if (!"PUT".equals(request.getMethod()) || !body.peek().readUtf8().equals("bogus")) {
                        return new MockResponse().setResponseCode(200);
                    } else {
                        return new MockResponse().setResponseCode(HttpStatus.BAD_REQUEST.value());
                    }
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && POST.equals(request.getMethod())) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
                        .addHeader("Content-Type", "application/json; charset=utf-8");
                } else if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*")) {
                    JSONObject jsonObject = new JSONObject(Map.of("substationIds", List.of("s2")));
                    return new MockResponse().setResponseCode(200)
                        .setBody(new JSONArray(List.of(jsonObject)).toString())
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
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenReturn(network);
    }

    @Test
    public void testBuildFailed() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();

        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");

        testBuildFailedWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuildError() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, "UCTE");
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");

        testBuildErrorWithNodeUuid(studyUuid, modificationNode.getId());
    }

    @Test
    public void testBuild() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                modificationGroupUuid1, "variant_1", "node 1");
        UUID modificationGroupUuid2 = UUID.randomUUID();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1.getId(), modificationGroupUuid2, "variant_2", "node 2");
        UUID modificationGroupUuid3 = UUID.randomUUID();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2.getId(), modificationGroupUuid3, "variant_3", "node 3");
        UUID modificationGroupUuid4 = UUID.randomUUID();
        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode3.getId(), modificationGroupUuid4, "variant_4", "node 4");
        UUID modificationGroupUuid5 = UUID.randomUUID();
        NetworkModificationNode modificationNode5 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode4.getId(), modificationGroupUuid5, "variant_5", "node 5");

        /*
            root
             |
          modificationNode1
             |
          modificationNode2
             |
          modificationNode3
             |
          modificationNode4
             |
          modificationNode5
         */

        BuildInfos buildInfos = networkModificationTreeService.prepareBuild(modificationNode5.getId());
        assertNull(buildInfos.getOriginVariantId());  // previous built node is root node
        assertEquals("variant_5", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid1, modificationGroupUuid2, modificationGroupUuid3, modificationGroupUuid4, modificationGroupUuid5), buildInfos.getModificationGroupUuids());

        modificationNode3.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT);

        buildInfos = networkModificationTreeService.prepareBuild(modificationNode4.getId());
        assertEquals("variant_3", buildInfos.getOriginVariantId()); // variant to clone is variant associated to node
                                                                    // modificationNode3
        assertEquals("variant_4", buildInfos.getDestinationVariantId());
        assertEquals(List.of(modificationGroupUuid4), buildInfos.getModificationGroupUuids());

        modificationNode2.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode2 as not built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT);
        modificationNode4.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode4 as built invalid
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode4);
        output.receive(TIMEOUT);
        modificationNode5.setBuildStatus(BuildStatus.BUILT);  // mark node modificationNode5 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode5);
        output.receive(TIMEOUT);

        // build modificationNode2 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode2.getId(), 2);

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode3.getId()));
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));

        modificationNode3.setBuildStatus(BuildStatus.NOT_BUILT);  // mark node modificationNode3 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode3);
        output.receive(TIMEOUT);

        // build modificationNode3 and stop build
        testBuildWithNodeUuid(studyNameUserIdUuid, modificationNode3.getId(), 3);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode4.getId()));
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode5.getId()));
    }

    @Test
    public void testNetworkModificationSwitch() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        // update switch on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                studyNameUserIdUuid, rootNodeUuid, "switchId")).andExpect(status().isForbidden());

        // update switch on first modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                studyNameUserIdUuid, modificationNode1Uuid, "switchId")).andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s1", "s2", "s3");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        Set<RequestWithBody> requests = TestUtils.getRequestsWithBodyDone(1, server);

        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));

        mvcResult = mockMvc.perform(get("/v1/studies").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResult = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResult.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update switch on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                        studyNameUserIdUuid, modificationNode2Uuid, "switchId")).andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkSwitchModificationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        requests = TestUtils.getRequestsWithBodyDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID_2)));

        // test build status on switch modification
        modificationNode1.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode1 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        modificationNode2.setBuildStatus(BuildStatus.BUILT);  // mark modificationNode2 as built
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode2);
        output.receive(TIMEOUT);

        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/switches/{switchId}?open=true",
                        studyNameUserIdUuid, modificationNode1Uuid, "switchId")).andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        output.receive(TIMEOUT);
        try {
            output.receive(TIMEOUT);
        } catch (Exception e) {
            throw e;
        }
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/switches/switchId\\?group=.*&open=true&variantId=" + VARIANT_ID)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/reports/.*")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(modificationNode1Uuid)); // modificationNode1
                                                                                                               // is
                                                                                                               // still
                                                                                                               // built
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(modificationNode2Uuid)); // modificationNode2
                                                                                                                       // is
                                                                                                                       // now
                                                                                                                       // invalid
    }

    @Test
    public void testNetworkModificationEquipment() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNodeUuid, VARIANT_ID_2, "node 2");
        UUID modificationNodeUuid2 = modificationNode2.getId();

        //update equipment on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                rootNodeUuid).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isForbidden());

        //update equipment
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                        modificationNodeUuid).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isOk());

        Set<String> substationsSet = ImmutableSet.of("s4", "s5", "s6", "s7");
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
                .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*")));

        mvcResult = mockMvc.perform(get("/v1/studies").header("userId", "userId").header("userId", "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<List<CreatedStudyBasicInfos>>() { });

        assertThat(csbiListResponse.get(0), createMatcherCreatedStudyBasicInfos(studyNameUserIdUuid, "userId", "UCTE"));

        // update equipment on second modification node
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/groovy", studyNameUserIdUuid,
                        modificationNodeUuid2).content("equipment = network.getGenerator('idGen')\nequipment.setTargetP('42')"))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);
        checkEquipmentMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS, substationsSet);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNodeUuid2);

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/groovy\\?group=.*&variantId=" + VARIANT_ID_2)));
    }

    @Test
    public void testCreateGenerator() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createGeneratorAttributes = "{\"generatorId\":\"generatorId1\",\"generatorName\":\"generatorName1\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create generator on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, rootNodeUuid).content(createGeneratorAttributes))
                .andExpect(status().isForbidden());

        // create generator on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createGeneratorAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create generator on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createGeneratorAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update generator creation
        String generatorAttributesUpdated = "{\"generatorId\":\"generatorId2\",\"generatorName\":\"generatorName2\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"150.0\",\"maxActivePower\":\"50.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/generators-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(generatorAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createGeneratorAttributes2 = "{\"generatorId\":\"generatorId3\",\"generatorName\":\"generatorName3\",\"energySource\":\"UNDEFINED\",\"minActivePower\":\"100.0\",\"maxActivePower\":\"200.0\",\"ratedNominalPower\":\"50.0\",\"activePowerSetpoint\":\"10.0\",\"reactivePowerSetpoint\":\"20.0\",\"voltageRegulatorOn\":\"true\",\"voltageSetpoint\":\"225.0\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        // create generator on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/generators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createGeneratorAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*") && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/generators\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createGeneratorAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/generators-creation") && r.getBody().equals(generatorAttributesUpdated)));
    }

    @Test
    public void testCreateShuntsCompensator() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        String createShuntCompensatorAttributes = "{\"shuntCompensatorId\":\"shuntCompensatorId1\",\"shuntCompensatorName\":\"shuntCompensatorName1\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";

        // create shuntCompensator on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, rootNodeUuid).content(createShuntCompensatorAttributes))
            .andExpect(status().isForbidden());

        // create shuntCompensator on modification node child of root node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createShuntCompensatorAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // update shunt compensator creation
        String shuntCompensatorAttributesUpdated = "{\"shuntCompensatorId\":\"shuntCompensatorId2\",\"shuntCompensatorName\":\"shuntCompensatorName2\",\"voltageLevelId\":\"idVL2\",\"busOrBusbarSectionId\":\"idBus1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/shunt-compensators-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(shuntCompensatorAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createShuntCompensatorAttributes2 = "{\"shuntCompensatorId\":\"shuntCompensatorId3\",\"shuntCompensatorName\":\"shuntCompensatorName3\",\"voltageLevelId\":\"idVL1\",\"busOrBusbarSectionId\":\"idBus1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        // create shunt compensator on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/shunt-compensators",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createShuntCompensatorAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(2, server);
        assertTrue(requests.stream()
                .anyMatch(r -> r
                        .getPath().matches("/v1/networks/" + NETWORK_UUID_STRING
                                + "/shunt-compensators[?]group=.*&variantId=" + VARIANT_ID)
                        && r.getBody().equals(createShuntCompensatorAttributes)));
        assertTrue(requests.stream().anyMatch(
            r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/shunt-compensators-creation")
                        && r.getBody().equals(shuntCompensatorAttributesUpdated)));
    }

    @Test
    public void testCreateLine() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createLineAttributes = "{" + "\"lineId\":\"lineId1\"," + "\"lineName\":\"lineName1\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";

        // create line on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, rootNodeUuid).content(createLineAttributes))
                .andExpect(status().isForbidden());

        // create line on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, modificationNode1Uuid).content(createLineAttributes))
                .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create line on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                studyNameUserIdUuid, modificationNode2Uuid).content(createLineAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update line creation
        String lineAttributesUpdated = "{" + "\"lineId\":\"lineId2\"," + "\"lineName\":\"lineName2\","
                + "\"seriesResistance\":\"54.0\"," + "\"seriesReactance\":\"55.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL2\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/lines-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(lineAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createLineAttributes2 = "{" + "\"lineId\":\"lineId3\"," + "\"lineName\":\"lineName3\","
                + "\"seriesResistance\":\"50.0\"," + "\"seriesReactance\":\"50.0\","
                + "\"shuntConductance1\":\"100.0\"," + "\"shuntSusceptance1\":\"100.0\","
                + "\"shuntConductance2\":\"200.0\"," + "\"shuntSusceptance2\":\"200.0\","
                + "\"voltageLevelId1\":\"idVL1\"," + "\"busOrBusbarSectionId1\":\"idBus1\","
                + "\"voltageLevelId2\":\"idVL2\"," + "\"busOrBusbarSectionId2\":\"idBus2\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        // create line on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/lines",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createLineAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*") && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/lines\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createLineAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/lines-creation") && r.getBody().equals(lineAttributesUpdated)));
    }

    @Test
    public void testCreateTwoWindingsTransformer() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, "UCTE");
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        String createTwoWindingsTransformerAttributes = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        // create 2WT on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, rootNodeUuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isForbidden());

        // create 2WT on first modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        // create 2WT on second modification node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode2Uuid).content(createTwoWindingsTransformerAttributes))
            .andExpect(status().isOk());
        checkEquipmentCreatingMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);
        checkEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid, ImmutableSet.of("s2"));
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode2Uuid);

        // update Two Windings Transformer creation
        String twoWindingsTransformerAttributesUpdated = "{\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/modifications/{modificationUuid}/two-windings-transformers-creation",
                studyNameUserIdUuid, modificationNode1Uuid, MODIFICATION_UUID).content(twoWindingsTransformerAttributesUpdated))
            .andExpect(status().isOk());
        checkEquipmentUpdatingMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkUpdateEquipmentCreationMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);
        checkEquipmentUpdatingFinishedMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        String createTwoWindingsTransformerAttributes2 = "{\"equipmentId\":\"2wtId3\",\"equipmentName\":\"2wtName3\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        modificationNode1.setBuildStatus(BuildStatus.BUILDING);
        networkModificationTreeService.updateNode(studyNameUserIdUuid, modificationNode1);
        output.receive(TIMEOUT);
        // create Two Windings Transformer on building node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modification/two-windings-transformers",
                        studyNameUserIdUuid, modificationNode1Uuid).content(createTwoWindingsTransformerAttributes2))
                .andExpect(status().isForbidden());

        var requests = TestUtils.getRequestsWithBodyDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*") && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/networks/" + NETWORK_UUID_STRING + "/two-windings-transformers\\?group=.*&variantId=" + VARIANT_ID_2) && r.getBody().equals(createTwoWindingsTransformerAttributes)));
        assertTrue(requests.stream().anyMatch(r -> r.getPath().matches("/v1/modifications/" + MODIFICATION_UUID + "/two-windings-transformers-creation") && r.getBody().equals(twoWindingsTransformerAttributesUpdated)));

    }

    private void testBuildWithNodeUuid(UUID studyUuid, UUID nodeUuid, int nbReportExpected) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        // Initial node update -> BUILDING
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // Successful ->  Node update -> BUILT
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_COMPLETED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(Set.of("s1", "s2"), buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS));

        assertTrue(TestUtils.getRequestsDone(nbReportExpected, server).stream().allMatch(r -> r.contains("reports")));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is built

        networkModificationTreeService.updateBuildStatus(nodeUuid, BuildStatus.BUILDING);

        // stop build
        mockMvc.perform(put("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build/stop", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        output.receive(TIMEOUT);
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_CANCELLED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid)); // node is not
                                                                                                      // built

        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/build/stop\\?receiver=.*")));
    }

    // builds on network 2 will fail
    private void testBuildFailedWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isOk());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // fail -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error message sent to frontend
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_BUILD_FAILED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertTrue(TestUtils.getRequestsDone(1, server).iterator().next().contains("reports"));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_2_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is not built
    }

    // builds on network 3 will throw an error on networkmodificationservice call
    private void testBuildErrorWithNodeUuid(UUID studyUuid, UUID nodeUuid) throws Exception {
        // build node
        mockMvc.perform(post("/v1/studies/{studyUuid}/nodes/{nodeUuid}/build", studyUuid, nodeUuid))
            .andExpect(status().isInternalServerError());

        // initial node update -> building
        Message<byte[]> buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        // error -> second node update -> not built
        buildStatusMessage = output.receive(TIMEOUT);
        assertEquals(studyUuid, buildStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_UPDATED, buildStatusMessage.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertTrue(TestUtils.getRequestsDone(1, server).iterator().next().contains("reports"));
        assertTrue(TestUtils.getRequestsDone(1, server).stream()
            .anyMatch(r -> r.matches("/v1/networks/" + NETWORK_UUID_3_STRING + "/build\\?receiver=.*")));

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getBuildStatus(nodeUuid));  // node is not built
    }

    private void checkEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedIdsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedIdsSet);
    }

    private void checkEquipmentCreatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
    }

    private void checkUpdateNodesMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodesUuids, headersStatus.get(NotificationService.HEADER_NODES));
        assertEquals(NotificationService.NODE_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid, String headerUpdateTypeId,
            Set<String> modifiedIdsSet) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(modifiedIdsSet, headersStudyUpdate.get(headerUpdateTypeId));

        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkSwitchModificationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid,
            Set<String> modifiedSubstationsSet) {
        checkEquipmentMessagesReceived(studyNameUserIdUuid, nodeUuid, NotificationService.HEADER_UPDATE_TYPE_SUBSTATIONS_IDS,
                modifiedSubstationsSet);

        // assert that the broker message has been sent
        Message<byte[]> messageSwitch = output.receive(TIMEOUT);
        assertEquals("", new String(messageSwitch.getPayload()));
        MessageHeaders headersSwitch = messageSwitch.getHeaders();
        assertEquals(studyNameUserIdUuid, headersSwitch.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersSwitch.get(NotificationService.HEADER_NODE));
        assertEquals(NotificationService.UPDATE_TYPE_SWITCH, headersSwitch.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkEquipmentUpdatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkUpdateEquipmentCreationMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        checkUpdateNodesMessageReceived(studyNameUserIdUuid, List.of(nodeUuid));
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, nodeUuid);
    }

    private void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, String caseFormat) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
                .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
                .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
                .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
                .readSlackBus(true)
                .distributedSlack(true)
                .dcUseTransformerRatio(true)
                .hvdcAcEmulation(true)
                .build();
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, caseFormat, defaultLoadflowProvider, defaultLoadflowParametersEntity);
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

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, UUID.randomUUID(), variantId, nodeName);
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

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId(), null));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);

        cleanDB();

        TestUtils.assertQueuesEmpty(destinations, output);

        try {
            TestUtils.assertServerRequestsEmpty(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
