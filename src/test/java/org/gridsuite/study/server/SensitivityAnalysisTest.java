/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import com.powsybl.sensitivity.SensitivityFunctionType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.*;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.dto.ComputationType.SENSITIVITY_ANALYSIS;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SensitivityAnalysisTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SensitivityAnalysisTest.class);

    private static final String SENSITIVITY_ANALYSIS_RESULT_UUID = "b3a84c9b-9594-4e85-8ec7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID = "11131111-8594-4e55-8ef7-07ea965d24eb";
    private static final String SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID = "25222222-9994-4e55-8ec7-07ea965d24eb";
    private static final String NOT_FOUND_NODE_UUID = "a3a80c9b-9594-4e55-8ec7-07ea965d24eb";

    private static final String FAKE_RESULT_JSON = "fake result json";
    private static final String SENSITIVITY_ANALYSIS_STATUS_JSON = "{\"status\":\"COMPLETED\"}";

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String NETWORK_UUID_2_STRING = "11111111-aaaa-48be-be46-ef7b93331e32";
    private static final String NETWORK_UUID_3_STRING = "22222222-bd31-43be-be46-e50296951e32";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String CASE_2_UUID_STRING = "656719f3-aaaa-48be-be46-ef7b93331e32";
    private static final UUID CASE_2_UUID = UUID.fromString(CASE_2_UUID_STRING);
    private static final String CASE_3_UUID_STRING = "790769f9-bd31-43be-be46-e50296951e32";
    private static final UUID CASE_3_UUID = UUID.fromString(CASE_3_UUID_STRING);

    private static final String VARIANT_ID = "variant_1";
    private static final String VARIANT_ID_2 = "variant_2";
    private static final String VARIANT_ID_3 = "variant_3";

    private static final String SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID SENSITIVITY_ANALYSIS_PARAMETERS_UUID = UUID.fromString(SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING);
    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String NO_PARAMS_IN_PROFILE_USER_ID = "noParamInProfileUser";
    private static final String INVALID_PARAMS_IN_PROFILE_USER_ID = "invalidParamInProfileUser";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING = "f09f5282-8e34-48b5-b66e-7ef9f3f36c4f";
    private static final String VALID_PARAMS_IN_PROFILE_USER_ID = "validParamInProfileUser";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING = "1cec4a7b-ab7e-4d78-9dd7-ce73c5ef11d9";
    private static final String PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING = "a4ce25e1-59a7-401d-abb1-04425fe24587";
    private static final String USER_PROFILE_NO_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile No params\"}";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":true}";
    private static final String USER_PROFILE_INVALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with broken params\",\"sensitivityAnalysisParameterId\":\"" + PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING + "\",\"allParametersLinksValid\":false}";
    private static final String DUPLICATED_PARAMS_JSON = "\"" + PROFILE_SENSITIVITY_ANALYSIS_DUPLICATED_PARAMETERS_UUID_STRING + "\"";
    public static final String SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":0.0,\"angleFlowSensitivityValueThreshold\":0.0,\"flowVoltageSensitivityValueThreshold\":0.0," +
        "\"sensitivityInjectionsSet\":[],\"sensitivityInjection\":[],\"sensitivityHVDC\":[],\"sensitivityPST\":[],\"sensitivityNodes\":[]}";
    public static final String SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON = "{\"flowFlowSensitivityValueThreshold\":90.0,\"angleFlowSensitivityValueThreshold\":0.6,\"flowVoltageSensitivityValueThreshold\":0.1,\"sensitivityInjectionsSet\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"distributionType\":\"PROPORTIONAL\",\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityInjection\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"injections\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityHVDC\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"sensitivityType\":\"DELTA_MW\",\"hvdcs\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityPST\":[{\"monitoredBranches\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"sensitivityType\":\"DELTA_MW\",\"psts\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}],\"sensitivityNodes\":[{\"monitoredVoltageLevels\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da321\",\"containerName\":\"identifiable1\"}],\"equipmentsInVoltageRegulation\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da322\",\"containerName\":\"identifiable2\"}],\"contingencies\":[{\"containerId\":\"cf399ef3-7f14-4884-8c82-1c90300da323\",\"containerName\":\"identifiable3\"}],\"activated\":true}]}";

    private static final String SENSITIVITY_ANALYSIS_PROFILE_PARAMETERS_JSON = "{\"lowVoltageAbsoluteThreshold\":30.0,\"lowVoltageProportionalThreshold\":0.4,\"highVoltageAbsoluteThreshold\":0.0,\"highVoltageProportionalThreshold\":0.0,\"flowProportionalThreshold\":0.1}";

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final String ELEMENT_UPDATE_DESTINATION = "element.update";
    private static final String SENSITIVITY_ANALYSIS_RESULT_DESTINATION = "sensitivityanalysis.result";
    private static final String SENSITIVITY_ANALYSIS_STOPPED_DESTINATION = "sensitivityanalysis.stopped";
    private static final String SENSITIVITY_ANALYSIS_FAILED_DESTINATION = "sensitivityanalysis.run.dlx";

    private static final byte[] SENSITIVITY_RESULTS_AS_ZIPPED_CSV = {0x00, 0x01};

    private static final long TIMEOUT = 1000;

    private static WireMockServer wireMockServer;
    private ComputationServerStubs computationServerStubs;
    private ReportServerStubs reportServerStubs;
    private UserAdminServerStubs userAdminServerStubs;

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
    private SensitivityAnalysisService sensitivityAnalysisService;

    @Autowired
    private ActionsService actionsService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ConsumerService consumerService;

    private static final SensitivityAnalysisParameters SENSITIVITY_ANALYSIS_PARAMETERS = new SensitivityAnalysisParameters();

    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeAll
    static void initWireMock(@Autowired InputDestination input) {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockServer.start();
    }

    @AfterAll
    static void shutdownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.shutdown();
        }
    }

    @BeforeEach
    void setup() throws JsonProcessingException {
        computationServerStubs = new ComputationServerStubs(wireMockServer);
        reportServerStubs = new ReportServerStubs(wireMockServer);
        userAdminServerStubs = new UserAdminServerStubs(wireMockServer);

        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        sensitivityAnalysisService.setSensitivityAnalysisServerBaseUri(wireMockServer.baseUrl());
        actionsService.setActionsServerBaseUri(wireMockServer.baseUrl());
        reportService.setReportServerBaseUri(wireMockServer.baseUrl());

        loadFlowService.setLoadFlowServerBaseUri(wireMockServer.baseUrl());
        userAdminService.setUserAdminServerBaseUri(wireMockServer.baseUrl());
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID sensitivityAnalysisParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, UUID.randomUUID(), null, null, sensitivityAnalysisParametersUuid, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
                                                                  UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
            .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
            .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).build());

        return modificationNode;
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    private void consumeSensitivityAnalysisResult(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, String resultUuid) throws JsonProcessingException {
        // consume result
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid));
        MessageHeaders messageHeaders = new MessageHeaders(Map.of("resultUuid", resultUuid, HEADER_RECEIVER, resultUuidJson));
        consumerService.consumeSensitivityAnalysisResult().accept(MessageBuilder.createMessage("", messageHeaders));

        Message<byte[]> sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        sensitivityAnalysisStatusMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisStatusMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisStatusMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        Message<byte[]> sensitivityAnalysisUpdateMessage = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, sensitivityAnalysisUpdateMessage.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) sensitivityAnalysisUpdateMessage.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT, updateType);
    }

    @Test
    void testSensitivityAnalysis() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode1Uuid, UUID.randomUUID(), VARIANT_ID_2, "node 2");
        UUID modificationNode2Uuid = modificationNode2.getId();
        NetworkModificationNode modificationNode3 = createNetworkModificationNode(studyNameUserIdUuid, modificationNode2Uuid, UUID.randomUUID(), VARIANT_ID_3, "node 3");
        UUID modificationNode3Uuid = modificationNode3.getId();

        // run sensitivity analysis on root node (not allowed)
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isForbidden());

        testSensitivityAnalysisWithRootNetworkUuidAndNodeUuid(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, SENSITIVITY_ANALYSIS_RESULT_UUID);
        testSensitivityAnalysisWithRootNetworkUuidAndNodeUuid(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode3Uuid, SENSITIVITY_ANALYSIS_OTHER_NODE_RESULT_UUID);

        // --- 1. Non-existing node → 404 / No Content ---

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/.*"))
            .willReturn(WireMock.notFound().withBody("Node result not found")));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyNameUserIdUuid, firstRootNetworkUuid, UUID.randomUUID(), "fakeJsonSelector"))
            .andExpect(status().isNotFound());

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/.*/filter-options"))
            .willReturn(WireMock.noContent()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}",
                studyNameUserIdUuid, firstRootNetworkUuid, UUID.randomUUID(), "fakeJsonSelector"))
            .andExpect(status().isNoContent());

        // --- 2. Run additional sensitivity analysis for deletion test ---
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, SENSITIVITY_ANALYSIS_RESULT_UUID);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run",
                studyNameUserIdUuid, firstRootNetworkUuid, modificationNode2Uuid)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        consumeSensitivityAnalysisResult(studyNameUserIdUuid, firstRootNetworkUuid, modificationNode2Uuid, SENSITIVITY_ANALYSIS_RESULT_UUID);

        // --- 3. Test result count (dryRun) ---
        computationServerStubs.stubResultsCount(1);

        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SENSITIVITY_ANALYSIS.toString())
                .queryParam("dryRun", "true"))
            .andExpect(status().isOk());

        computationServerStubs.verifyResultsCountGet();

        // --- 4. Delete Sensitivity analysis results ---
        assertEquals(1, rootNetworkNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());

        computationServerStubs.stubDeleteResults("/v1/results");
        reportServerStubs.stubDeleteReport();

        mockMvc.perform(delete("/v1/supervision/computation/results")
                .queryParam("type", SENSITIVITY_ANALYSIS.toString())
                .queryParam("dryRun", "false"))
            .andExpect(status().isOk());

        // Verify delete requests
        WireMockUtilsCriteria.verifyDeleteRequest(wireMockServer, "/v1/results", Map.of("resultsUuids", WireMock.matching(".*")));
        reportServerStubs.verifyDeleteReport();

        // Verify repository is empty after deletion
        assertEquals(0, rootNetworkNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull().size());

    }

    private void testSensitivityAnalysisWithRootNetworkUuidAndNodeUuid(UUID studyUuid,
                                                                       UUID rootNetworkUuid,
                                                                       UUID nodeUuid,
                                                                       String resultUuid) throws Exception {

        // --- 1. Sensitivity analysis not found ---
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyUuid, rootNetworkUuid, NOT_FOUND_NODE_UUID, "subjectId"))
            .andExpect(status().isNotFound());

        // --- 2. Run sensitivity analysis ---
        computationServerStubs.stubComputationRun(NETWORK_UUID_STRING, null, resultUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run",
                studyUuid, rootNetworkUuid, nodeUuid)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        consumeSensitivityAnalysisResult(studyUuid, rootNetworkUuid, nodeUuid, resultUuid);

        // --- 3. GET sensitivity analysis result ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + ".*"))
            .willReturn(WireMock.ok().withBody(FAKE_RESULT_JSON)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}",
                studyUuid, rootNetworkUuid, nodeUuid, "fakeJsonSelector"))
            .andExpectAll(status().isOk(), content().string(FAKE_RESULT_JSON));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathMatching("/v1/results/" + resultUuid + ".*")));

        // --- 4. GET sensitivity analysis filter-options ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/filter-options.*"))
            .willReturn(WireMock.ok().withBody(FAKE_RESULT_JSON)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}",
                studyUuid, rootNetworkUuid, nodeUuid, "fakeJsonSelector"))
            .andExpectAll(status().isOk(), content().string(FAKE_RESULT_JSON));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/filter-options.*")));

        // --- 5. GET sensitivity analysis with filters and globalFilters ---
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid))
            .withQueryParam("selector", WireMock.matching("subjectId"))
            .withQueryParam("filters", WireMock.matching(".*"))        // match any value
            .withQueryParam("globalFilters", WireMock.matching(".*"))
            .withQueryParam("networkUuid", WireMock.matching(".*"))
            .withQueryParam("variantId", WireMock.matching(".*"))
            .willReturn(WireMock.ok().withBody(FAKE_RESULT_JSON)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?filters=lineId2&selector=subjectId&globalFilters=ss",
                studyUuid, rootNetworkUuid, nodeUuid))
            .andExpectAll(status().isOk(), content().string(FAKE_RESULT_JSON));

        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/results/" + resultUuid))
            .withQueryParam("selector", WireMock.equalTo("subjectId"))
            .withQueryParam("filters", WireMock.equalTo("lineId2"))
            .withQueryParam("globalFilters", WireMock.equalTo("ss"))
            .withQueryParam("networkUuid", WireMock.matching(".*"))
            .withQueryParam("variantId", WireMock.matching(".*"))
        );

        // --- 6. GET sensitivity analysis status ---
        computationServerStubs.stubGetResultStatus(resultUuid, SENSITIVITY_ANALYSIS_STATUS_JSON);

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/status",
                studyUuid, rootNetworkUuid, nodeUuid))
            .andExpectAll(status().isOk(), content().string(SENSITIVITY_ANALYSIS_STATUS_JSON));

        computationServerStubs.verifyGetResultStatus(resultUuid);

        // --- 7. Export CSV ---
        String content = objectMapper.writeValueAsString(SensitivityAnalysisCsvFileInfos.builder()
            .sensitivityFunctionType(SensitivityFunctionType.BRANCH_CURRENT_1)
            .resultTab("N")
            .csvHeaders(List.of("h1", "h2", "h3"))
            .language("en")
            .build());

        // CSV error case
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv?selector=fakeJsonSelector",
                studyUuid, rootNetworkUuid, UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, "userId")
                .content(content))
            .andExpect(status().isNotFound());
        // CSV export success
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/csv.*"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .withBody(SENSITIVITY_RESULTS_AS_ZIPPED_CSV)));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/csv?selector=fakeJsonSelector",
                studyUuid, rootNetworkUuid, nodeUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_USER_ID, "userId")
                .content(content))
            .andExpectAll(status().isOk(), content().bytes(SENSITIVITY_RESULTS_AS_ZIPPED_CSV));

        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/csv.*")));

        // --- 8. Stop sensitivity analysis ---
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/stop"))
            .willReturn(WireMock.ok()));

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/stop",
                studyUuid, rootNetworkUuid, nodeUuid)
                .header(HEADER_USER_ID, "userId"))
            .andExpect(status().isOk());

        Message<String> stoppedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)))
            .setHeader("resultUuid", resultUuid)
            .build();

        consumerService.consumeSensitivityAnalysisStopped().accept(stoppedMessage);
        checkMessagesReceived(studyUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);

        computationServerStubs.verifyComputationStop(resultUuid, Map.of("receiver", WireMock.matching(".*")));
    }

    private void checkMessagesReceived(UUID studyUuid, String updateTypeToCheck) {
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(updateType, updateTypeToCheck);
    }

    @Test
    void testGetSensitivityResultWithWrongId() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID notFoundSensitivityUuid = UUID.randomUUID();
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, firstRootNetworkUuid, UUID.randomUUID(), FAKE_RESULT_JSON))
            .andExpect(status().isNotFound()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, firstRootNetworkUuid, UUID.randomUUID(), FAKE_RESULT_JSON))
            .andExpect(status().isNoContent()).andReturn();

        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNodeUuid, firstRootNetworkUuid, notFoundSensitivityUuid, SENSITIVITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNodeUuid, firstRootNetworkUuid, SENSITIVITY_ANALYSIS));
        assertEquals(notFoundSensitivityUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNodeUuid, firstRootNetworkUuid, SENSITIVITY_ANALYSIS));

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid))
            .willReturn(WireMock.notFound()));

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + notFoundSensitivityUuid + "/filter-options" + ".*"))
            .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result?selector={selector}", studyUuid, firstRootNetworkUuid, modificationNodeUuid, FAKE_RESULT_JSON))
            .andExpect(status().isNotFound()).andReturn();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/result/filter-options?selector={selector}", studyUuid, firstRootNetworkUuid, modificationNodeUuid, FAKE_RESULT_JSON))
            .andExpect(status().isNotFound()).andReturn();
    }

    @Test
    void testResetUuidResultWhenSensitivityFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), firstRootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, resultUuid, SENSITIVITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));

        StudyService studyServiceMock = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), SENSITIVITY_ANALYSIS_FAILED_DESTINATION);
            return resultUuid;
        }).when(studyServiceMock).runSensitivityAnalysis(any(), any(), any(), any());
        assertNotNull(studyEntity.getId());
        studyServiceMock.runSensitivityAnalysis(studyEntity.getId(), modificationNode.getId(), firstRootNetworkUuid, "testUserId");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);
    }

    @Test
    void testResetUuidResultWhenSAFailed() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        StudyEntity studyEntity = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyEntity.getId(), null);
        NetworkModificationNode modificationNode = createNetworkModificationNode(studyEntity.getId(), rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node 1");
        String resultUuidJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode.getId(), firstRootNetworkUuid));

        // Set an uuid result in the database
        rootNetworkNodeInfoService.updateComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, resultUuid, SENSITIVITY_ANALYSIS);
        assertNotNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));
        assertEquals(resultUuid, rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));

        StudyService studyServiceMock = Mockito.mock(StudyService.class);
        doAnswer(invocation -> {
            input.send(MessageBuilder.withPayload("").setHeader(HEADER_RECEIVER, resultUuidJson).build(), SENSITIVITY_ANALYSIS_FAILED_DESTINATION);
            return resultUuid;
        }).when(studyServiceMock).runSensitivityAnalysis(any(), any(), any(), any());
        assertNotNull(studyEntity.getId());
        studyServiceMock.runSensitivityAnalysis(studyEntity.getId(), modificationNode.getId(), firstRootNetworkUuid, "testUserId");

        // Test reset uuid result in the database
        assertNull(rootNetworkNodeInfoService.getComputationResultUuid(modificationNode.getId(), firstRootNetworkUuid, SENSITIVITY_ANALYSIS));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);
    }

    // test sensitivity analysis on network 2 will fail
    @Test
    void testSensitivityAnalysisFailedForNotification() throws Exception {
        // --- Setup study and node ---
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_2_STRING), CASE_2_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNode1Uuid = modificationNode1.getId();

        // --- Stub failing sensitivity analysis run ---
        computationServerStubs.stubComputationRun(NETWORK_UUID_2_STRING, null, SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID);

        // Run failing sensitivity analysis
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run",
                studyUuid, firstRootNetworkUuid, modificationNode1Uuid)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // Simulate failed result message
        String receiverJson = objectMapper.writeValueAsString(new NodeReceiver(modificationNode1Uuid, firstRootNetworkUuid));
        Message<String> failedMessage = MessageBuilder.withPayload("")
            .setHeader(HEADER_RECEIVER, receiverJson)
            .setHeader("resultUuid", SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID)
            .build();
        consumerService.consumeSensitivityAnalysisFailed().accept(failedMessage);

        // Verify message sent to frontend
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        // Status message
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED, updateType);

        // Verify the "run-and-save" POST request was called
        wireMockServer.verify(1, WireMock.postRequestedFor(
            WireMock.urlPathMatching("/v1/networks/" + NETWORK_UUID_2_STRING + "/run-and-save.*")
        ));

        // --- Test coverage: failed message without receiver ---
        StudyEntity studyEntity2 = insertDummyStudy(UUID.fromString(NETWORK_UUID_3_STRING), CASE_3_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyUuid2 = studyEntity2.getId();
        UUID firstRootNetworkUuid2 = studyTestUtils.getOneRootNetworkUuid(studyUuid2);
        UUID rootNodeUuid2 = getRootNodeUuid(studyUuid2);
        NetworkModificationNode modificationNode2 = createNetworkModificationNode(studyUuid2, rootNodeUuid2, UUID.randomUUID(), VARIANT_ID, "node 2");
        UUID modificationNode1Uuid2 = modificationNode2.getId();

        // Stub failing analysis for second study
        computationServerStubs.stubComputationRun(NETWORK_UUID_3_STRING, null, SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID);
        // Run failing sensitivity analysis without receiver
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run",
                studyUuid2, firstRootNetworkUuid2, modificationNode1Uuid2)
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // Consume failed message without receiver -> ignored
        failedMessage = MessageBuilder.withPayload("")
            .setHeader("resultUuid", SENSITIVITY_ANALYSIS_ERROR_NODE_RESULT_UUID)
            .build();
        consumerService.consumeSensitivityAnalysisFailed().accept(failedMessage);

        // Status message still sent
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid2, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        updateType = (String) message.getHeaders().get(HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, updateType);

        // Verify run-and-save POST request called
        wireMockServer.verify(1, WireMock.postRequestedFor(
            WireMock.urlPathMatching("/v1/networks/" + NETWORK_UUID_3_STRING + "/run-and-save.*")
        ));
    }

    private void createOrUpdateParametersAndDoChecks(UUID studyNameUserIdUuid, String parameters, String userId, HttpStatusCode status) throws Exception {
        mockMvc.perform(
                post("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)
                    .header("userId", userId)
                    .contentType(MediaType.ALL)
                    .content(parameters))
            .andExpect(status().is(status.value()));

        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, ELEMENT_UPDATE_DESTINATION);
        assertEquals(studyNameUserIdUuid, message.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
    }

    @Test
    void testSensitivityAnalysisParameters() throws Exception {

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON)));
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID))
            .willReturn(WireMock.ok()));

        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(SENSITIVITY_ANALYSIS_PARAMETERS_UUID))));

        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters/default"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(objectMapper.writeValueAsString(SENSITIVITY_ANALYSIS_PARAMETERS_UUID))));

        UUID studyNameUserIdUuid = insertDummyStudy(UUID.randomUUID(), UUID.randomUUID(), SENSITIVITY_ANALYSIS_PARAMETERS_UUID).getId();
        assertNotNull(studyNameUserIdUuid);

        // Get sensitivity analysis parameters (on existing)
        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));

        // Set sensitivity analysis parameters (will update)
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON, "userId", HttpStatus.OK);

        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));

        // Get sensitivity analysis (not existing, so it will create default)
        StudyEntity studyEntityToUpdate = studyRepository.findById(studyNameUserIdUuid).orElseThrow(() -> new StudyException(NOT_FOUND));
        studyEntityToUpdate.setSensitivityAnalysisParametersUuid(null);
        studyRepository.save(studyEntityToUpdate);

        mockMvc.perform(get("/v1/studies/{studyUuid}/sensitivity-analysis/parameters", studyNameUserIdUuid)).andExpectAll(
            status().isOk(),
            content().string(SENSITIVITY_ANALYSIS_DEFAULT_PARAMETERS_JSON));

        wireMockServer.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/default")));
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID)));
        assertEquals(SENSITIVITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSensitivityAnalysisParametersUuid());

        // Set sensitivity analysis parameters (will create)
        studyEntityToUpdate.setSensitivityAnalysisParametersUuid(null);
        studyRepository.save(studyEntityToUpdate);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, SENSITIVITY_ANALYSIS_UPDATED_PARAMETERS_JSON, "userId", HttpStatus.OK);

        wireMockServer.verify(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/parameters")));
        assertEquals(SENSITIVITY_ANALYSIS_PARAMETERS_UUID, studyRepository.findById(studyNameUserIdUuid).orElseThrow().getSensitivityAnalysisParametersUuid());
    }

    @Test
    void testResetSensitivityAnalysisParametersUserHasNoProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        userAdminServerStubs.stubGetUserProfile(NO_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING, SENSITIVITY_ANALYSIS_PROFILE_PARAMETERS_JSON);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PROFILE_USER_ID, HttpStatus.OK);

        userAdminServerStubs.verifyGetUserProfile(NO_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetSensitivityAnalysisParametersUserHasNoParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        userAdminServerStubs.stubGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING, SENSITIVITY_ANALYSIS_PROFILE_PARAMETERS_JSON);

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", NO_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        userAdminServerStubs.verifyGetUserProfile(NO_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetSensitivityAnalysisParametersUserHasInvalidParamsInProfile() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, SENSITIVITY_ANALYSIS_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        userAdminServerStubs.stubGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING, SENSITIVITY_ANALYSIS_PROFILE_PARAMETERS_JSON);
        computationServerStubs.stubParametersDuplicateFromNotFound(PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", INVALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.NO_CONTENT);

        userAdminServerStubs.verifyGetUserProfile(INVALID_PARAMS_IN_PROFILE_USER_ID);
        computationServerStubs.verifyParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING);
        computationServerStubs.verifyParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
    }

    @Test
    void testResetSensitivityAnalysisParametersUserHasValidParamsInProfile() throws Exception {

        StudyEntity studyEntity = insertDummyStudy(
            UUID.fromString(NETWORK_UUID_STRING),
            CASE_UUID,
            SENSITIVITY_ANALYSIS_PARAMETERS_UUID
        );

        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(
            studyUuid,
            rootNodeUuid,
            UUID.randomUUID(),
            VARIANT_ID,
            "node 1"
        );

        // ---------------- WireMock stubs ----------------
        wireMockServer.stubFor(post(urlPathMatching(
            "/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .willReturn(ok()));

        wireMockServer.stubFor(post(urlPathMatching("/v1/results/invalidate-status.*"))
            .withQueryParam("resultUuid", matching(".*"))
            .willReturn(ok()));

        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        computationServerStubs.stubParameterPut(wireMockServer, SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING, objectWriter.writeValueAsString(SENSITIVITY_ANALYSIS_PARAMETERS));
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_PARAMS_JSON);

        // ---------------- Run sensitivity analysis ----------------
        mockMvc.perform(post(
                "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/sensitivity-analysis/run", studyUuid, firstRootNetworkUuid, modificationNode1.getId())
                .header(HEADER_USER_ID, "testUserId"))
            .andExpect(status().isOk());

        // consume sensitivity status event (REQUIRED)
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(
            UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS,
            message.getHeaders().get(HEADER_UPDATE_TYPE)
        );

        createOrUpdateParametersAndDoChecks(studyUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        // run-and-save called
        wireMockServer.verify(postRequestedFor(
            urlPathMatching("/v1/networks/" + NETWORK_UUID_STRING + "/run-and-save.*"))
            .withQueryParam("reportType", equalTo("SensitivityAnalysis"))
            .withQueryParam("variantId", equalTo(VARIANT_ID))
            .withQueryParam("receiver", matching(".*"))
        );
        // parameters duplicated
        computationServerStubs.stubParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_PARAMS_JSON);
        wireMockServer.verify(deleteRequestedFor(
            urlPathEqualTo("/v1/parameters/" + SENSITIVITY_ANALYSIS_PARAMETERS_UUID_STRING))
        );
        long invalidateCalls = wireMockServer.getAllServeEvents().stream()
            .filter(e -> e.getRequest().getUrl().startsWith("/v1/results/invalidate-status"))
            .count();
        assertTrue(invalidateCalls <= 1);
    }

    @Test
    void testResetSensitivityAnalysisParametersUserHasValidParamsInProfileButNoExistingSensitivityAnalysisParams() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        userAdminServerStubs.stubGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON);
        wireMockServer.stubFor(post(urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", equalTo(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))
            .willReturn(WireMock.ok()
                .withBody(DUPLICATED_PARAMS_JSON)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        createOrUpdateParametersAndDoChecks(studyNameUserIdUuid, "", VALID_PARAMS_IN_PROFILE_USER_ID, HttpStatus.OK);

        // --- Verify requests ---
        userAdminServerStubs.verifyGetUserProfile(VALID_PARAMS_IN_PROFILE_USER_ID);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", equalTo(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING))
        );
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION, SENSITIVITY_ANALYSIS_FAILED_DESTINATION, SENSITIVITY_ANALYSIS_RESULT_DESTINATION, SENSITIVITY_ANALYSIS_STOPPED_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertQueuesEmptyThenClear(
                destinations, output
            );
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
