/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.rootnetworks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class RootNetworkTest {
    private static final String USER_ID = "userId";
    // 1st root network
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

    // 2nd root network
    private static final UUID NETWORK_UUID2 = UUID.randomUUID();
    private static final String NETWORK_ID2 = "networkId2";
    private static final UUID CASE_UUID2 = UUID.randomUUID();
    private static final String CASE_NAME2 = "caseName2";
    private static final String CASE_FORMAT2 = "caseFormat2";
    private static final UUID REPORT_UUID2 = UUID.randomUUID();

    // root network node info 1
    private static final String VARIANT_ID = "variantId";
    private static final UUID DYNAMIC_SIMULATION_RESULT_UUID = UUID.randomUUID();
    private static final UUID DYNAMIC_SECURITY_ANALYSIS_RESULT_UUID = UUID.randomUUID();
    private static final UUID LOADFLOW_RESULT_UUID = UUID.randomUUID();
    private static final UUID SECURITY_ANALYSIS_RESULT_UUID = UUID.randomUUID();
    private static final UUID SHORT_CIRCUIT_ANALYSIS_RESULT_UUID = UUID.randomUUID();
    private static final UUID ONE_BUS_SHORT_CIRCUIT_ANALYSIS_RESULT_UUID = UUID.randomUUID();
    private static final UUID STATE_ESTIMATION_RESULT_UUID = UUID.randomUUID();
    private static final UUID SENSITIVITY_ANALYSIS_RESULT_UUID = UUID.randomUUID();
    private static final UUID VOLTAGE_INIT_RESULT_UUID = UUID.randomUUID();
    private static final UUID NON_EVACUATED_ENERGY_RESULT_UUID = UUID.randomUUID();

    // root network node info 2
    private static final String VARIANT_ID2 = "variantId2";
    private static final UUID LOADFLOW_RESULT_UUID2 = UUID.randomUUID();

    // updated root network
    private static final UUID DUPLICATE_CASE_UUID = UUID.randomUUID();

    private static final String NODE_1_NAME = "node1";
    private static final String NODE_2_NAME = "node2";

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyRepository studyRepository;
    @SpyBean
    private RootNetworkService rootNetworkService;
    @Autowired
    private RootNetworkRequestRepository rootNetworkRequestRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private RootNetworkRepository rootNetworkRepository;
    @Autowired
    private TestUtils testUtils;

    @SpyBean
    private StudyService studyService;

    @MockBean
    private ReportService reportService;
    @MockBean
    private EquipmentInfosService equipmentInfosService;
    @MockBean
    private NetworkStoreService networkStoreService;
    @MockBean
    private CaseService caseService;
    @MockBean
    private DynamicSimulationService dynamicSimulationService;
    @MockBean
    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    @MockBean
    private SecurityAnalysisService securityAnalysisService;
    @MockBean
    private LoadFlowService loadFlowService;
    @MockBean
    private NonEvacuatedEnergyService nonEvacuatedEnergyService;
    @MockBean
    private ShortCircuitService shortCircuitService;
    @MockBean
    private SensitivityAnalysisService sensitivityAnalysisService;
    @MockBean
    private StateEstimationService stateEstimationService;
    @MockBean
    private VoltageInitService voltageInitService;
    @MockBean
    private NetworkService networkService;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

        // start server
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        networkConversionService.setNetworkConversionServerBaseUri(baseUrlWireMock);
        wireMockUtils = new WireMockUtils(wireMockServer);
    }

    @Test
    void testCreateRootNetworkRequest() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // prepare headers for 2nd root network creation request
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
            .willReturn(WireMock.ok())).getId();
        Mockito.doReturn(DUPLICATE_CASE_UUID).when(caseService).duplicateCase(caseUuid, true);

        // request execution - returns RootNetworkRequestInfos
        String response = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName2", "rn2")
                .header("userId", USER_ID)
                .header("content-type", "application/json")
                .content(objectMapper.writeValueAsString(importParameters)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        RootNetworkRequestInfos result = objectMapper.readValue(response, RootNetworkRequestInfos.class);

        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
            false,
            Map.of("caseUuid", WireMock.equalTo(DUPLICATE_CASE_UUID.toString()),
                "caseFormat", WireMock.equalTo(caseFormat),
                "receiver", WireMock.matching(".*rootNetworkUuid.*")),
                objectMapper.writeValueAsString(importParameters)
        );

        // check result values and check it has been saved in database
        assertEquals(USER_ID, result.getUserId());
        assertEquals(studyEntity.getId(), result.getStudyUuid());
        assertNotNull(rootNetworkRequestRepository.findById(result.getId()));
    }

    @Test
    void testCreateRootNetworkRequestOnNotExistingStudy() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", UUID.randomUUID(), caseUuid, caseFormat, "rootNetworkName", "rn1")
                .header("userId", "userId"))
            .andExpect(status().isNotFound());

        // check no rootNetworkRequest has been saved
        assertEquals(0, rootNetworkRequestRepository.count());
    }

    @Test
    void testCreateRootNetworkRequestWithError() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // prepare headers for 2nd root network creation request
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
            .willReturn(WireMock.serverError().withBody("Error when creating root network"))).getId();
        Mockito.doReturn(DUPLICATE_CASE_UUID).when(caseService).duplicateCase(caseUuid, true);

        // request execution - returns RootNetworkRequestInfos
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName2", "rn2")
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isInternalServerError());

        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
            false,
            Map.of("caseUuid", WireMock.equalTo(DUPLICATE_CASE_UUID.toString()),
                "caseFormat", WireMock.equalTo(caseFormat),
                "receiver", WireMock.matching(".*rootNetworkUuid.*")),
            null
        );

        Mockito.verify(caseService, Mockito.times(1)).duplicateCase(caseUuid, true);
        // check no rootNetworkRequest has been saved
        assertEquals(0, rootNetworkRequestRepository.count());
    }

    @Test
    void testCreateRootNetworkWithMaximumReached() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        // create another dummy root networks for the same entity
        createDummyRootNetwork(studyEntity, "dummyRootNetwork");

        studyRepository.save(studyEntity);

        // insert a creation request for the same study entity
        rootNetworkService.insertCreationRequest(UUID.randomUUID(), studyEntity.getId(), "rootNetworkName", "rn1", USER_ID);

        // request execution - fails since there is already too many root networks + root network creation requests for this study
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName", "rn1")
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isForbidden());

        assertEquals(1, rootNetworkRequestRepository.countAllByStudyUuid(studyEntity.getId()));
        assertEquals(2, rootNetworkRepository.countAllByStudyId(studyEntity.getId()));
    }

    @Test
    void testCreateRootNetworkWithExistingName() throws Exception {
        // create study with first root network - first root network will have the name "rootNetworkName"
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // test name existence
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks?name={rootNetworkName}", studyEntity.getId(), "rootNetworkName")
                .header("userId", USER_ID))
            .andExpect(status().isOk());

        // execute request to create root network with name "rootNetworkName" - should fail since this name already exists within the same study
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName", "rn1")
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isForbidden());

        // test name non existence
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks?name={rootNetworkName}", studyEntity.getId(), "rootNetworkNameNotExist")
                .header("userId", USER_ID))
            .andExpect(status().isNoContent());
    }

    @Test
    void testCreateRootNetworkWithForbiddenTag() throws Exception {
        // create study with first root network - first root network will have the tag "dum"
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // test tag existence
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks?tag={rootNetworkTag}", studyEntity.getId(), "dum")
                .header("userId", USER_ID))
            .andExpect(status().isOk());

        // execute request to create root network with tag "dum" - should fail since this tag already exists within the same study
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        String tag = "dum"; // dummy Study default tag
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkNewName", tag)
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isForbidden());
        tag = "thisisatagName"; // forbidden size tag
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}&tag={rootNetworkTag}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkNewName", tag)
            .header("userId", USER_ID)
            .header("content-type", "application/json"))
            .andExpect(status().isForbidden());

        // test tag non existence
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks?tag={rootNetworkTag}", studyEntity.getId(), "xxxx")
                .header("userId", USER_ID))
            .andExpect(status().isNoContent());
    }

    @Test
    void testCreateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        UUID studyUuid = studyEntity.getId();
        studyRepository.save(studyEntity);

        UUID newRootNetworkUuid = UUID.randomUUID();

        // insert creation request as it should be when receiving a caseImportSucceeded with a rootNetworkUuid set
        rootNetworkService.insertCreationRequest(newRootNetworkUuid, studyUuid, "CASE_NAME2", "rn2", USER_ID);

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, CASE_UUID, REPORT_UUID2, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_CREATION);
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        Map<String, Object> headers = createConsumeCaseImportSucceededHeaders(NETWORK_UUID2.toString(), NETWORK_ID2, CASE_FORMAT2, CASE_NAME2, caseImportReceiver, importParameters);

        // send message to consumer
        Mockito.doNothing().when(caseService).disableCaseExpiration(CASE_UUID2);
        messageConsumer.accept(new GenericMessage<>("", headers));

        // get study from database and check new root network has been created with correct values
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        assertEquals(2, updatedStudyEntity.getRootNetworks().size());

        RootNetworkEntity rootNetworkEntity = updatedStudyEntity.getRootNetworks().stream().filter(rne -> rne.getId().equals(newRootNetworkUuid)).findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        assertEquals(newRootNetworkUuid, rootNetworkEntity.getId());
        assertEquals(NETWORK_UUID2, rootNetworkEntity.getNetworkUuid());
        assertEquals(NETWORK_ID2, rootNetworkEntity.getNetworkId());
        assertEquals(CASE_FORMAT2, rootNetworkEntity.getCaseFormat());
        assertEquals(CASE_NAME2, rootNetworkEntity.getCaseName());
        assertEquals(CASE_UUID2, rootNetworkEntity.getCaseUuid());
        assertEquals(REPORT_UUID2, rootNetworkEntity.getReportUuid());
        assertEquals("rn2", rootNetworkEntity.getTag());
        assertEquals(importParameters, rootNetworkService.getImportParameters(newRootNetworkUuid));

        // corresponding rootNetworkRequestRepository should be emptied when root network creation is done
        assertFalse(rootNetworkRequestRepository.existsById(newRootNetworkUuid));

        // check case expiration has been disabled
        Mockito.verify(caseService, Mockito.times(1)).disableCaseExpiration(CASE_UUID2);
    }

    private void createAndConsumeMessageCaseImport(UUID studyUuid, RootNetworkInfos rootNetworkInfos, CaseImportAction caseImportAction) throws Exception {
        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyUuid, rootNetworkInfos.getId(), rootNetworkInfos.getCaseInfos().getCaseUuid(), rootNetworkInfos.getCaseInfos().getOriginalCaseUuid(), rootNetworkInfos.getReportUuid(), USER_ID, 0L, caseImportAction);
        Map<String, Object> headers = createConsumeCaseImportSucceededHeaders(rootNetworkInfos.getNetworkInfos().getNetworkUuid().toString(), rootNetworkInfos.getNetworkInfos().getNetworkId(), rootNetworkInfos.getCaseInfos().getCaseFormat(), rootNetworkInfos.getCaseInfos().getCaseName(), caseImportReceiver, rootNetworkInfos.getImportParameters());

        // send message to consumer
        Mockito.doNothing().when(caseService).disableCaseExpiration(rootNetworkInfos.getCaseInfos().getCaseUuid());
        messageConsumer.accept(new GenericMessage<>("", headers));
    }

    @Test
    void testCreateRootNetworkCaseImportFailure() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID newRootNetworkUuid = UUID.randomUUID();

        // insert creation request as it should be when receiving a caseImportFailed with a rootNetworkUuid set
        rootNetworkRequestRepository.save(RootNetworkRequestEntity.builder().id(newRootNetworkUuid).name(CASE_NAME2).tag("rn2").studyUuid(studyEntity.getId()).userId(USER_ID).build());

        // prepare all headers that will be sent to consumer supposed to receive "caseImportFailed" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportFailed();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, CASE_UUID, REPORT_UUID2, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_CREATION);
        Map<String, Object> headers = createConsumeCaseImportFailedHeaders(caseImportReceiver);

        // send message to consumer
        messageConsumer.accept(new GenericMessage<>("", headers));

        // get study from database and check that root network creation request has been created and deleted after case import failure
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElse(null);
        assertEquals(1, updatedStudyEntity.getRootNetworks().size());

        // corresponding rootNetworkRequestRepository should be emptied when root network creation is failed
        assertFalse(rootNetworkRequestRepository.existsById(newRootNetworkUuid));
    }

    @Test
    void testCreateRootNetworkConsumerWithoutRequest() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // DO NOT insert creation request - it means root network won't be created and remote resources will be deleted
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().id(UUID.randomUUID()).name("newRootNetworkName").tag("newT")
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_UUID, CASE_NAME2, CASE_FORMAT2)).networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .importParameters(Map.of("param1", "value1", "param2", "value2"))
            .reportUuid(REPORT_UUID2)
            .build();
        createAndConsumeMessageCaseImport(studyEntity.getId(), rootNetworkInfos, CaseImportAction.ROOT_NETWORK_CREATION);

        // get study from database and check new root network has been created with correct values
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        assertEquals(1, updatedStudyEntity.getRootNetworks().size());

        // corresponding rootNetworkRequestRepository should be emptied when root network creation is done
        assertFalse(rootNetworkRequestRepository.existsById(rootNetworkInfos.getId()));

        // assert distant resources deletions have been called
        Mockito.verify(caseService, Mockito.times(1)).disableCaseExpiration(CASE_UUID2);
        Mockito.verify(reportService, Mockito.times(1)).deleteReports(List.of(REPORT_UUID2));
        Mockito.verify(equipmentInfosService, Mockito.times(1)).deleteEquipmentIndexes(NETWORK_UUID2);
        Mockito.verify(networkStoreService, Mockito.times(1)).deleteNetwork(NETWORK_UUID2);
        Mockito.verify(caseService, Mockito.times(1)).deleteCase(CASE_UUID2);
    }

    @Test
    void testDeleteRootNetwork() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        // create a second root network - will create a root network link between this and each node
        UUID rootNetworkEntityToDeleteUuid = UUID.randomUUID();
        createDummyRootNetwork(studyEntity, RootNetworkInfos.builder()
            .id(rootNetworkEntityToDeleteUuid)
            .name(CASE_NAME2)
            .importParameters(Map.of("param1", "value1", "param2", "value2"))
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_UUID, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .tag("dum")
            .build());
        studyRepository.save(studyEntity);

        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);
        UUID firstRootNetworkUuid = testUtils.getOneRootNetworkUuid(studyEntity.getId());

        // updating one of the link (firstNode - rootNetworkEntityToDelete) with many data, needed to check all of them will be deleted when root network is deleted
        rootNetworkNodeInfoService.updateRootNetworkNode(firstNode.getId(), rootNetworkEntityToDeleteUuid, RootNetworkNodeInfo.builder()
                .variantId(VARIANT_ID)
                .dynamicSimulationResultUuid(DYNAMIC_SIMULATION_RESULT_UUID)
                .dynamicSecurityAnalysisResultUuid(DYNAMIC_SECURITY_ANALYSIS_RESULT_UUID)
                .loadFlowResultUuid(LOADFLOW_RESULT_UUID)
                .securityAnalysisResultUuid(SECURITY_ANALYSIS_RESULT_UUID)
                .shortCircuitAnalysisResultUuid(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                .oneBusShortCircuitAnalysisResultUuid(ONE_BUS_SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                .stateEstimationResultUuid(STATE_ESTIMATION_RESULT_UUID)
                .sensitivityAnalysisResultUuid(SENSITIVITY_ANALYSIS_RESULT_UUID)
                .voltageInitResultUuid(VOLTAGE_INIT_RESULT_UUID)
                .nonEvacuatedEnergyResultUuid(NON_EVACUATED_ENERGY_RESULT_UUID)
            .build());

        // updating the other link (secondNode - rootNetworkEntityToDelete) with a few data, needed to check data of all root network node info are indeed deleted
        rootNetworkNodeInfoService.updateRootNetworkNode(secondNode.getId(), rootNetworkEntityToDeleteUuid, RootNetworkNodeInfo.builder()
            .variantId(VARIANT_ID2)
            .loadFlowResultUuid(LOADFLOW_RESULT_UUID2)
            .build());

        // before deletion, check we have 2 root networks for study
        assertEquals(2, studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).size());

        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(rootNetworkEntityToDeleteUuid)))
                .header("userId", USER_ID))
            .andExpect(status().isOk());

        // after deletion, check we have only 1 root network for study
        List<BasicRootNetworkInfos> rootNetworkListAfterDeletion = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());
        assertEquals(1, rootNetworkListAfterDeletion.size());
        assertEquals(firstRootNetworkUuid, rootNetworkListAfterDeletion.getFirst().rootNetworkUuid());

        // check deletion of link remote infos
        Mockito.verify(reportService, Mockito.times(1)).deleteReports(List.of(REPORT_UUID2));
        Mockito.verify(equipmentInfosService, Mockito.times(1)).deleteEquipmentIndexes(NETWORK_UUID2);
        Mockito.verify(networkStoreService, Mockito.times(1)).deleteNetwork(NETWORK_UUID2);
        Mockito.verify(caseService, Mockito.times(1)).deleteCase(CASE_UUID2);
        Mockito.verify(dynamicSimulationService, Mockito.times(1)).deleteResults(List.of(DYNAMIC_SIMULATION_RESULT_UUID));
        Mockito.verify(dynamicSecurityAnalysisService, Mockito.times(1)).deleteResults(List.of(DYNAMIC_SECURITY_ANALYSIS_RESULT_UUID));
        // check LOADFLOW_RESULT_UUID2 is also deleted
        Mockito.verify(loadFlowService, Mockito.times(1)).deleteLoadFlowResults(List.of(LOADFLOW_RESULT_UUID, LOADFLOW_RESULT_UUID2));
        Mockito.verify(securityAnalysisService, Mockito.times(1)).deleteSecurityAnalysisResults(List.of(SECURITY_ANALYSIS_RESULT_UUID));
        Mockito.verify(shortCircuitService, Mockito.times(1)).deleteShortCircuitAnalysisResults(List.of(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID));
        Mockito.verify(shortCircuitService, Mockito.times(1)).deleteShortCircuitAnalysisResults(List.of(ONE_BUS_SHORT_CIRCUIT_ANALYSIS_RESULT_UUID));
        Mockito.verify(stateEstimationService, Mockito.times(1)).deleteStateEstimationResults(List.of(STATE_ESTIMATION_RESULT_UUID));
        Mockito.verify(sensitivityAnalysisService, Mockito.times(1)).deleteSensitivityAnalysisResults(List.of(SENSITIVITY_ANALYSIS_RESULT_UUID));
        Mockito.verify(voltageInitService, Mockito.times(1)).deleteVoltageInitResults(List.of(VOLTAGE_INIT_RESULT_UUID));
        Mockito.verify(nonEvacuatedEnergyService, Mockito.times(1)).deleteNonEvacuatedEnergyResults(List.of(NON_EVACUATED_ENERGY_RESULT_UUID));
    }

    @Test
    void testDeleteRootNetworkFailed() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        // create a second root network - will create a root network link between this and each node
        UUID secondRootNetworkUuid = UUID.randomUUID();
        createDummyRootNetwork(studyEntity, RootNetworkInfos.builder()
            .id(secondRootNetworkUuid)
            .name(CASE_NAME2)
            .importParameters(Map.of("param1", "value1", "param2", "value2"))
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_UUID, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .tag("dum")
            .build());
        studyRepository.save(studyEntity);

        networkModificationTreeService.createRoot(studyEntity);
        UUID firstRootNetworkUuid = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).getFirst().rootNetworkUuid();

        // try to delete all root networks
        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(firstRootNetworkUuid, secondRootNetworkUuid)))
                .header("userId", USER_ID))
            .andExpect(status().isForbidden());

        assertEquals(2, studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).size());

        // try to delete unknown root network
        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(UUID.randomUUID())))
                .header("userId", USER_ID))
            .andExpect(status().isNotFound());

        assertEquals(2, studyService.getExistingBasicRootNetworkInfos(studyEntity.getId()).size());
    }

    @Test
    void testUpdateRootNetworkNameAndTag() throws Exception {
        // create study with
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);

        final UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        final String newRootNetworkName = "newRootNetworkName";
        final String newRootNetworkTag = "newT";

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}",
                studyEntity.getId(), rootNetworkUuid)
                .contentType(APPLICATION_JSON)
                .param("name", newRootNetworkName)
                .param("tag", newRootNetworkTag)
                .header("userId", USER_ID)
        ).andExpect(status().isOk());

        RootNetworkEntity updatedRootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElse(null);
        assertNotNull(updatedRootNetwork);
        assertEquals(newRootNetworkName, updatedRootNetwork.getName());
        assertEquals(newRootNetworkTag, updatedRootNetwork.getTag());

    }

    @Test
    void testUpdateOnlyRootNetworkTag() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        RootNetworkEntity firstRootNetwork = studyEntity.getFirstRootNetwork();
        networkModificationTreeService.createRoot(studyEntity);
        final UUID rootNetworkUuid = firstRootNetwork.getId();
        final String newRootNetworkTag = "tag1";

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}",
                studyEntity.getId(), rootNetworkUuid)
                .contentType(APPLICATION_JSON)
                .param("tag", newRootNetworkTag)
                .header("userId", USER_ID)
        ).andExpect(status().isOk());

        RootNetworkEntity updatedRootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElse(null);
        assertNotNull(updatedRootNetwork);
        assertEquals(newRootNetworkTag, updatedRootNetwork.getTag());
        assertEquals(firstRootNetwork.getName(), updatedRootNetwork.getName());
    }

    @Test
    void testUpdateOnlyRootNetworkName() throws Exception {
        // create study with
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        RootNetworkEntity firstRootNetwork = studyEntity.getFirstRootNetwork();
        final UUID rootNetworkUuid = firstRootNetwork.getId();
        final String newRootNetworkNameToUpdate = "nameToUpdate";

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}",
                studyEntity.getId(), rootNetworkUuid)
                .contentType(APPLICATION_JSON)
                .param("name", newRootNetworkNameToUpdate)
                .header("userId", USER_ID)
        ).andExpect(status().isOk());

        RootNetworkEntity updatedRootNetwork = rootNetworkService.getRootNetwork(rootNetworkUuid).orElse(null);
        assertNotNull(updatedRootNetwork);
        assertEquals(newRootNetworkNameToUpdate, updatedRootNetwork.getName());
        assertEquals(firstRootNetwork.getTag(), updatedRootNetwork.getTag());
    }

    @Test
    void testUpdateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        // create a second root network
        RootNetworkInfos rootNetworkInfos = RootNetworkInfos.builder().id(UUID.randomUUID()).tag("oldT").name("oldName")
            .caseInfos(new CaseInfos(UUID.randomUUID(), UUID.randomUUID(), "oldCaseName", "oldCaseFormat")).networkInfos(new NetworkInfos(UUID.randomUUID(), "oldNetworkId"))
            .importParameters(Map.of("param1", "oldValue1", "param2", "oldValue2"))
            .reportUuid(UUID.randomUUID())
            .build();
        createDummyRootNetwork(studyEntity, rootNetworkInfos);
        studyRepository.save(studyEntity);
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode modificationNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        assertEqualsRootNetworkInDB(rootNetworkInfos);
        assertNodeBlocked(modificationNode.getId(), rootNetworkInfos.getId(), false);

        // update root network
        RootNetworkInfos rootNetworkUpdateInfos = RootNetworkInfos.builder().id(rootNetworkInfos.getId()).name("newRootNetworkName").tag("newT")
            .caseInfos(new CaseInfos(DUPLICATE_CASE_UUID, CASE_UUID, "newCaseName", "newCaseFormat")).networkInfos(new NetworkInfos(UUID.randomUUID(), "newNetworkId"))
            .importParameters(Map.of("param1", "newValue1", "param2", "newValue2", "param3", "value3"))
            .reportUuid(UUID.randomUUID())
            .build();

        final UUID newCaseUuid = UUID.randomUUID();
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
                .willReturn(WireMock.ok())).getId();
        Mockito.doReturn(DUPLICATE_CASE_UUID).when(caseService).duplicateCase(newCaseUuid, true);
        Mockito.doNothing().when(networkService).deleteVariants(any(), any());
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}",
                studyEntity.getId(), rootNetworkInfos.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HashMap<>()))
                .param("caseUuid", newCaseUuid.toString())
                .param("caseFormat", rootNetworkUpdateInfos.getCaseInfos().getCaseFormat())
                .param("name", rootNetworkUpdateInfos.getName())
                .param("tag", rootNetworkUpdateInfos.getTag())
                .header("userId", USER_ID)
        ).andExpect(status().isOk());

        Mockito.verify(rootNetworkService, Mockito.times(1)).insertModificationRequest(rootNetworkUpdateInfos.getId(), studyEntity.getId(), rootNetworkUpdateInfos.getName(), rootNetworkUpdateInfos.getTag(), USER_ID);
        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
                false,
                Map.of(
                        "caseUuid", WireMock.equalTo(rootNetworkUpdateInfos.getCaseInfos().getCaseUuid().toString()),
                        "caseFormat", WireMock.equalTo(rootNetworkUpdateInfos.getCaseInfos().getCaseFormat())
                ),
                objectMapper.writeValueAsString(new HashMap<>())
        );

        // verify that the node is blocked
        // build is forbidden, for example
        assertNodeBlocked(modificationNode.getId(), rootNetworkInfos.getId(), true);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/build", studyEntity.getId(), rootNetworkInfos.getId(), modificationNode.getId()).header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isForbidden());

        rootNetworkService.insertModificationRequest(rootNetworkInfos.getId(), studyEntity.getId(), rootNetworkUpdateInfos.getName(), rootNetworkUpdateInfos.getTag(), USER_ID);
        createAndConsumeMessageCaseImport(studyEntity.getId(), rootNetworkUpdateInfos, CaseImportAction.ROOT_NETWORK_MODIFICATION);

        assertEqualsRootNetworkInDB(rootNetworkUpdateInfos);
        assertNodeBlocked(modificationNode.getId(), rootNetworkInfos.getId(), false);
    }

    private void assertNodeBlocked(UUID nodeUuid, UUID rootNetworkUuid, boolean isNodeBlocked) {
        Optional<RootNetworkNodeInfoEntity> networkNodeInfoEntity = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rootNetworkUuid);
        assertTrue(networkNodeInfoEntity.isPresent());
        assertEquals(isNodeBlocked, networkNodeInfoEntity.get().getBlockedBuild());
    }

    private void assertEqualsRootNetworkInDB(RootNetworkInfos rootNetworkInfos) {
        assertTrue(rootNetworkService.getRootNetwork(rootNetworkInfos.getId()).isPresent());
        RootNetworkEntity rootNetworkEntity = rootNetworkService.getRootNetwork(rootNetworkInfos.getId()).get();

        assertEquals(rootNetworkInfos.getName(), rootNetworkEntity.getName());
        assertEquals(rootNetworkInfos.getTag(), rootNetworkEntity.getTag());
        assertEquals(rootNetworkInfos.getCaseInfos().getCaseName(), rootNetworkEntity.getCaseName());
        assertEquals(rootNetworkInfos.getCaseInfos().getCaseUuid(), rootNetworkEntity.getCaseUuid());
        assertEquals(rootNetworkInfos.getCaseInfos().getCaseFormat(), rootNetworkEntity.getCaseFormat());
        assertEquals(rootNetworkInfos.getReportUuid(), rootNetworkEntity.getReportUuid());
        assertEquals(rootNetworkInfos.getImportParameters(), rootNetworkService.getImportParameters(rootNetworkInfos.getId()));
    }

    @Test
    void testUpdateRootNetworkOnNonExistingRootNetwork() throws Exception {
        UUID newCaseUuid = UUID.randomUUID();
        String newCaseFormat = "newCaseFormat";
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/?caseUuid={caseUuid}&caseFormat={newCaseFormat}", studyEntity.getId(), UUID.randomUUID(), newCaseUuid, newCaseFormat)
                .header("userId", "userId"))
            .andExpect(status().isNotFound());

        // check case uuid has not been changed
        assertEquals(CASE_UUID, studyEntity.getFirstRootNetwork().getCaseUuid());
    }

    @Test
    void getRootNetworks() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        // create a second root network
        UUID rootNetworkUuid = UUID.randomUUID();
        createDummyRootNetwork(studyEntity, RootNetworkInfos.builder()
            .name(CASE_NAME2)
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_UUID, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .id(rootNetworkUuid)
            .tag("dum")
            .build());
        studyRepository.save(studyEntity);

        // create a request of root network creation
        UUID requestUuid = UUID.randomUUID();
        rootNetworkService.insertCreationRequest(requestUuid, studyEntity.getId(), "rootNetworkName2", "R_01", USER_ID);

        String response = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks", studyEntity.getId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<BasicRootNetworkInfos> result = objectMapper.readValue(response, new TypeReference<>() { });
        assertEquals(3, result.size());

        BasicRootNetworkInfos requestRootNetwork = result.stream().filter(rootNetworkMinimalInfos -> rootNetworkMinimalInfos.rootNetworkUuid().equals(requestUuid)).findFirst().orElseThrow(() -> new Exception("this should be in the results"));
        assertTrue(requestRootNetwork.isCreating());
        BasicRootNetworkInfos createdRootNetwork = result.stream().filter(rootNetworkMinimalInfos -> rootNetworkMinimalInfos.rootNetworkUuid().equals(rootNetworkUuid)).findFirst().orElseThrow(() -> new Exception("this should be in the results"));
        assertFalse(createdRootNetwork.isCreating());
    }

    @Test
    void getOrderedRootNetworksFromController() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "dummyRootNetwork1");
        createDummyRootNetwork(studyEntity, "dummyRootNetwork2");
        studyRepository.save(studyEntity);

        // check controller
        String response = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks", studyEntity.getId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<BasicRootNetworkInfos> result = objectMapper.readValue(response, new TypeReference<>() { });
        assertEquals(3, result.size());
        assertEquals("rootNetworkName", result.get(0).name());
        assertEquals("dummyRootNetwork1", result.get(1).name());
        assertEquals("dummyRootNetwork2", result.get(2).name());
    }

    @Test
    void getOrderedRootNetworksFromService() {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        createDummyRootNetwork(studyEntity, "dummyRootNetwork1");
        createDummyRootNetwork(studyEntity, "dummyRootNetwork2");
        studyRepository.save(studyEntity);

        // after creating 3 root networks, check rootNetworkOrder to assert they are correctly incremented
        List<BasicRootNetworkInfos> result = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());
        assertEquals(3, result.size());
        // check rootNetworkOrdered is ordered by creation ordered
        assertEquals("rootNetworkName", result.get(0).name());
        assertEquals("dummyRootNetwork1", result.get(1).name());
        assertEquals("dummyRootNetwork2", result.get(2).name());

        // delete the 2nd root network
        studyService.deleteRootNetworks(studyEntity.getId(), List.of(result.get(1).rootNetworkUuid()));

        // check "dummyRootNetwork2" root network order have been updated correctly
        List<BasicRootNetworkInfos> resultAfterDeletion = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());
        assertEquals(2, resultAfterDeletion.size());
        assertEquals("rootNetworkName", resultAfterDeletion.get(0).name());
        assertEquals("dummyRootNetwork2", resultAfterDeletion.get(1).name());

        // create new root network
        UUID rootNetworkUuid = UUID.randomUUID();
        rootNetworkService.insertCreationRequest(rootNetworkUuid, studyEntity.getId(), "dummyRootNetwork3", "RN_3", USER_ID);
        studyService.createRootNetwork(studyEntity.getId(), RootNetworkInfos.builder()
            .name(CASE_NAME2)
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_UUID, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .id(rootNetworkUuid)
            .tag("dum")
            .build());

        // check "dummyRootNetwork3" root network order have been created correctly
        List<BasicRootNetworkInfos> resultAfterCreation = studyService.getExistingBasicRootNetworkInfos(studyEntity.getId());
        assertEquals(3, resultAfterCreation.size());
        assertEquals("rootNetworkName", resultAfterCreation.get(0).name());
        assertEquals("dummyRootNetwork2", resultAfterCreation.get(1).name());
        assertEquals("dummyRootNetwork3", resultAfterCreation.get(2).name());
    }

    private Map<String, Object> createConsumeCaseImportSucceededHeaders(String networkUuid, String networkId, String caseFormat, String caseName, CaseImportReceiver caseImportReceiver, Map<String, String> importParameters) throws JsonProcessingException {
        Map<String, Object> headers = new HashMap<>();
        headers.put("networkUuid", networkUuid);
        headers.put("networkId", networkId);
        headers.put("caseFormat", caseFormat);
        headers.put("caseName", caseName);
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(caseImportReceiver));
        headers.put(HEADER_IMPORT_PARAMETERS, importParameters);
        return headers;
    }

    private Map<String, Object> createConsumeCaseImportFailedHeaders(CaseImportReceiver caseImportReceiver) throws JsonProcessingException {
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_ERROR, "FAILED TO IMPORT CASE");
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(caseImportReceiver));
        return headers;
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, RootNetworkInfos rootNetworkInfos) {
        studyEntity.addRootNetwork(rootNetworkInfos.toEntity());
    }

    private void createDummyRootNetwork(StudyEntity studyEntity, String name) {
        RootNetworkEntity rootNetworkEntity = RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(name)
            .caseInfos(new CaseInfos(UUID.randomUUID(), UUID.randomUUID(), "caseName", "caseFormat"))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .tag("dum")
            .build().toEntity();
        studyEntity.addRootNetwork(rootNetworkEntity);
    }

    @AfterEach
    void tearDown() {
        TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        rootNetworkRequestRepository.deleteAll();
        rootNetworkRepository.deleteAll();
    }
}
