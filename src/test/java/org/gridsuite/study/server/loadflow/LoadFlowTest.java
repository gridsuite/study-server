/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.loadflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.security.LimitViolationType;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeBuildStatusEmbeddable;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.LOAD_FLOW;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.gridsuite.study.server.utils.TestUtils.USER_DEFAULT_PROFILE_JSON;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria.removeRequestMatching;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Anis Touri <anis.touri at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class LoadFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadFlowTest.class);

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";

    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String LOADFLOW_RESULT_UUID = "1b6cc22c-3f33-11ed-b878-0242ac120002";

    private static final String LOADFLOW_ERROR_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";

    private static final String LOADFLOW_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";

    private static final String LOADFLOW_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";

    private static final String PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"loadFlowParameterId\":\"" + PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"loadFlowParameterId\":\"" + PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";

    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString(LOADFLOW_PARAMETERS_UUID_STRING);

    private static final String PROVIDER = "LF_PROVIDER";

    private static final String VARIANT_ID = "variant_1";

    private static final String VARIANT_ID_2 = "variant_2";

    private static final long TIMEOUT = 1000;

    private static final String USER_ID_HEADER = "userId";

    private static final String DEFAULT_PROVIDER = "defaultProvider";
    private static final String OTHER_PROVIDER = "otherProvider";

    private static String LIMIT_VIOLATIONS_JSON;

    private static String COMPUTING_STATUS_JSON;

    private static String LOADFLOW_DEFAULT_PARAMETERS_JSON;
    private static String LOADFLOW_PROFILE_PARAMETERS_JSON;

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String LOADFLOW_RESULT_DESTINATION = "loadflow.result";
    private static final String LOADFLOW_STOPPED_DESTINATION = "loadflow.stopped";
    private static final String LOADFLOW_FAILED_DESTINATION = "loadflow.run.dlx";
    private static final String LOADFLOW_MODIFICATIONS = "loadflow modifications mock";

    @Autowired
    private MockMvc mockMvc;
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
    private LoadFlowService loadFlowService;
    @Autowired
    private StudyRepository studyRepository;
    @MockitoSpyBean
    private UserAdminService userAdminService;
    @Autowired
    private ReportService reportService;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @MockitoSpyBean
    private StudyService studyService;
    @MockitoBean
    private NetworkModificationService networkModificationService;
    @MockitoBean
    private NetworkService networkService;
    @Autowired
    private TestUtils studyTestUtils;
    @Autowired
    private ConsumerService consumerService;

    private static WireMockServer wireMockServer;
    private WireMockStubs wireMockStubs;

    @BeforeAll
    static void initWireMock(@Autowired InputDestination input) {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockServer.start();
    }

    @BeforeEach
    void setup() throws Exception {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        wireMockStubs = new WireMockStubs(wireMockServer);
        reportService.setReportServerBaseUri(wireMockServer.baseUrl());
        userAdminService.setUserAdminServerBaseUri(wireMockServer.baseUrl());
        loadFlowService.setLoadFlowServerBaseUri(wireMockServer.baseUrl());
        networkModificationService.setNetworkModificationServerBaseUri(wireMockServer.baseUrl());

        List<LimitViolationInfos> limitViolations = List.of(LimitViolationInfos.builder()
                        .subjectId("lineId2")
                        .limit(100.)
                        .limitName("lineName2")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(300)
                        .overload(null)
                        .value(80.)
                        .side(TwoSides.TWO.name())
                        .limitType(LimitViolationType.CURRENT)
                        .build(),
                LimitViolationInfos.builder()
                        .subjectId("lineId1")
                        .limit(200.)
                        .limitName("lineName1")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(60)
                        .overload(null)
                        .value(150.0)
                        .side(TwoSides.ONE.name())
                        .limitType(LimitViolationType.CURRENT)
                        .build(),
                LimitViolationInfos.builder()
                        .subjectId("genId1")
                        .limit(500.)
                        .limitName("genName1")
                        .actualOverloadDuration(null)
                        .upComingOverloadDuration(null)
                        .overload(null)
                        .value(370.)
                        .side(null)
                        .limitType(LimitViolationType.HIGH_VOLTAGE)
                        .build());
        LIMIT_VIOLATIONS_JSON = objectMapper.writeValueAsString(limitViolations);
        COMPUTING_STATUS_JSON = objectMapper.writeValueAsString(List.of("CONVERGED", "FAILED"));

        LoadFlowParametersInfos loadFlowParametersInfos = LoadFlowParametersInfos.builder()
                .provider(PROVIDER)
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build();
        LOADFLOW_DEFAULT_PARAMETERS_JSON = objectMapper.writeValueAsString(loadFlowParametersInfos);
        LoadFlowParametersInfos profileLoadFlowParametersInfos = LoadFlowParametersInfos.builder()
                .provider(OTHER_PROVIDER)
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build();
        LOADFLOW_PROFILE_PARAMETERS_JSON = objectMapper.writeValueAsString(profileLoadFlowParametersInfos);

    }

    private void assertNodeBlocked(UUID nodeUuid, UUID rootNetworkUuid, boolean isNodeBlocked) {
        Optional<RootNetworkNodeInfoEntity> networkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid);
        assertTrue(networkNodeInfoEntity.isPresent());
        assertEquals(isNodeBlocked, networkNodeInfoEntity.get().getBlockedNode());
    }

    private void consumeLoadFlowResult(UUID studyUuid, UUID rootNetworkUuid, NetworkModificationNode modificationNode) throws JsonProcessingException {
        UUID nodeUuid = modificationNode.getId();

        assertNodeBlocked(nodeUuid, rootNetworkUuid, true);
        doNothing().when(studyService).buildFirstLevelChildren(studyUuid, nodeUuid, rootNetworkUuid, "userId");

        // consume loadflow result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        if (modificationNode.isSecurityNode()) {
            wireMockStubs.loadflowServer.stubGetLoadflowStatus(UUID.fromString(LOADFLOW_RESULT_UUID), objectMapper.writeValueAsString(LoadFlowStatus.CONVERGED), false);
        }
        MessageHeaders messageHeaders = new MessageHeaders(
            Map.of(
                "resultUuid", LOADFLOW_RESULT_UUID,
                "withRatioTapChangers", false,
                HEADER_RECEIVER, resultUuidJson,
                USER_ID_HEADER, "userId"));
        consumerService.consumeLoadFlowResult().accept(MessageBuilder.createMessage("", messageHeaders));
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);

        assertNodeBlocked(nodeUuid, rootNetworkUuid, false);
        if (modificationNode.isSecurityNode()) {
            // if running successful loadflow on security node -> first children are built
            wireMockStubs.loadflowServer.verifyGetLoadflowStatus(UUID.fromString(LOADFLOW_RESULT_UUID));
            verify(studyService, times(1)).buildFirstLevelChildren(studyUuid, nodeUuid, rootNetworkUuid, "userId");
        }
    }

    @Test
    void testLoadFlow() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationConstructionNode(studyNameUserIdUuid, rootNodeUuid,
            UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationConstructionNode(studyNameUserIdUuid,
            modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationConstructionNode(studyNameUserIdUuid,
            modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3");

        NetworkModificationNode modificationNode4 = createNetworkModificationNode(studyNameUserIdUuid,
            modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 4", NetworkModificationNodeType.SECURITY);

        // run a loadflow on root node (not allowed)
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, UUID.randomUUID(), rootNodeUuid)
                .header("userId", "userId"))
            .andExpect(status().isForbidden());

        // run LF with failed status
        testLoadFlowFailed(studyNameUserIdUuid, modificationNode2.getId());

        // run LF with construction node
        testLoadFlow(studyNameUserIdUuid, modificationNode3);

        // run LF with security node
        testLoadFlow(studyNameUserIdUuid, modificationNode4);
    }

    private void testLoadFlow(UUID studyNameUserIdUuid, NetworkModificationNode modificationNode) throws Exception {
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID modificationNodeUuid = modificationNode.getId();
        UUID networkUuid = studyTestUtils.getNetworkUuid(studyNameUserIdUuid);
        wireMockStubs.loadflowServer.stubGetLoadflowProvider(LOADFLOW_PARAMETERS_UUID.toString(), DEFAULT_PROVIDER);
        wireMockStubs.loadflowServer.stubRunLoadflow(networkUuid, objectMapper.writeValueAsString(LOADFLOW_ERROR_RESULT_UUID));

        // run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)
                .header("userId", "userId"))
            .andExpect(status().isOk());

        wireMockStubs.loadflowServer.verifyRunLoadflow(networkUuid);

        if (!modificationNode.isSecurityNode()) {
            wireMockStubs.loadflowServer.verifyGetLoadflowProvider(LOADFLOW_PARAMETERS_UUID.toString());
        } else {
            // running loadflow invalidate node children and their computations
            checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNodeUuid);
            RequestPatternBuilder getLoadflowProviderRequestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID.toString() + "/provider"));
            removeRequestMatching(wireMockServer, getLoadflowProviderRequestBuilder, 0);
        }

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        consumeLoadFlowResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode);

        UUID loadflowResultUuid = rootNetworkNodeInfoService.getComputationResultUuid(modificationNodeUuid, firstRootNetworkUuid, LOAD_FLOW);
        wireMockStubs.loadflowServer.stubGetLoadflowResult(loadflowResultUuid, TestUtils.resourceToString("/loadflow-result.json"));

        // get loadflow result
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)).andExpectAll(
            status().isOk()).andReturn();

        assertEquals(TestUtils.resourceToString("/loadflow-result.json"), mvcResult.getResponse().getContentAsString());

        wireMockStubs.loadflowServer.verifyGetLoadflowResult(loadflowResultUuid);

        // get loadflow status
        wireMockStubs.loadflowServer.stubGetLoadflowStatus(loadflowResultUuid, objectMapper.writeValueAsString(LoadFlowStatus.CONVERGED), false);
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/status?withRatioTapChangers={withRatioTapChangers}", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, false))
            .andExpect(status().isOk())
            .andReturn();
        assertEquals(LoadFlowStatus.CONVERGED.name(), mvcResult.getResponse().getContentAsString());
        wireMockStubs.loadflowServer.verifyGetLoadflowStatus(loadflowResultUuid);

        // stop loadflow
        wireMockStubs.loadflowServer.stubStopLoadflow(loadflowResultUuid, modificationNodeUuid, firstRootNetworkUuid, objectMapper.writeValueAsString(LoadFlowStatus.CONVERGED));
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/stop?withRatioTapChangers={withRatioTapChangers}", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, false)
                .header(HEADER_USER_ID, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.loadflowServer.verifyStopLoadflow(loadflowResultUuid);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT);
    }

    private void testLoadFlowFailed(UUID studyNameUserIdUuid, UUID modificationNodeUuid) throws Exception {
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID networkUuid = studyTestUtils.getNetworkUuid(studyNameUserIdUuid);
        wireMockStubs.loadflowServer.stubGetLoadflowProvider(LOADFLOW_PARAMETERS_UUID.toString(), DEFAULT_PROVIDER);
        wireMockStubs.loadflowServer.stubRunLoadflowFailed(networkUuid, modificationNodeUuid, objectMapper.writeValueAsString(LOADFLOW_ERROR_RESULT_UUID));

        // loadflow failed
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());

        wireMockStubs.loadflowServer.verifyGetLoadflowProvider(LOADFLOW_PARAMETERS_UUID.toString());
        wireMockStubs.loadflowServer.verifyRunLoadflow(networkUuid);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_FAILED);
    }

    @Test
    void testGetLimitViolations() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 1", NetworkModificationNodeType.SECURITY);
        UUID modificationNode1Uuid = modificationNode1.getId();
        UUID networkUuid = studyTestUtils.getNetworkUuid(studyNameUserIdUuid);

        wireMockStubs.loadflowServer.stubRunLoadflow(networkUuid, objectMapper.writeValueAsString(LOADFLOW_RESULT_UUID));
        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.loadflowServer.verifyRunLoadflow(networkUuid);

        // running loadflow (with security node) invalidate node children and their computations
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        consumeLoadFlowResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1);

        wireMockStubs.loadflowServer.stubGetComputationStatus(LOADFLOW_RESULT_UUID, COMPUTING_STATUS_JSON);
        // get computing status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, LOAD_FLOW, "computation-status"))
                .andExpectAll(status().isOk(),
                        content().string(COMPUTING_STATUS_JSON));
        wireMockStubs.loadflowServer.verifyGetComputationStatus(LOADFLOW_RESULT_UUID);

        wireMockStubs.loadflowServer.stubGetComputation(LOADFLOW_RESULT_UUID);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/computation/result/enum-values?computingType={computingType}&enumName={enumName}",
                        studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, LOAD_FLOW, "computation")).andReturn();
        wireMockStubs.loadflowServer.verifyGetComputation(LOADFLOW_RESULT_UUID);

        wireMockStubs.loadflowServer.stubGetLimitViolation(LOADFLOW_RESULT_UUID, LIMIT_VIOLATIONS_JSON, false);
        // get limit violations
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/limit-violations", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(LIMIT_VIOLATIONS_JSON));
        wireMockStubs.loadflowServer.verifyGetLimitViolation(LOADFLOW_RESULT_UUID, false);

        wireMockStubs.loadflowServer.stubGetLimitViolation(LOADFLOW_RESULT_UUID, LIMIT_VIOLATIONS_JSON, true);
        // get limit violations with filters, globalFilters and sort
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/limit-violations?filters=lineId2&sort=subjectId,ASC&globalFilters=ss", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isOk(),
                content().string(LIMIT_VIOLATIONS_JSON));
        wireMockStubs.loadflowServer.verifyGetLimitViolation(LOADFLOW_RESULT_UUID, true);

        // get limit violations on non existing node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/limit-violations", studyNameUserIdUuid, firstRootNetworkUuid, UUID.randomUUID())).andExpectAll(
                status().isNotFound());
    }

    @Test
    void testInvalidateStatus() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID networkUuid = studyTestUtils.getNetworkUuid(studyNameUserIdUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID_2, "node 1", NetworkModificationNodeType.SECURITY);
        UUID modificationNode1Uuid = modificationNode1.getId();

        wireMockStubs.loadflowServer.stubRunLoadflow(networkUuid, objectMapper.writeValueAsString(LOADFLOW_RESULT_UUID));
        // run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.loadflowServer.verifyRunLoadflow(networkUuid);

        // running loadflow (with security node) invalidate children and their computations
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode1Uuid);

        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);

        consumeLoadFlowResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1);
    }

    @Test
    void testDeleteLoadFlowResults() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID networkUuid = studyTestUtils.getNetworkUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationConstructionNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        NetworkModificationNode modificationNode2 = createNetworkModificationConstructionNode(studyNameUserIdUuid,
                modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();

        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid,
                modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 3", NetworkModificationNodeType.SECURITY);
        UUID modificationNode3Uuid = modificationNode3.getId();

        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity3 = rootNetworkNodeInfoService.getRootNetworkNodeInfo(modificationNode3Uuid, firstRootNetworkUuid).orElseThrow();
        rootNetworkNodeInfoEntity3.setNodeBuildStatus(NodeBuildStatusEmbeddable.from(BuildStatus.BUILT));
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity3);

        wireMockStubs.loadflowServer.stubRunLoadflow(networkUuid, objectMapper.writeValueAsString(LOADFLOW_RESULT_UUID));
        //run a loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/run", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid)
                        .header("userId", "userId"))
                .andExpect(status().isOk());
        wireMockStubs.loadflowServer.verifyRunLoadflow(networkUuid);

        // running loadflow ((with security node)) invalidate node children and their computations
        checkUpdateModelsStatusMessagesReceived(studyNameUserIdUuid, modificationNode3Uuid);
        checkUpdateModelStatusMessagesReceived(studyNameUserIdUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        consumeLoadFlowResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3);

        //Test result count
        testResultCount();

        //Delete Voltage init results
        testDeleteResults(studyNameUserIdUuid, 1);
    }

    @Test
    void testResetUuidResultWhenLFFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), LOADFLOW_PARAMETERS_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationConstructionNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), rootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateLoadflowResultUuid(modificationNode.getId(), rootNetworkUuid, resultUuid, false);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));

        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), LOADFLOW_FAILED_DESTINATION);
            return resultUuid;
        }).when(studyService).rerunLoadflow(any(), any(), any(), any(), any(), any());
        studyService.rerunLoadflow(studyEntity.getId(), modificationNode.getId(), rootNetworkUuid, resultUuid, true, "");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), rootNetworkUuid, LOAD_FLOW));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);

        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_FAILED, updateType);
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck, String otherUpdateTypeToCheck) {
        Message<byte[]> loadFlowStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, loadFlowStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) loadFlowStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        if (otherUpdateTypeToCheck == null) {
            assertEquals(updateTypeToCheck, updateType);
        } else {
            assertTrue(updateType.equals(updateTypeToCheck) || updateType.equals(otherUpdateTypeToCheck));
        }
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        checkUpdateModelStatusMessagesReceived(studyUuid, updateTypeToCheck, null);
    }

    private void testResultCount() throws Exception {
        wireMockStubs.loadflowServer.stubGetResultsCount(objectMapper.writeValueAsString(1));
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", LOAD_FLOW.toString())
                        .queryParam("dryRun", "true"))
                .andExpect(status().isOk());
        wireMockStubs.loadflowServer.verifyGetResultsCount();
    }

    private void testDeleteResults(UUID studyUuid, int expectedInitialResultCount) throws Exception {
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoEntities.getFirst();

        assertEquals(expectedInitialResultCount, rootNetworkNodeInfoEntities.size());
        wireMockStubs.loadflowServer.stubDeleteLoadflowResults(LOADFLOW_RESULT_UUID);
        wireMockStubs.reportServer.stubDeleteReport();
        mockMvc.perform(delete("/v1/supervision/computation/results")
                        .queryParam("type", LOAD_FLOW.toString())
                        .queryParam("dryRun", "false"))
                .andExpect(status().isOk());
        wireMockStubs.loadflowServer.verifyDeleteLoadflowResults();
        wireMockStubs.reportServer.verifyDeleteReport();

        assertEquals(0, rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull().size());

        checkUpdateModelsStatusMessagesReceived(studyUuid);

        assertEquals(BuildStatus.NOT_BUILT, rootNetworkNodeInfoRepository.findById(rootNetworkNodeInfoEntity.getId()).orElseThrow().getNodeBuildStatus().getLocalBuildStatus());
    }

    @Test
    void testNoResult() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode modificationNode1 = createNetworkModificationConstructionNode(studyNameUserIdUuid, rootNodeUuid,
                UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // No loadflow result
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/result", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid)).andExpectAll(
                status().isNoContent());

        // No loadflow status
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/status?withRatioTapChangers={withRatioTapChangers}", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, false)).andExpectAll(
                status().isNoContent());

        // stop non-existing loadflow
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/stop?withRatioTapChangers={withRatioTapChangers}", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, false)
                .header(HEADER_USER_ID, "userId")).andExpect(status().isOk());
    }

    private void updateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String loadflowParametersUuid, String userId, HttpStatusCode status, String returnedUserProfileJson, boolean shouldDuplicate, String duplicateFromUuid, boolean duplicateIsNotFound) throws Exception {
        wireMockStubs.loadflowServer.stubPutLoadflowParameters(loadflowParametersUuid, parameters);
        UUID duplicatedLoadflowParametersUuid = UUID.randomUUID();
        if (parameters == null || parameters.isEmpty()) {
            wireMockStubs.userAdminServer.stubGetUserProfile(userId, returnedUserProfileJson);
        }
        if (shouldDuplicate) {
            wireMockStubs.loadflowServer.stubDuplicateLoadflowParameters(duplicateFromUuid, objectMapper.writeValueAsString(duplicatedLoadflowParametersUuid), duplicateIsNotFound);
        }
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters == null ? "" : parameters))
                .andExpect(status().is(status.value()));
        wireMockStubs.loadflowServer.verifyPutLoadflowParameters(loadflowParametersUuid, parameters);
        if (parameters == null || parameters.isEmpty()) {
            wireMockStubs.userAdminServer.verifyGetUserProfile(userId);
        }
        if (shouldDuplicate) {
            wireMockStubs.loadflowServer.verifyDuplicateLoadflowParameters(duplicateFromUuid);
        }
        testMessages(studyNameUserIdUuid);
    }

    private void updateParametersAndDoChecksForResetLoadFlowParameters(UUID studyNameUserIdUuid, String loadflowParametersUuid, String userId, String returnedUserProfileJson, String duplicateFromUuid, String returnedLoadFlowParameters) throws Exception {
        UUID duplicatedLoadflowParametersUuid = UUID.randomUUID();
        wireMockStubs.userAdminServer.stubGetUserProfile(userId, returnedUserProfileJson);
        wireMockStubs.loadflowServer.stubDuplicateLoadflowParameters(duplicateFromUuid, objectMapper.writeValueAsString(duplicatedLoadflowParametersUuid), false);
        wireMockStubs.loadflowServer.stubGetLoadflowParameters(loadflowParametersUuid, returnedLoadFlowParameters, false);
        wireMockStubs.loadflowServer.stubPutLoadflowProvider(duplicatedLoadflowParametersUuid.toString(), PROVIDER);
        wireMockStubs.loadflowServer.stubDeleteLoadFlowParameters(loadflowParametersUuid);

        mockMvc.perform(
                        post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                                .header("userId", userId)
                                .contentType(MediaType.ALL)
                                .content(""))
                .andExpect(status().is(HttpStatus.OK.value()));
        wireMockStubs.userAdminServer.verifyGetUserProfile(userId);
        wireMockStubs.loadflowServer.verifyDuplicateLoadflowParameters(duplicateFromUuid);
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(loadflowParametersUuid);
        wireMockStubs.loadflowServer.verifyPutLoadflowProvider(duplicatedLoadflowParametersUuid.toString());
        wireMockStubs.loadflowServer.verifyDeleteLoadFlowParameters(loadflowParametersUuid);

        testMessages(studyNameUserIdUuid);
    }

    private void createParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, String returnedUserProfileJson, boolean shouldDuplicate, String duplicateFromUuid) throws Exception {
        String createdLoadflowParametersUuid = UUID.randomUUID().toString();
        wireMockStubs.loadflowServer.stubCreateLoadflowParameters(objectMapper.writeValueAsString(createdLoadflowParametersUuid));
        if (parameters == null || parameters.isEmpty()) {
            wireMockStubs.userAdminServer.stubGetUserProfile(userId, returnedUserProfileJson);
        }
        if (shouldDuplicate) {
            wireMockStubs.loadflowServer.stubDuplicateLoadflowParameters(duplicateFromUuid, objectMapper.writeValueAsString(UUID.randomUUID()), false);
        }
        mockMvc.perform(
                        post("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)
                                .header("userId", userId)
                                .contentType(MediaType.ALL)
                                .content(parameters == null ? "" : parameters))
                .andExpect(status().is(HttpStatus.OK.value()));
        if (parameters == null || parameters.isEmpty()) {
            wireMockStubs.userAdminServer.verifyGetUserProfile(userId);
        }
        if (shouldDuplicate) {
            wireMockStubs.loadflowServer.verifyDuplicateLoadflowParameters(duplicateFromUuid);
        } else {
            wireMockStubs.loadflowServer.verifyCreateLoadflowParameters();
        }
        testMessages(studyNameUserIdUuid);
    }

    private void testMessages(UUID studyNameUserIdUuid) {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testResetLoadFlowParametersUserHasNoProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        updateParametersAndDoChecks(studyNameUserIdUuid, "", LOADFLOW_PARAMETERS_UUID_STRING, NO_PROFILE_USER_ID, HttpStatus.OK, USER_DEFAULT_PROFILE_JSON, false, null, false);
    }

    @Test
    void testResetLoadFlowParametersUserHasNoParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        updateParametersAndDoChecks(studyNameUserIdUuid, "", LOADFLOW_PARAMETERS_UUID_STRING, NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK, USER_PROFILE_NO_PARAMS_JSON, false, null, false);
    }

    @Test
    void testResetLoadFlowParametersUserHasInvalidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        updateParametersAndDoChecks(studyNameUserIdUuid, null, LOADFLOW_PARAMETERS_UUID_STRING, INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT, USER_PROFILE_INVALID_PARAMS_JSON, true, PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING, true);
    }

    @Test
    void testResetLoadFlowParametersUserHasValidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        updateParametersAndDoChecksForResetLoadFlowParameters(studyNameUserIdUuid, LOADFLOW_PARAMETERS_UUID_STRING, VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING, LOADFLOW_DEFAULT_PARAMETERS_JSON);
    }

    @Test
    void testResetLoadFlowParametersUserHasValidParamsInProfileButNoExistingLoadflowParams() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        createParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, true, PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING);
    }

    // the following testGetDefaultProviders tests are related to StudyTest::testGetDefaultProviders but with a user and different profile cases
    @Test
    void testGetDefaultProvidersFromProfile() throws Exception {
        wireMockStubs.userAdminServer.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        wireMockStubs.loadflowServer.stubGetLoadflowParameters(PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING, LOADFLOW_PROFILE_PARAMETERS_JSON, false);
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, VALID_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(OTHER_PROVIDER));
        wireMockStubs.userAdminServer.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING);
    }

    @Test
    void testGetDefaultProvidersFromProfileInvalid() throws Exception {
        wireMockStubs.userAdminServer.stubGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON);
        wireMockStubs.loadflowServer.stubGetLoadflowParameters(PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING, null, true);
        wireMockStubs.loadflowServer.stubGetDefaultProvider(DEFAULT_PROVIDER);
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, INVALID_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        wireMockStubs.userAdminServer.verifyGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID);
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING);
        wireMockStubs.loadflowServer.verifyGetDefaultProvider();
    }

    @Test
    void testGetDefaultProvidersWithoutProfile() throws Exception {
        wireMockStubs.userAdminServer.stubGetUserProfile(NO_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        wireMockStubs.loadflowServer.stubGetDefaultProvider(DEFAULT_PROVIDER);
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, NO_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        wireMockStubs.userAdminServer.verifyGetUserProfile(NO_PROFILE_USER_ID);
        wireMockStubs.loadflowServer.verifyGetDefaultProvider();
    }

    @Test
    void testGetDefaultProvidersWithoutParamInProfile() throws Exception {
        wireMockStubs.userAdminServer.stubGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        wireMockStubs.loadflowServer.stubGetDefaultProvider(DEFAULT_PROVIDER);
        mockMvc.perform(get("/v1/loadflow-default-provider").header(USER_ID_HEADER, NO_PARAMS_IN_PROFILE_USER_ID)).andExpectAll(
                status().isOk(),
                content().string(DEFAULT_PROVIDER));
        wireMockStubs.userAdminServer.verifyGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID);
        wireMockStubs.loadflowServer.verifyGetDefaultProvider();
    }

    @Test
    void testLoadFlowParameters() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        wireMockStubs.loadflowServer.stubGetLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING, LOADFLOW_DEFAULT_PARAMETERS_JSON, false);
        //get initial loadFlow parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING);

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        updateParametersAndDoChecks(studyNameUserIdUuid, LOADFLOW_DEFAULT_PARAMETERS_JSON, LOADFLOW_PARAMETERS_UUID_STRING, "userId", HttpStatus.OK, null, false, null, false);

        wireMockStubs.loadflowServer.stubGetLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING, LOADFLOW_DEFAULT_PARAMETERS_JSON, false);
        //checking update is registered
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING);

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        studyNameUserIdUuid = studyEntity2.getId();

        createParametersAndDoChecks(studyNameUserIdUuid, LOADFLOW_DEFAULT_PARAMETERS_JSON, "userId", null, false, null);
        UUID study2loadFlowParametersUuid = studyRepository.findById(studyNameUserIdUuid).orElseThrow().getLoadFlowParametersUuid();

        wireMockStubs.loadflowServer.stubGetLoadflowParameters(study2loadFlowParametersUuid.toString(), LOADFLOW_DEFAULT_PARAMETERS_JSON, false);
        //get initial loadFlow parameters
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();
        wireMockStubs.loadflowServer.verifyGetLoadflowParameters(study2loadFlowParametersUuid.toString());

        JSONAssert.assertEquals(LOADFLOW_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testGetLoadFlowParametersId() throws Exception {
        // Test case 1: Study with existing loadflow parameters
        StudyEntity studyEntity1 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyWithParametersUuid = studyEntity1.getId();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters/id", studyWithParametersUuid))
                .andExpect(status().isOk())
                .andReturn();

        UUID returnedParametersId = UUID.fromString(mvcResult.getResponse().getContentAsString().replace("\"", ""));
        assertEquals(LOADFLOW_PARAMETERS_UUID, returnedParametersId);

        // Test case 2: Study without existing loadflow parameters (should create and return default)
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyWithoutParametersUuid = studyEntity2.getId();
        wireMockStubs.loadflowServer.stubCreateLoadflowDefaultParameters(objectMapper.writeValueAsString(LOADFLOW_PARAMETERS_UUID_STRING));
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters/id", studyWithoutParametersUuid))
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.loadflowServer.verifyCreateLoadflowDefaultParameters();
        UUID returnedDefaultParametersId = UUID.fromString(mvcResult.getResponse().getContentAsString().replace("\"", ""));
        assertEquals(LOADFLOW_PARAMETERS_UUID, returnedDefaultParametersId); // Should return the default parameters UUID

        // Test case 3: Non-existent study should return 404
        UUID nonExistentStudyUuid = UUID.randomUUID();
        mockMvc.perform(get("/v1/studies/{studyUuid}/loadflow/parameters/id", nonExistentStudyUuid))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetStatusNotFound() {
        UUID notExistingNetworkUuid = UUID.fromString(LOADFLOW_ERROR_RESULT_UUID);
        wireMockStubs.loadflowServer.stubGetLoadflowStatus(UUID.fromString(LOADFLOW_ERROR_RESULT_UUID), null, true);
        assertThrows(HttpClientErrorException.NotFound.class, () -> loadFlowService.getLoadFlowStatus(notExistingNetworkUuid), NOT_FOUND.name());
        wireMockStubs.loadflowServer.verifyGetLoadflowStatus(UUID.fromString(LOADFLOW_ERROR_RESULT_UUID));
    }

    @Test
    void testInvalidateNodesAfterLoadflow() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = getRootNode(studyUuid);
        NetworkModificationNode node1 = createNetworkModificationConstructionNode(studyUuid, rootNode.getId(), UUID.randomUUID(), "variant1", "N1");
        NetworkModificationNode node2 = createNetworkModificationConstructionNode(studyUuid, node1.getId(), UUID.randomUUID(), "variant2", "N2");
        NetworkModificationNode node3 = createNetworkModificationNode(studyUuid, node1.getId(), UUID.randomUUID(), "variant3", "N3", NetworkModificationNodeType.SECURITY);

        /*
         *      R
         *      |
         *     N1(C)
         *   |     |
         *  N2(C)  N3(S)
         */
        updateNodeBuildStatus(node1.getId(), rootNetworkUuid, BuildStatus.BUILT);
        updateNodeBuildStatus(node2.getId(), rootNetworkUuid, BuildStatus.BUILT);
        updateNodeBuildStatus(node3.getId(), rootNetworkUuid, BuildStatus.BUILT);
        updateLoadflowResultUuid(node2.getId(), rootNetworkUuid, UUID.fromString(LOADFLOW_RESULT_UUID));
        updateLoadflowResultUuid(node3.getId(), rootNetworkUuid, UUID.fromString(LOADFLOW_OTHER_NODE_RESULT_UUID));
        /*
         *      R
         *      |
         *     N1
         *   |     |
         *  N2*   N3*
         *
         *  All nodes are BUILT, N2 and N3 have loadflow results
         *  N2 is construction - its loadflow computation will be invalidated
         *  N3 is security - it will be invalidated
         */

        wireMockStubs.userAdminServer.stubGetUserProfile(NO_PROFILE_USER_ID, USER_DEFAULT_PROFILE_JSON);
        wireMockStubs.loadflowServer.stubPutLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING, null);
        wireMockStubs.loadflowServer.stubPutInvalidateStatus();
        wireMockStubs.loadflowServer.stubDeleteLoadflowResults(LOADFLOW_OTHER_NODE_RESULT_UUID);
        wireMockStubs.reportServer.stubDeleteReport();

        // run loadflow invalidation on all study after parameter change
        studyService.setLoadFlowParameters(studyUuid, null, NO_PROFILE_USER_ID);

        wireMockStubs.userAdminServer.verifyGetUserProfile(NO_PROFILE_USER_ID);
        wireMockStubs.loadflowServer.verifyPutLoadflowParameters(LOADFLOW_PARAMETERS_UUID_STRING, null);
        wireMockStubs.loadflowServer.verifyPutInvalidateStatus();
        wireMockStubs.loadflowServer.verifyDeleteLoadflowResults();
        wireMockStubs.reportServer.verifyDeleteReport();

        // node2 and node3 will be invalidated with their children, but the order in which way they are invalidated is not deterministic
        // this is why we don't check node uuid here for those two calls
        checkUpdateModelsStatusMessagesReceived(studyUuid);

        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_COMPUTATION_PARAMETERS);

        assertEquals(NodeBuildStatusEmbeddable.from(BuildStatus.BUILT), rootNetworkNodeInfoService.getRootNetworkNodeInfo(node1.getId(), rootNetworkUuid).map(RootNetworkNodeInfoEntity::getNodeBuildStatus).orElseThrow());
        assertEquals(NodeBuildStatusEmbeddable.from(BuildStatus.BUILT), rootNetworkNodeInfoService.getRootNetworkNodeInfo(node2.getId(), rootNetworkUuid).map(RootNetworkNodeInfoEntity::getNodeBuildStatus).orElseThrow());
        assertEquals(NodeBuildStatusEmbeddable.from(BuildStatus.NOT_BUILT), rootNetworkNodeInfoService.getRootNetworkNodeInfo(node3.getId(), rootNetworkUuid).map(RootNetworkNodeInfoEntity::getNodeBuildStatus).orElseThrow());
    }

    @Test
    void testGetLoadFlowModification() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = getRootNode(studyUuid);
        NetworkModificationNode node1 = createNetworkModificationConstructionNode(studyUuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "N1");
        updateLoadflowResultUuid(node1.getId(), rootNetworkUuid, UUID.fromString(LOADFLOW_RESULT_UUID));

        wireMockStubs.loadflowServer.stubGetLoadflowModifications(UUID.fromString(LOADFLOW_RESULT_UUID), LOADFLOW_MODIFICATIONS, false);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/modifications", studyUuid, rootNetworkUuid, node1.getId()))
            .andExpect(status().isOk())
            .andReturn();
        wireMockStubs.loadflowServer.verifyGetLoadflowModifications(UUID.fromString(LOADFLOW_RESULT_UUID));

        assertEquals(LOADFLOW_MODIFICATIONS, mvcResult.getResponse().getContentAsString());
    }

    @Test
    void testGetLoadFlowModificationNotFound() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, LOADFLOW_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = getRootNode(studyUuid);
        NetworkModificationNode node1 = createNetworkModificationConstructionNode(studyUuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "N1");

        wireMockStubs.loadflowServer.stubGetLoadflowModifications(UUID.fromString(LOADFLOW_ERROR_RESULT_UUID), null, true);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/modifications", studyUuid, rootNetworkUuid, node1.getId()))
            .andExpect(status().isNotFound());

        updateLoadflowResultUuid(node1.getId(), rootNetworkUuid, UUID.fromString(LOADFLOW_ERROR_RESULT_UUID));
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/loadflow/modifications", studyUuid, rootNetworkUuid, node1.getId()))
            .andExpect(status().isNotFound());
        wireMockStubs.loadflowServer.verifyGetLoadflowModifications(UUID.fromString(LOADFLOW_ERROR_RESULT_UUID));
    }

    private void updateNodeBuildStatus(UUID nodeId, UUID rootNetworkUuid, BuildStatus buildStatus) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeId, rootNetworkUuid).orElseThrow();
        rootNetworkNodeInfoEntity.setNodeBuildStatus(NodeBuildStatusEmbeddable.from(buildStatus));
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }

    private void updateLoadflowResultUuid(UUID nodeId, UUID rootNetworkUuid, UUID resultUuid) {
        RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity = rootNetworkNodeInfoRepository.findByNodeInfoIdAndRootNetworkId(nodeId, rootNetworkUuid).orElseThrow();
        rootNetworkNodeInfoEntity.setLoadFlowResultUuid(resultUuid);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID loadFlowParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                loadFlowParametersUuid, null, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private NetworkModificationNode createNetworkModificationConstructionNode(UUID studyUuid, UUID parentNodeUuid,
                                                                              UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid, modificationGroupUuid, variantId, nodeName, NetworkModificationNodeType.CONSTRUCTION);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid, UUID modificationGroupUuid, String variantId,
                                                                  String nodeName, NetworkModificationNodeType nodeType) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName).nodeType(nodeType)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        reset(studyService);
        doNothing().when(studyService).createNodePostAction(eq(studyUuid), eq(parentNodeUuid), any(NetworkModificationNode.class), eq("userId"));

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
                .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        verify(studyService, times(1)).createNodePostAction(eq(studyUuid), eq(parentNodeUuid), any(NetworkModificationNode.class), eq("userId"));

        return modificationNode;
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
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
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, UPDATE_TYPE_PCC_MIN_STATUS);
    }

    private void checkUpdateModelsStatusMessagesReceived(UUID studyUuid) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
        List<UUID> nodesToInvalidate = (List<UUID>) headersStatus.get(NotificationService.HEADER_NODES);
        checkUpdateModelsStatusMessagesReceived(studyUuid, nodesToInvalidate.getFirst());
    }

    @AfterEach
    void tearDown() {
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, LOADFLOW_RESULT_DESTINATION, LOADFLOW_STOPPED_DESTINATION, LOADFLOW_FAILED_DESTINATION);

        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenClear(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
