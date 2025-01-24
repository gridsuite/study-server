/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.gridsuite.study.server.service.*;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_IMPORT_PARAMETERS;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.*;
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
    private static final UUID NEW_NETWORK_UUID = UUID.randomUUID();
    private static final String NEW_NETWORK_ID = "newNetworkId";
    private static final UUID NEW_CASE_UUID = UUID.randomUUID();
    private static final String NEW_CASE_NAME = "newCaseName";
    private static final String NEW_CASE_FORMAT = "newCaseFormat";
    private static final UUID NEW_REPORT_UUID = UUID.randomUUID();
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
    @Autowired
    private RootNetworkService rootNetworkService;
    @Autowired
    private RootNetworkCreationRequestRepository rootNetworkCreationRequestRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

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
    @Autowired
    private RootNetworkRepository rootNetworkRepository;

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

        // request execution - returns RootNetworkCreationRequestInfos
        String response = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName2")
                .header("userId", USER_ID)
                .header("content-type", "application/json")
                .content(objectMapper.writeValueAsString(importParameters)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        RootNetworkCreationRequestInfos result = objectMapper.readValue(response, RootNetworkCreationRequestInfos.class);

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
        assertNotNull(rootNetworkCreationRequestRepository.findById(result.getId()));
    }

    @Test
    void testCreateRootNetworkRequestOnNotExistingStudy() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}", UUID.randomUUID(), caseUuid, caseFormat, "rootNetworkName")
                .header("userId", "userId"))
            .andExpect(status().isNotFound());

        // check no rootNetworkCreationRequest has been saved
        assertEquals(0, rootNetworkCreationRequestRepository.count());
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

        // request execution - returns RootNetworkCreationRequestInfos
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName2")
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
        // check no rootNetworkCreationRequest has been saved
        assertEquals(0, rootNetworkCreationRequestRepository.count());
    }

    @Test
    void testCreateRootNetworkWithMaximumReached() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // create another dummy root networks for the same entity
        createDummyRootNetwork(studyEntity);

        // insert a creation request for the same study entity
        rootNetworkCreationRequestRepository.save(RootNetworkCreationRequestEntity.builder()
                .id(UUID.randomUUID())
                .name("rootNetworkName")
                .studyUuid(studyEntity.getId())
                .userId(USER_ID)
            .build());

        // request execution - fails since there is already too many root networks + root network creation requests for this study
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName")
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isForbidden());

        assertEquals(1, rootNetworkCreationRequestRepository.countAllByStudyUuid(studyEntity.getId()));
        assertEquals(2, rootNetworkRepository.countAllByStudyId(studyEntity.getId()));
    }

    @Test
    void testCreateRootNeworkWithExistingName() throws Exception {
        // create study with first root network - first root network will have the name "rootNetworkName"
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // execute request to create root network with name "rootNetworkName" - should fail since this name already exists within the same study
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}&name={rootNetworkName}", studyEntity.getId(), caseUuid, caseFormat, "rootNetworkName")
                .header("userId", USER_ID)
                .header("content-type", "application/json"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testCreateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID newRootNetworkUuid = UUID.randomUUID();

        // insert creation request as it should be when receiving a caseImportSucceeded with a rootNetworkUuid set
        rootNetworkCreationRequestRepository.save(RootNetworkCreationRequestEntity.builder().id(newRootNetworkUuid).name(CASE_NAME2).studyUuid(studyEntity.getId()).userId(USER_ID).build());

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, REPORT_UUID2, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_CREATION);
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
        assertEquals(importParameters, rootNetworkService.getImportParameters(newRootNetworkUuid));

        // corresponding rootNetworkCreationRequestRepository should be emptied when root network creation is done
        assertFalse(rootNetworkCreationRequestRepository.existsById(newRootNetworkUuid));

        // check case expiration has been disabled
        Mockito.verify(caseService, Mockito.times(1)).disableCaseExpiration(CASE_UUID2);
    }

    @Test
    void testCreateRootNetworkConsumerWithoutRequest() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID newRootNetworkUuid = UUID.randomUUID();

        // DO NOT insert creation request - it means root network won't be created and remote resources will be deleted

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, REPORT_UUID2, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_CREATION);
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        Map<String, Object> headers = createConsumeCaseImportSucceededHeaders(NETWORK_UUID2.toString(), NETWORK_ID2, CASE_FORMAT2, CASE_NAME2, caseImportReceiver, importParameters);

        // send message to consumer
        Mockito.doNothing().when(caseService).disableCaseExpiration(CASE_UUID2);
        messageConsumer.accept(new GenericMessage<>("", headers));

        // get study from database and check new root network has been created with correct values
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        assertEquals(1, updatedStudyEntity.getRootNetworks().size());

        // corresponding rootNetworkCreationRequestRepository should be emptied when root network creation is done
        assertFalse(rootNetworkCreationRequestRepository.existsById(newRootNetworkUuid));

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
        studyRepository.save(studyEntity);
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNode.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
        NetworkModificationNode secondNode = networkModificationTreeService.createNode(studyEntity, firstNode.getId(), createModificationNodeInfo(NODE_2_NAME), InsertMode.AFTER, null);
        UUID firstRootNetworkUuid = rootNetworkService.getStudyRootNetworks(studyEntity.getId()).get(0).getId();

        // create a second root network - will create a root network link between this and each node
        RootNetworkEntity rootNetworkEntityToDelete = rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(CASE_NAME2)
            .importParameters(Map.of("param1", "value1", "param2", "value2"))
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .build());

        // updating one of the link (firstNode - rootNetworkEntityToDelete) with many data, needed to check all of them will be deleted when root network is deleted
        rootNetworkNodeInfoService.updateRootNetworkNode(firstNode.getId(), rootNetworkEntityToDelete.getId(), RootNetworkNodeInfo.builder()
                .variantId(VARIANT_ID)
                .dynamicSimulationResultUuid(DYNAMIC_SIMULATION_RESULT_UUID)
                .loadFlowResultUuid(LOADFLOW_RESULT_UUID)
                .securityAnalysisResultUuid(SECURITY_ANALYSIS_RESULT_UUID)
                .shortCircuitAnalysisResultUuid(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                .oneBusShortCircuitAnalysisResultUuid(ONE_BUS_SHORT_CIRCUIT_ANALYSIS_RESULT_UUID)
                .stateEstimationResultUuid(STATE_ESTIMATION_RESULT_UUID)
                .sensitivityAnalysisResultUuid(SENSITIVITY_ANALYSIS_RESULT_UUID)
                .voltageInitResultUuid(VOLTAGE_INIT_RESULT_UUID)
                .nonEvacuatedEnergyResultUuid(NON_EVACUATED_ENERGY_RESULT_UUID)
            .build());

        // updating the other link  (secondNode - rootNetworkEntityToDelete) with a few data, needed to check data of all root network node info are indeed deleted
        rootNetworkNodeInfoService.updateRootNetworkNode(secondNode.getId(), rootNetworkEntityToDelete.getId(), RootNetworkNodeInfo.builder()
            .variantId(VARIANT_ID2)
            .loadFlowResultUuid(LOADFLOW_RESULT_UUID2)
            .build());

        // before deletion, check we have 2 root networks for study
        assertEquals(2, rootNetworkService.getStudyRootNetworks(studyEntity.getId()).size());

        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(rootNetworkEntityToDelete.getId())))
                .header("userId", USER_ID))
            .andExpect(status().isOk());

        // after deletion, check we have only 1 root network for study
        List<RootNetworkEntity> rootNetworkEntityListAfterDeletion = rootNetworkService.getStudyRootNetworks(studyEntity.getId());
        assertEquals(1, rootNetworkEntityListAfterDeletion.size());
        assertEquals(firstRootNetworkUuid, rootNetworkEntityListAfterDeletion.get(0).getId());

        // check deletion of 1st link remote infos
        Mockito.verify(reportService, Mockito.times(1)).deleteReports(List.of(REPORT_UUID2));
        Mockito.verify(equipmentInfosService, Mockito.times(1)).deleteEquipmentIndexes(NETWORK_UUID2);
        Mockito.verify(networkStoreService, Mockito.times(1)).deleteNetwork(NETWORK_UUID2);
        Mockito.verify(caseService, Mockito.times(1)).deleteCase(CASE_UUID2);
        Mockito.verify(dynamicSimulationService, Mockito.times(1)).deleteResult(DYNAMIC_SIMULATION_RESULT_UUID);
        Mockito.verify(loadFlowService, Mockito.times(1)).deleteLoadFlowResult(LOADFLOW_RESULT_UUID);
        Mockito.verify(securityAnalysisService, Mockito.times(1)).deleteSaResult(SECURITY_ANALYSIS_RESULT_UUID);
        Mockito.verify(shortCircuitService, Mockito.times(1)).deleteShortCircuitAnalysisResult(SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        Mockito.verify(shortCircuitService, Mockito.times(1)).deleteShortCircuitAnalysisResult(ONE_BUS_SHORT_CIRCUIT_ANALYSIS_RESULT_UUID);
        Mockito.verify(stateEstimationService, Mockito.times(1)).deleteStateEstimationResult(STATE_ESTIMATION_RESULT_UUID);
        Mockito.verify(sensitivityAnalysisService, Mockito.times(1)).deleteSensitivityAnalysisResult(SENSITIVITY_ANALYSIS_RESULT_UUID);
        Mockito.verify(voltageInitService, Mockito.times(1)).deleteVoltageInitResult(VOLTAGE_INIT_RESULT_UUID);
        Mockito.verify(nonEvacuatedEnergyService, Mockito.times(1)).deleteNonEvacuatedEnergyResult(NON_EVACUATED_ENERGY_RESULT_UUID);

        // check deletion of 2nd link remote infos
        Mockito.verify(loadFlowService, Mockito.times(1)).deleteLoadFlowResult(LOADFLOW_RESULT_UUID2);
    }

    @Test
    void testDeleteRootNetworkFailed() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        UUID firstRootNetworkUuid = rootNetworkService.getStudyRootNetworks(studyEntity.getId()).get(0).getId();

        // create a second root network - will create a root network link between this and each node
        RootNetworkEntity secondRootNetwork = rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name(CASE_NAME2)
            .importParameters(Map.of("param1", "value1", "param2", "value2"))
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .build());

        // try to delete all root networks
        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(firstRootNetworkUuid, secondRootNetwork.getId())))
                .header("userId", USER_ID))
            .andExpect(status().isForbidden());

        assertEquals(2, rootNetworkService.getStudyRootNetworks(studyEntity.getId()).size());

        // try to delete unknown root network
        mockMvc.perform(delete("/v1/studies/{studyUuid}/root-networks", studyEntity.getId())
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(UUID.randomUUID())))
                .header("userId", USER_ID))
            .andExpect(status().isNotFound());

        assertEquals(2, rootNetworkService.getStudyRootNetworks(studyEntity.getId()).size());
    }

    @Test
    void testUpdateRootNetworkCase() throws Exception {
        final UUID studyUuid = UUID.randomUUID();
        final UUID rootNetworkUuid = UUID.randomUUID();
        final UUID caseUuid = UUID.randomUUID();
        final String caseFormat = "newCaseFormat";
        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("param1", "newValue1");
        importParameters.put("param2", "newValue2");

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
            .willReturn(WireMock.ok())).getId();

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}",
            studyUuid, rootNetworkUuid)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(importParameters))
            .param("caseUuid", caseUuid.toString())
            .param("caseFormat", caseFormat)
            .header("userId", USER_ID)
        ).andExpect(status().isOk());

        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
            false,
            Map.of(
                "caseUuid", WireMock.equalTo(caseUuid.toString()),
                "caseFormat", WireMock.equalTo(caseFormat)
            ),
            objectMapper.writeValueAsString(importParameters)
        );
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
    void testUpdateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        NodeEntity rootNode = networkModificationTreeService.createRoot(studyEntity);

        UUID rootNetworkUuid = studyEntity.getFirstRootNetwork().getId();
        UUID studyUuid = studyEntity.getId();
        UUID oldCaseUuid = studyEntity.getFirstRootNetwork().getCaseUuid();

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyUuid, rootNetworkUuid, NEW_CASE_UUID, NEW_REPORT_UUID, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_MODIFICATION);
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        Map<String, Object> headers = createConsumeCaseImportSucceededHeaders(NEW_NETWORK_UUID.toString(), NEW_NETWORK_ID, NEW_CASE_FORMAT, NEW_CASE_NAME, caseImportReceiver, importParameters);

        // send message to consumer
        Mockito.doNothing().when(caseService).disableCaseExpiration(NEW_CASE_UUID);
        Mockito.doNothing().when(studyService).invalidateBuild(studyUuid, rootNode.getIdNode(), rootNetworkUuid, false,
            false,
            true);
        messageConsumer.accept(new GenericMessage<>("", headers));

        // get study from database and check new root network has been updated with new case
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));

        assertEquals(1, updatedStudyEntity.getRootNetworks().size());

        RootNetworkEntity rootNetworkEntity = updatedStudyEntity.getRootNetworks().stream().filter(rne -> rne.getId().equals(rootNetworkUuid)).findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOT_NETWORK_NOT_FOUND));
        assertEquals(rootNetworkUuid, rootNetworkEntity.getId());
        assertEquals(NEW_NETWORK_UUID, rootNetworkEntity.getNetworkUuid());
        assertEquals(NEW_NETWORK_ID, rootNetworkEntity.getNetworkId());
        assertEquals(NEW_CASE_FORMAT, rootNetworkEntity.getCaseFormat());
        assertEquals(NEW_CASE_NAME, rootNetworkEntity.getCaseName());
        assertEquals(NEW_CASE_UUID, rootNetworkEntity.getCaseUuid());
        assertEquals(NEW_REPORT_UUID, rootNetworkEntity.getReportUuid());
        assertEquals(importParameters, rootNetworkService.getImportParameters(rootNetworkUuid));

        // check that old case has been deleted successfully
        assertFalse(caseService.caseExists(oldCaseUuid));
        //assert invalidate Build node has been called on root node
        Mockito.verify(studyService, Mockito.times(1)).invalidateBuild(studyUuid, rootNode.getIdNode(), rootNetworkUuid, false,
            false,
            true);
    }

    @Test
    void getRootNetworks() throws Exception {
        // create study with one root node, two network modification node and a root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // create a second root network
        RootNetworkEntity rootNetworkEntity = rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder()
            .name(CASE_NAME2)
            .caseInfos(new CaseInfos(CASE_UUID2, CASE_NAME2, CASE_FORMAT2))
            .networkInfos(new NetworkInfos(NETWORK_UUID2, NETWORK_ID2))
            .reportUuid(REPORT_UUID2)
            .id(UUID.randomUUID())
            .build());

        // create a request of root network creation
        UUID requestUuid = UUID.randomUUID();
        rootNetworkCreationRequestRepository.save(new RootNetworkCreationRequestEntity(requestUuid, "rootNetworkName", studyEntity.getId(), USER_ID));

        String response = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks", studyEntity.getId()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<BasicRootNetworkInfos> result = objectMapper.readValue(response, new TypeReference<>() { });
        assertEquals(3, result.size());

        BasicRootNetworkInfos requestRootNetwork = result.stream().filter(rootNetworkMinimalInfos -> rootNetworkMinimalInfos.rootNetworkUuid().equals(requestUuid)).findFirst().orElseThrow(() -> new Exception("this should be in the results"));
        assertTrue(requestRootNetwork.isCreating());
        BasicRootNetworkInfos createdRootNetwork = result.stream().filter(rootNetworkMinimalInfos -> rootNetworkMinimalInfos.rootNetworkUuid().equals(rootNetworkEntity.getId())).findFirst().orElseThrow(() -> new Exception("this should be in the results"));
        assertFalse(createdRootNetwork.isCreating());
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

    private void createDummyRootNetwork(StudyEntity studyEntity) {
        rootNetworkService.createRootNetwork(studyEntity, RootNetworkInfos.builder()
            .id(UUID.randomUUID())
            .name("dummyRootNetwork")
            .caseInfos(new CaseInfos(UUID.randomUUID(), "caseName", "caseFormat"))
            .networkInfos(new NetworkInfos(UUID.randomUUID(), UUID.randomUUID().toString()))
            .reportUuid(UUID.randomUUID())
            .build());
    }

    @AfterEach
    void tearDown() {
        TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        rootNetworkCreationRequestRepository.deleteAll();
        rootNetworkRepository.deleteAll();
    }
}
