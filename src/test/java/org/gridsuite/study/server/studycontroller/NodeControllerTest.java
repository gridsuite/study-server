package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.powsybl.network.store.model.VariantInfos;
import org.gridsuite.study.server.dto.ReportLog;
import org.gridsuite.study.server.dto.ReportPage;
import org.gridsuite.study.server.dto.RootNetworkIndexationStatus;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.utils.MatcherReportLog;
import org.junit.jupiter.api.Test;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Florent MILLOT {@literal <florent.millot_externe at rte-france.com>}
 */
public class NodeControllerTest extends StudyTestBase {

    protected static final String URI_NETWORK_MODIF = "/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-modifications?rootNetworkUuid={rootNetworkUuid}";

    @Test
    void testDuplicateStudyWithParametersUuid() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(UUID.randomUUID());
        studyEntity.setSecurityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID());
        studyEntity.setSensitivityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setStateEstimationParametersUuid(UUID.randomUUID());
        studyEntity.setPccMinParametersUuid(UUID.randomUUID());
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
    }

    @Test
    void testDuplicateStudyWithGridLayout() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(UUID.randomUUID());
        studyEntity.setSecurityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID());
        studyEntity.setSensitivityAnalysisParametersUuid(UUID.randomUUID());
        studyEntity.setStateEstimationParametersUuid(UUID.randomUUID());
        studyEntity.setPccMinParametersUuid(UUID.randomUUID());
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, NAD_CONFIG_USER_ID);
    }

    @Test
    void testDuplicateStudyWithoutParametersUuid() throws Exception {
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        StudyEntity studyEntity = studyRepository.findById(study1Uuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setPccMinParametersUuid(null);
        studyEntity.setNetworkVisualizationParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(null);
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
    }

    @Test
    void testDuplicateStudyWithErrorDuringCaseDuplication() throws Exception {
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyRepository.save(studyEntity);

        doAnswer(invocation -> {
            throw new RuntimeException();
        }).when(caseService).duplicateCase(any(), any());

        UUID stubUserProfileNotFoundId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/userId/profile"))
            .willReturn(WireMock.notFound())).getId();

        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                .param(CASE_FORMAT, "XIIDM")
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        String duplicatedStudyUuid = mapper.readValue(response, String.class);
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        assertTrue(studyRepository.findById(UUID.fromString(duplicatedStudyUuid)).isEmpty());

        //now case are duplicated after parameters, case import error does not prevent parameters from being duplicated
        wireMockStubs.verifyParametersDuplicateFromAny(stubParametersDuplicateFromId, 7);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, studyEntity.getNetworkVisualizationParametersUuid().toString());
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, studyEntity.getSpreadsheetConfigCollectionUuid().toString());
        wireMockStubs.verifyUserProfile(stubUserProfileNotFoundId, "userId");
    }

    private void testDuplicateStudy(UUID study1Uuid, UUID rootNetworkUuid, String userId) throws Exception {
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";

        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), rootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";

        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), rootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .securityAnalysisResultUuid(UUID.randomUUID())
                .sensitivityAnalysisResultUuid(UUID.randomUUID())
                .shortCircuitAnalysisResultUuid(UUID.randomUUID())
                .oneBusShortCircuitAnalysisResultUuid(UUID.randomUUID())
                .dynamicSimulationResultUuid(UUID.randomUUID())
                .dynamicSecurityAnalysisResultUuid(UUID.randomUUID())
                .voltageInitResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        // add node aliases to study
        List<NodeAlias> aliases = List.of(new NodeAlias(null, "alias1", null),
            new NodeAlias(node1.getId(), "alias2", "node1"),
            new NodeAlias(node2.getId(), "alias3", "node2"));
        mockMvc.perform(post("/v1/studies/{studyUuid}/node-aliases", study1Uuid)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectWriter.writeValueAsString(aliases))
        ).andExpect(status().isOk());
        checkNodeAliasUpdateMessageReceived(study1Uuid);

        // duplicate the study
        StudyEntity duplicatedStudy = duplicateStudy(study1Uuid, userId);
        assertNotEquals(study1Uuid, duplicatedStudy.getId());

        // Verify node aliases on the duplicated study
        aliases = mapper.readValue(mockMvc.perform(get("/v1/studies/{studyUuid}/node-aliases", duplicatedStudy.getId())).andExpect(status().isOk()).andReturn()
            .getResponse()
            .getContentAsString(), new TypeReference<>() {
            });
        assertEquals(3, aliases.size());
        assertEquals("alias1", aliases.get(0).alias());
        assertNull(aliases.get(0).id());
        assertNull(aliases.get(0).name());
        assertEquals("alias2", aliases.get(1).alias());
        assertEquals("node1", aliases.get(1).name());
        assertEquals("alias3", aliases.get(2).alias());
        assertEquals("node2", aliases.get(2).name());

        // Verify that the network was cloned with only one variant
        List<VariantInfos> networkVariants = networkService.getNetworkVariants(CLONED_NETWORK_UUID);
        assertEquals(1, networkVariants.size(), "Network should be cloned with only one variant");

        //Test duplication from a non-existing source study
        mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", UUID.randomUUID())
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isNotFound());
    }

    private StudyEntity duplicateStudy(UUID studyUuid, String userId) throws Exception {
        // Network reindex stubs - using scenarios for stateful behavior
        UUID stubReindexAllId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("indexed")
            .willReturn(WireMock.ok())).getId();
        UUID stubUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));
        UUID stubUserProfileId = wireMockStubs.stubUserProfile(userId);
        UUID stubUserProfileNadConfigId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + NAD_CONFIG_USER_ID + "/profile"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(USER_PROFILE_WITH_DIAGRAM_CONFIG_PARAMS_JSON))).getId();
        UUID stubDuplicateCaseId = wireMockStubs.caseServer.stubDuplicateCaseWithBody(CASE_UUID_STRING, mapper.writeValueAsString(CLONED_CASE_UUID));
        UUID stubReportsDuplicateId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/reports/.*/duplicate"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();

        wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        // NAD specific mocks
        UUID stubDiagramGridLayoutId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/diagram-grid-layout"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();
        UUID stubNetworkAreaDiagramConfigId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-area-diagram/config"))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();
        UUID stubElementNameId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/elements/" + PROFILE_DIAGRAM_CONFIG_UUID_STRING + "/name"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(mapper.writeValueAsString(NAD_ELEMENT_NAME)))).getId();

        String response = mockMvc.perform(post(STUDIES_URL + "?duplicateFrom={studyUuid}", studyUuid)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newUuid = mapper.readValue(response, String.class);
        StudyEntity sourceStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageOnGoing.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(RootNetworkIndexationStatus.INDEXING_ONGOING.name(), indexationStatusMessageOnGoing.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));
        Message<byte[]> indexationStatusMessageDone = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(newUuid, indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_STUDY_UUID).toString());
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageDone.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(RootNetworkIndexationStatus.INDEXED.name(), indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_INDEXATION_STATUS));

        StudyEntity duplicatedStudy = studyRepository.findById(UUID.fromString(newUuid)).orElse(null);
        assertNotNull(duplicatedStudy);
        RootNode duplicatedRootNode = networkModificationTreeService.getStudyTree(UUID.fromString(newUuid), null);
        assertNotNull(duplicatedRootNode);

        //Check tree node has been duplicated
        assertEquals(1, duplicatedRootNode.getChildren().size());
        NetworkModificationNode duplicatedModificationNode = (NetworkModificationNode) duplicatedRootNode.getChildren().get(0);
        assertEquals(2, duplicatedModificationNode.getChildren().size());

        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getLoadFlowResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getSecurityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getSensitivityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getStateEstimationResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(0)).getPccMinResultUuid());

        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getLoadFlowResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSecurityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getSensitivityAnalysisResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getStateEstimationResultUuid());
        assertNull(((NetworkModificationNode) duplicatedModificationNode.getChildren().get(1)).getPccMinResultUuid());

        //Check requests to duplicate modification groups has been emitted (3 nodes)
        wireMockStubs.verifyDuplicateModificationGroup(stubUuid, 3);

        if (sourceStudy.getSecurityAnalysisParametersUuid() == null) {
            // if we don't have a securityAnalysisParametersUuid we don't call the security-analysis-server to duplicate them
            assertNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
        } else {
            // else we call the security-analysis-server to duplicate them
            assertNotNull(duplicatedStudy.getSecurityAnalysisParametersUuid());
        }
        if (sourceStudy.getVoltageInitParametersUuid() == null) {
            assertNull(duplicatedStudy.getVoltageInitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getVoltageInitParametersUuid());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() == null) {
            assertNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getSensitivityAnalysisParametersUuid());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            assertNotNull(duplicatedStudy.getLoadFlowParametersUuid());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() == null) {
            assertNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getNetworkVisualizationParametersUuid());
        }
        if (sourceStudy.getShortCircuitParametersUuid() == null) {
            assertNull(duplicatedStudy.getShortCircuitParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getShortCircuitParametersUuid());
        }
        if (sourceStudy.getStateEstimationParametersUuid() == null) {
            assertNull(duplicatedStudy.getStateEstimationParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getStateEstimationParametersUuid());
        }
        if (sourceStudy.getPccMinParametersUuid() == null) {
            assertNull(duplicatedStudy.getPccMinParametersUuid());
        } else {
            assertNotNull(duplicatedStudy.getPccMinParametersUuid());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() == null) {
            assertNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
        } else {
            assertNotNull(duplicatedStudy.getSpreadsheetConfigCollectionUuid());
        }

        // Verify HTTP requests
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(duplicatedStudy.getId());
        wireMockStubs.verifyReindexAll(stubReindexAllId, rootNetworkEntity.getNetworkUuid().toString());
        wireMockStubs.caseServer.verifyDuplicateCase(stubDuplicateCaseId, CASE_UUID_STRING, "false");
        if (sourceStudy.getVoltageInitParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getVoltageInitParametersUuid().toString());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getLoadFlowParametersUuid().toString());
        }
        if (sourceStudy.getShortCircuitParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getShortCircuitParametersUuid().toString());
        }
        if (sourceStudy.getSecurityAnalysisParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getSecurityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getSensitivityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() != null) {
            wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, sourceStudy.getNetworkVisualizationParametersUuid().toString());
        }
        if (sourceStudy.getStateEstimationParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getStateEstimationParametersUuid().toString());
        }
        if (sourceStudy.getPccMinParametersUuid() != null) {
            wireMockStubs.verifyParametersDuplicateFrom(sourceStudy.getPccMinParametersUuid().toString());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() != null) {
            wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, sourceStudy.getSpreadsheetConfigCollectionUuid().toString());
        }
        if (NAD_CONFIG_USER_ID.equals(userId)) {
            wireMockStubs.verifyUserProfile(stubUserProfileNadConfigId, userId);
            wireMockStubs.verifyNetworkAreaDiagramConfig(stubNetworkAreaDiagramConfigId);
            wireMockStubs.verifyElementNameGet(stubElementNameId, PROFILE_DIAGRAM_CONFIG_UUID_STRING);
            wireMockStubs.verifyDiagramGridLayout(stubDiagramGridLayoutId);
        } else {
            wireMockStubs.verifyUserProfile(stubUserProfileId, userId);
        }
        wireMockStubs.verifyReportsDuplicate(stubReportsDuplicateId);

        return duplicatedStudy;
    }

    @Test
    void testCutAndPasteNode() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);
        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *       /  \
         *    node1 node2
         *
         */

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .content(createTwoWindingsTransformerAttributes).contentType(MediaType.APPLICATION_JSON)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        // node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // cut the node1 and paste it after node2
        cutAndPasteNode(study1Uuid, node1, node2.getId(), InsertMode.AFTER, 0, userId);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *       |
         *     node2
         *       |
         *     node1
         */

        //node2 should now have 1 child : node 1
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(List.of(node1.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        //modificationNode should now have 1 child : node2
        assertEquals(List.of(node2.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        // cut and paste the node2 before emptyNode
        cutAndPasteNode(study1Uuid, node2, emptyNode.getId(), InsertMode.BEFORE, 1, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        /*
         *              rootNode
         *              /      \
         * modificationNode   node2
         *       |              |
         *     node1        emptyNode
         */

        //rootNode should now have 2 children : modificationNode and node2
        assertEquals(List.of(modificationNodeUuid, node2.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(rootNode.getId()))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        //node1 parent should be modificiationNode
        assertEquals(List.of(node1.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        // emptyNode parent should be node2
        assertEquals(List.of(emptyNode.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        //cut and paste node2 in a new branch starting from modificationNode
        cutAndPasteNode(study1Uuid, node2, modificationNodeUuid, InsertMode.CHILD, 1, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        /*
         *              rootNode
         *              /      \
         * modificationNode   emptyNode
         *     /      \
         *  node1    node2
         */

        //modificationNode should now have 2 children : node1 and node2
        assertEquals(List.of(node1.getId(), node2.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));

        // modificationNode should now have 2 children : emptyNode and modificationNode
        assertEquals(List.of(modificationNodeUuid, emptyNode.getId()), allNodes.stream()
            .filter(nodeEntity ->
                nodeEntity.getParentNode() != null
                    && nodeEntity.getParentNode().getIdNode().equals(rootNode.getId()))
            .map(NodeEntity::getIdNode)
            .collect(Collectors.toList()));
    }

    @Test
    void testCutAndPasteNodeAroundItself() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);

        UUID stubGetCountUuid = wireMockStubs.stubNetworkModificationCountGet(node1.getModificationGroupUuid().toString(), 0);

        //try to cut and paste a node before itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), node1.getId(), InsertMode.BEFORE)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), node1.getId(), InsertMode.AFTER)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());

        //try to cut and paste a node in a new branch after itself and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), node1.getId(), InsertMode.CHILD)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, node1.getModificationGroupUuid().toString());
    }

    @Test
    void testCutAndPasteNodeWithoutModification() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNodeChild = createNetworkModificationNode(study1Uuid, emptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "emptyNodeChild", BuildStatus.BUILT, userId);

        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();

        cutAndPasteNode(study1Uuid, emptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPasteNodeWithModification() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode notEmptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID_2, "notEmptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode notEmptyNodeChild = createNetworkModificationNode(study1Uuid, notEmptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "notEmptyNodeChild", BuildStatus.BUILT, userId);

        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();

        cutAndPasteNode(study1Uuid, notEmptyNode, node1.getId(), InsertMode.BEFORE, 1, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 2);

        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(notEmptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testCutAndPastErrors() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);

        //try cut non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, UUID.randomUUID(), node1.getId(), InsertMode.AFTER)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        //try to cut to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), UUID.randomUUID(), InsertMode.AFTER)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        //try to cut and paste to before the root node and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());
    }

    @Test
    void testCutAndPasteSubtree() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, UUID.randomUUID(), VARIANT_ID, "node1", BuildStatus.BUILT, userId);

        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNodeChild = createNetworkModificationNode(study1Uuid, emptyNode.getId(), UUID.randomUUID(), VARIANT_ID_3, "emptyNodeChild", BuildStatus.BUILT, userId);

        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/subtrees?subtreeToCutParentNodeUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}",
                study1Uuid, emptyNode.getId(), emptyNodeChild.getId())
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isForbidden());

        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/subtrees?subtreeToCutParentNodeUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}",
                study1Uuid, emptyNode.getId(), node1.getId())
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub);

        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(emptyNode.getId(), emptyNodeChild.getId()));
        checkComputationStatusMessageReceived();

        checkSubtreeMovedMessageSent(study1Uuid, emptyNode.getId(), node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node1.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNode.getId(), rootNetworkUuid).getGlobalBuildStatus());
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(emptyNodeChild.getId(), rootNetworkUuid).getGlobalBuildStatus());

        mockMvc.perform(get(STUDIES_URL +
                "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
            study1Uuid, emptyNode.getId())
            .header(USER_ID_HEADER, "userId")).andExpect(status().isOk());

        mockMvc.perform(get(STUDIES_URL +
                "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
            study1Uuid, UUID.randomUUID())
            .header(USER_ID_HEADER, "userId")).andExpect(status().isNotFound());
    }

    private void cutAndPasteNode(UUID studyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, int childCount, String userId) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        boolean wasBuilt = rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeToCopy.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid)).get().getNodeBuildStatus().toDto().isBuilt();
        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        output.receive(TIMEOUT, studyUpdateDestination);
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCutUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                studyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
        checkElementUpdatedMessageSent(studyUuid, userId);
        wireMockStubs.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        boolean nodeHasModifications = networkModificationTreeService.hasModifications(nodeToCopy.getId(), false);

        wireMockStubs.verifyNetworkModificationCountsGet(stubUuid, nodeToCopy.getModificationGroupUuid().toString());

        /*
         * moving node
         */
        //nodeMoved
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.NODE_MOVED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(nodeToCopy.getId(), message.getHeaders().get(NotificationService.HEADER_MOVED_NODE));
        assertEquals(insertMode.name(), message.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        /*
         * invalidating moving node
         */
        //nodeUpdated
        if (wasBuilt) {
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        }
        //loadflow_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //securityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //sensitivityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //shortCircuitAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //oneBusShortCircuitAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //dynamicSimulation_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //dynamicSecurityAnalysis_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //voltageInit_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //stateEstimation_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        //pccMin_status
        assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));

        if (!nodeHasModifications) {
            return;
        }

        /*
         * invalidating old children
         */
        IntStream.rangeClosed(1, childCount).forEach(i -> {
            //nodeUpdated
            if (wasBuilt) {
                assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            }
            //loadflow_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //securityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //sensitivityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //shortCircuitAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //oneBusShortCircuitAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //dynamicSimulation_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //dynamicSecurityAnalysis_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //voltageInit_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //stateEstimation_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
            //pccMin_status
            assertNotNull(output.receive(TIMEOUT, studyUpdateDestination));
        });

        if (wasBuilt) {
            wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub, 1 + childCount);
        }
    }

    @Test
    void testDuplicateNode() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(study1Uuid, rootNode.getId(), UUID.randomUUID(), VARIANT_ID, "node3", BuildStatus.BUILT, userId);
        NetworkModificationNode emptyNode = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        // add modification on node "node1"
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), studyTestUtils.getOneRootNetworkUuid(study1Uuid),
            RootNetworkNodeInfo.builder()
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        //node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node2.getId())).count());

        // duplicate the node1 after node2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study1Uuid, node1, node2.getId(), InsertMode.AFTER, true, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                && nodeEntity.getParentNode().getIdNode().equals(node2.getId()))
            .count());

        // duplicate the node2 before node1
        UUID duplicatedNodeUuid2 = duplicateNode(study1Uuid, study1Uuid, node2, node1.getId(), InsertMode.BEFORE, true, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(duplicatedNodeUuid2)
                && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
            .count());

        //now the tree looks like root -> modificationNode -> duplicatedNode2 -> node1 -> node2 -> duplicatedNode1
        //duplicate node1 in a new branch starting from duplicatedNode2
        UUID duplicatedNodeUuid3 = duplicateNode(study1Uuid, study1Uuid, node1, duplicatedNodeUuid2, InsertMode.CHILD, true, userId);
        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        //expect to have modificationNode as a parent
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(duplicatedNodeUuid3)
                && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid2))
            .count());
        //and expect that no other node has the new branch create node as parent
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(duplicatedNodeUuid3)).count());

        //try copy non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, UUID.randomUUID(), node1.getId(), InsertMode.AFTER, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        //try to copy to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), UUID.randomUUID(), InsertMode.AFTER, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        //try to copy to before the root node and expect forbidden
        mockMvc.perform(post(STUDIES_URL +
                    "/{studyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), rootNode.getId(), InsertMode.BEFORE, study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isForbidden());

        // Test Built status when duplicating an empty node
        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), rootNetworkUuid).getGlobalBuildStatus());
        duplicateNode(study1Uuid, study1Uuid, emptyNode, node3.getId(), InsertMode.BEFORE, false, userId);
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), rootNetworkUuid).getGlobalBuildStatus());
    }

    @Test
    void testDuplicateSubtree() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        NetworkModificationNode node3 = createNetworkModificationNode(study1Uuid, node2.getId(), UUID.randomUUID(), VARIANT_ID, "node3", BuildStatus.BUILT, userId);
        NetworkModificationNode node4 = createNetworkModificationNode(study1Uuid, rootNode.getId(), EMPTY_MODIFICATION_GROUP_UUID, VARIANT_ID_2, "emptyNode", userId);

        /*tree state
            root
            ├── root children
            │   └── node 1
            │       └── node 2
            │           └── node 3
            └── node 4
         */

        // add modification on node "node1" (not built) -> invalidation of node 3
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        UUID deleteModificationIndexStub = wireMockStubs.stubNetworkModificationDeleteIndex();
        UUID stubDeleteReportsId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        checkNodeBuildStatusUpdatedMessageReceived(study1Uuid, List.of(node3.getId()));
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));
        wireMockStubs.verifyNetworkModificationDeleteIndex(deleteModificationIndexStub);

        // Invalidation node 3
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(node3.getId(), firstRootNetworkUuid).getGlobalBuildStatus());

        wireMockStubs.verifyDeleteReports(stubDeleteReportsId, 1);

        // add modification on node "node2"
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        rootNetworkNodeInfoService.updateRootNetworkNode(node2.getId(), firstRootNetworkUuid,
            RootNetworkNodeInfo.builder()
                .nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT))
                .loadFlowResultUuid(UUID.randomUUID())
                .securityAnalysisResultUuid(UUID.randomUUID())
                .stateEstimationResultUuid(UUID.randomUUID())
                .pccMinResultUuid(UUID.randomUUID())
                .build()
        );

        //node 4 should not have any children
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study1Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(node4.getId())).count());

        // duplicate the node1 after node4
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(study1Uuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubDuplicateUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));

        mockMvc.perform(post(STUDIES_URL +
                    "/{study1Uuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), node4.getId(), study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        List<UUID> nodesAfterDuplication = networkModificationTreeService.getAllNodes(study1Uuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        nodesAfterDuplication.removeAll(allNodesBeforeDuplication);
        assertEquals(3, nodesAfterDuplication.size());

        checkSubtreeCreatedMessageSent(study1Uuid, nodesAfterDuplication.get(0), node4.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        wireMockStubs.verifyDuplicateModificationGroup(stubDuplicateUuid, 3);

        /*tree state
            root
            ├── root children
            │   └── node 1
            │       └── node 2
            │           └── node 3
            └── node 4
                └── node 1 duplicated
                    └── node 2 duplicated
                        └── node 3 duplicated
         */

        mockMvc.perform(get(STUDIES_URL +
                "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
            study1Uuid, nodesAfterDuplication.get(0))
            .header(USER_ID_HEADER, "userId")).andExpect(status().isOk());

        mockMvc.perform(get(STUDIES_URL +
                "/{studyUuid}/subtree?parentNodeUuid={parentSubtreeNode}",
            study1Uuid, UUID.randomUUID())
            .header(USER_ID_HEADER, "userId")).andExpect(status().isNotFound());

        allNodes = networkModificationTreeService.getAllNodes(study1Uuid);

        //first root children should still have a children
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(node1.getId())
                && nodeEntity.getParentNode().getIdNode().equals(modificationNodeUuid))
            .count());

        //node4 should now have 1 child
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(nodesAfterDuplication.get(0))
                && nodeEntity.getParentNode().getIdNode().equals(node4.getId()))
            .count());

        //node2 should be built
        assertEquals(BuildStatus.BUILT, networkModificationTreeService.getNodeBuildStatus(node2.getId(), firstRootNetworkUuid).getGlobalBuildStatus());
        //duplicated node2 should now be not built
        assertEquals(BuildStatus.NOT_BUILT, networkModificationTreeService.getNodeBuildStatus(nodesAfterDuplication.get(1), firstRootNetworkUuid).getGlobalBuildStatus());

        //try copy non-existing node and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{targetStudyUuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, UUID.randomUUID(), node1.getId(), study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());

        //try to copy to a non-existing position and expect not found
        mockMvc.perform(post(STUDIES_URL +
                    "/{targetStudyUuid}/tree/subtrees?subtreeToCopyParentNodeUuid={parentNodeToCopy}&referenceNodeUuid={referenceNodeUuid}&sourceStudyUuid={sourceStudyUuid}",
                study1Uuid, node1.getId(), UUID.randomUUID(), study1Uuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testDuplicateNodeBetweenStudies() throws Exception {
        String userId = "userId";
        UUID study1Uuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(study1Uuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        NetworkModificationNode node1 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(study1Uuid, modificationNodeUuid, VARIANT_ID_2, "node2", userId);

        UUID study2Uuid = createStudyWithStubs(userId, CASE_UUID);
        RootNode study2RootNode = networkModificationTreeService.getStudyTree(study2Uuid, null);
        UUID study2ModificationNodeUuid = study2RootNode.getChildren().get(0).getId();
        NetworkModificationNode study2Node2 = createNetworkModificationNode(study2Uuid, study2ModificationNodeUuid, VARIANT_ID_2, "node2", userId);

        // add modification on study 1 node "node1"
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        String createTwoWindingsTransformerAttributes = "{\"type\":\"" + ModificationType.TWO_WINDINGS_TRANSFORMER_CREATION + "\",\"equipmentId\":\"2wtId\",\"equipmentName\":\"2wtName\",\"seriesResistance\":\"10\",\"seriesReactance\":\"10\",\"magnetizingConductance\":\"100\",\"magnetizingSusceptance\":\"100\",\"ratedVoltage1\":\"480\",\"ratedVoltage2\":\"380\",\"voltageLevelId1\":\"CHOO5P6\",\"busOrBusbarSectionId1\":\"CHOO5P6_1\",\"voltageLevelId2\":\"CHOO5P6\",\"busOrBusbarSectionId2\":\"CHOO5P6_1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node1.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTwoWindingsTransformerAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node1.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node1.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        Pair<String, List<ModificationApplicationContext>> modificationBody = Pair.of(createTwoWindingsTransformerAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node1.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        // add modification on node "node2"
        wireMockStubs.stubNetworkModificationPost(mapper.writeValueAsString(new NetworkModificationsResult(List.of(UUID.randomUUID()), List.of(Optional.empty()))));
        String createLoadAttributes = "{\"type\":\"" + ModificationType.LOAD_CREATION + "\",\"loadId\":\"loadId1\",\"loadName\":\"loadName1\",\"loadType\":\"UNDEFINED\",\"activePower\":\"100.0\",\"reactivePower\":\"50.0\",\"voltageLevelId\":\"idVL1\",\"busId\":\"idBus1\"}";
        mockMvc.perform(post(URI_NETWORK_MODIF, study1Uuid, node2.getId(), firstRootNetworkUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createLoadAttributes)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkUpdateModelsStatusMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentCreatingMessagesReceived(study1Uuid, node2.getId());
        checkEquipmentUpdatingFinishedMessagesReceived(study1Uuid, node2.getId());
        checkElementUpdatedMessageSent(study1Uuid, userId);
        modificationBody = Pair.of(createLoadAttributes, List.of(rootNetworkNodeInfoService.getNetworkModificationApplicationContext(firstRootNetworkUuid, node2.getId(), NETWORK_UUID)));
        wireMockStubs.verifyNetworkModificationPostWithVariant(getModificationContextJsonString(mapper, modificationBody));

        //study 2 node2 should not have any child
        List<NodeEntity> allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(0, allNodes.stream().filter(nodeEntity -> nodeEntity.getParentNode() != null && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId())).count());

        // duplicate the node1 from study 1 after node2 from study 2
        UUID duplicatedNodeUuid = duplicateNode(study1Uuid, study2Uuid, node1, study2Node2.getId(), InsertMode.AFTER, true, userId);

        //node2 should now have 1 child
        allNodes = networkModificationTreeService.getAllNodes(study2Uuid);
        assertEquals(1, allNodes.stream()
            .filter(nodeEntity -> nodeEntity.getParentNode() != null
                && nodeEntity.getIdNode().equals(duplicatedNodeUuid)
                && nodeEntity.getParentNode().getIdNode().equals(study2Node2.getId()))
            .count());
    }

    private UUID duplicateNode(UUID sourceStudyUuid, UUID targetStudyUuid, NetworkModificationNode nodeToCopy, UUID referenceNodeUuid, InsertMode insertMode, boolean checkMessagesForStatusModels, String userId) throws Exception {
        List<UUID> allNodesBeforeDuplication = networkModificationTreeService.getAllNodes(targetStudyUuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        UUID stubGetCountUuid = wireMockStubs.stubNetworkModificationCountGet(nodeToCopy.getModificationGroupUuid().toString(),
            EMPTY_MODIFICATION_GROUP_UUID.equals(nodeToCopy.getModificationGroupUuid()) ? 0 : 1);
        UUID stubDuplicateUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));
        if (sourceStudyUuid.equals(targetStudyUuid)) {
            //if source and target are the same no need to pass sourceStudy param
            mockMvc.perform(post(STUDIES_URL +
                        "/{targetStudyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}",
                    targetStudyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode)
                    .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        } else {
            mockMvc.perform(post(STUDIES_URL +
                        "/{targetStudyUuid}/tree/nodes?nodeToCopyUuid={nodeUuid}&referenceNodeUuid={referenceNodeUuid}&insertMode={insertMode}&sourceStudyUuid={sourceStudyUuid}",
                    targetStudyUuid, nodeToCopy.getId(), referenceNodeUuid, insertMode, sourceStudyUuid)
                    .header(USER_ID_HEADER, "userId"))
                .andExpect(status().isOk());
        }

        List<UUID> nodesAfterDuplication = networkModificationTreeService.getAllNodes(targetStudyUuid).stream().map(NodeEntity::getIdNode).collect(Collectors.toList());
        nodesAfterDuplication.removeAll(allNodesBeforeDuplication);
        assertEquals(1, nodesAfterDuplication.size());

        output.receive(TIMEOUT, studyUpdateDestination); // nodeCreated

        if (checkMessagesForStatusModels) {
            checkUpdateModelsStatusMessagesReceived(targetStudyUuid, nodesAfterDuplication.get(0));
        }
        checkElementUpdatedMessageSent(targetStudyUuid, userId);

        wireMockStubs.verifyNetworkModificationCountsGet(stubGetCountUuid, nodeToCopy.getModificationGroupUuid().toString());
        wireMockStubs.verifyDuplicateModificationGroup(stubDuplicateUuid, 1);

        return nodesAfterDuplication.get(0);
    }

    @Test
    void testGetNodeReportLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID, studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.getFirst()));
        wireMockStubs.verifyGetReportLogs(REPORT_ID.toString());

        //test with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        assertThat(reportLogs.get(0), new MatcherReportLog(REPORT_LOGS.getFirst()));
        wireMockStubs.verifyGetReportLogs(REPORT_ID.toString(), "WARN", "testMsgFilter");
    }

    @Test
    void testGetPagedNodeReportLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&paged=true&page=1&size=10", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsPaged(REPORT_ID.toString(), 1, 10);

        //test with severityFilter and messageFilter param
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?reportId=" + REPORT_ID + "&paged=true&page=1&size=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsPaged(REPORT_ID.toString(), 1, 10, "WARN", "testMsgFilter");
    }

    @Test
    void testGetParentNodesReportLogs() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().get(0).getId();
        AbstractNode modificationNode = rootNode.getChildren().get(0);
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID_3, "node3", userId);
        UUID rootNodeReportId = networkModificationTreeService.getReportUuid(rootNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID modificationNodeReportId = networkModificationTreeService.getReportUuid(modificationNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node1ReportId = networkModificationTreeService.getReportUuid(node1.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node2ReportId = networkModificationTreeService.getReportUuid(node2.getId(), firstRootNetworkUuid).orElseThrow();

        UUID stubGetReportLogsId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/reports/logs"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_PAGE)))).getId();

        //          root
        //           |
        //     modificationNode
        //           |
        //         node1
        //         /   \
        //     node2  node3

        //get logs of node2 and all its parents (should not get node3 logs)
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<ReportLog> reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));

        //get logs of node2 and all its parents (should not get node3 logs) with severityFilter and messageFilter param
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs?severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        reportLogs = mapper.readValue(resultAsString, new TypeReference<ReportPage>() { }).content();
        assertEquals(1, reportLogs.size());
        wireMockStubs.verifyGetReportLogs(stubGetReportLogsId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));
    }

    @Test
    void testGetSearchTermMatchesInMultipleFilteredLogs() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        UUID modificationNodeUuid = rootNode.getChildren().getFirst().getId();
        AbstractNode modificationNode = rootNode.getChildren().getFirst();
        NetworkModificationNode node1 = createNetworkModificationNode(studyUuid, modificationNodeUuid, VARIANT_ID, "node1", userId);
        NetworkModificationNode node2 = createNetworkModificationNode(studyUuid, node1.getId(), VARIANT_ID_2, "node2", userId);
        UUID rootNodeReportId = networkModificationTreeService.getReportUuid(rootNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID modificationNodeReportId = networkModificationTreeService.getReportUuid(modificationNode.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node1ReportId = networkModificationTreeService.getReportUuid(node1.getId(), firstRootNetworkUuid).orElseThrow();
        UUID node2ReportId = networkModificationTreeService.getReportUuid(node2.getId(), firstRootNetworkUuid).orElseThrow();

        UUID stubGetReportLogsSearchId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/reports/logs/search"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_PAGE)))).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?searchTerm=testTerm&pageSize=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, node2.getId()).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearch(stubGetReportLogsSearchId, List.of(rootNodeReportId, modificationNodeReportId, node1ReportId, node2ReportId));
    }

    private void checkUpdateModelStatusMessagesReceived(UUID studyUuid, UUID nodeUuid, String updateType) {
        // assert that the broker message has been sent for updating model status
        Message<byte[]> messageStatus = output.receive(StudyTest.TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        if (nodeUuid != null) {
            assertEquals(nodeUuid, headersStatus.get(NotificationService.HEADER_NODE));
        }
        assertEquals(updateType, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    protected void checkUpdateModelsStatusMessagesReceived(UUID studyUuid, UUID nodeUuid) {
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS);
        checkUpdateModelStatusMessagesReceived(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
    }

    protected void checkComputationStatusMessageReceived() {
        //loadflow_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //securityAnalysis_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //sensitivityAnalysis_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //shortCircuitAnalysis_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //oneBusShortCircuitAnalysis_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //dynamicSimulation_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //dynamicSecurityAnalysis_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //voltageInit_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //stateEstimation_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
        //pccMin_status
        assertNotNull(output.receive(StudyTest.TIMEOUT, studyUpdateDestination));
    }

    private void checkNodeAliasUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_SPREADSHEET_NODE_ALIASES, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkNodeBuildStatusUpdatedMessageReceived(UUID studyUuid, List<UUID> nodesUuids) {
        Message<byte[]> messageStatus = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStatus.getPayload()));
        MessageHeaders headersStatus = messageStatus.getHeaders();
        assertEquals(studyUuid, headersStatus.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(new TreeSet<>(nodesUuids), new TreeSet<>((List) headersStatus.get(NotificationService.HEADER_NODES)));
        assertEquals(NotificationService.NODE_BUILD_STATUS_UPDATED, headersStatus.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkSubtreeMovedMessageSent(UUID studyUuid, UUID movedNodeUuid, UUID referenceNodeUuid) {
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.SUBTREE_MOVED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(movedNodeUuid, message.getHeaders().get(NotificationService.HEADER_MOVED_NODE));
        assertEquals(referenceNodeUuid, message.getHeaders().get(NotificationService.HEADER_PARENT_NODE));

    }

    private void checkSubtreeCreatedMessageSent(UUID studyUuid, UUID newNodeUuid, UUID referenceNodeUuid) {
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.SUBTREE_CREATED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(newNodeUuid, message.getHeaders().get(NotificationService.HEADER_NEW_NODE));
        assertEquals(referenceNodeUuid, message.getHeaders().get(NotificationService.HEADER_PARENT_NODE));

    }

    protected void checkEquipmentCreatingMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_CREATING_IN_PROGRESS, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    protected void checkEquipmentUpdatingFinishedMessagesReceived(UUID studyNameUserIdUuid, UUID nodeUuid) {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyNameUserIdUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(nodeUuid, headersStudyUpdate.get(NotificationService.HEADER_PARENT_NODE));
        assertEquals(NotificationService.MODIFICATIONS_UPDATING_FINISHED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }
}
