package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.RootNetworkCreationRequestInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkCreationRequestRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_IMPORT_PARAMETERS;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.junit.jupiter.api.Assertions.*;
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

    // updated root network
    private static final UUID NEW_NETWORK_UUID = UUID.randomUUID();
    private static final String NEW_NETWORK_ID = "newNetworkId";
    private static final UUID NEW_CASE_UUID = UUID.randomUUID();
    private static final String NEW_CASE_NAME = "newCaseName";
    private static final String NEW_CASE_FORMAT = "newCaseFormat";
    private static final UUID NEW_REPORT_UUID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @MockBean
    CaseService caseService;

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
    private StudyService studyService;

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

        // request execution - returns RootNetworkCreationRequestInfos
        String response = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}", studyEntity.getId(), caseUuid, caseFormat)
                .header("userId", USER_ID)
                .header("content-type", "application/json")
                .content(objectMapper.writeValueAsString(importParameters)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        RootNetworkCreationRequestInfos result = objectMapper.readValue(response, RootNetworkCreationRequestInfos.class);

        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
            false,
            Map.of("caseUuid", WireMock.equalTo(caseUuid.toString()),
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
    void testUpdateRootNetworkOnNonExistingRootNetwork() throws Exception {
        UUID newCaseUuid = UUID.randomUUID();
        String newCaseFormat = "newCaseFormat";
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);

        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/?caseUuid={caseUuid}&caseFormat={newCaseFormat}", studyEntity.getId(), UUID.randomUUID(), newCaseUuid, newCaseFormat)
                        .header("userId", "userId"))
                .andExpect(status().isNotFound());

        // check case uuid has not been changed
        assertEquals(studyEntity.getFirstRootNetwork().getCaseUuid(), CASE_UUID);
    }

    @Test
    void testUpdateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID oldCaseUuid = studyEntity.getFirstRootNetwork().getCaseUuid();

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), studyEntity.getFirstRootNetwork().getId(), NEW_CASE_UUID, NEW_REPORT_UUID, USER_ID, 0L, CaseImportAction.ROOT_NETWORK_MODIFICATION);
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        Map<String, Object> headers = createConsumeCaseImportSucceededHeaders(NEW_NETWORK_UUID.toString(), NEW_NETWORK_ID, NEW_CASE_FORMAT, NEW_CASE_NAME, caseImportReceiver, importParameters);

        // send message to consumer
        Mockito.doNothing().when(caseService).disableCaseExpiration(NEW_CASE_UUID);
        messageConsumer.accept(new GenericMessage<>("", headers));

        // get study from database and check new root network has been updated with new case
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));

        assertEquals(1, updatedStudyEntity.getRootNetworks().size());

        RootNetworkEntity rootNetworkEntity = updatedStudyEntity.getRootNetworks().stream().filter(rne -> rne.getId().equals(studyEntity.getFirstRootNetwork().getId())).findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
        assertEquals(studyEntity.getFirstRootNetwork().getId(), rootNetworkEntity.getId());
        assertEquals(NEW_NETWORK_UUID, rootNetworkEntity.getNetworkUuid());
        assertEquals(NEW_NETWORK_ID, rootNetworkEntity.getNetworkId());
        assertEquals(NEW_CASE_FORMAT, rootNetworkEntity.getCaseFormat());
        assertEquals(NEW_CASE_NAME, rootNetworkEntity.getCaseName());
        assertEquals(NEW_CASE_UUID, rootNetworkEntity.getCaseUuid());
        assertEquals(NEW_REPORT_UUID, rootNetworkEntity.getReportUuid());
        assertEquals(importParameters, rootNetworkService.getImportParameters(studyEntity.getFirstRootNetwork().getId()));

        // check that old case has been deleted successfully
        assertFalse(caseService.caseExists(oldCaseUuid));

        // corresponding rootNetworkCreationRequestRepository should be emptied when root network creation is done
        assertFalse(rootNetworkCreationRequestRepository.existsById(studyEntity.getFirstRootNetwork().getId()));

        studyEntity.getFirstRootNetwork().getRootNetworkNodeInfos();
    }

    @Test
    void testCreateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID newRootNetworkUuid = UUID.randomUUID();

        // insert creation request as it should be when receiving a caseImportSucceeded with a rootNetworkUuid set
        rootNetworkCreationRequestRepository.save(RootNetworkCreationRequestEntity.builder().id(newRootNetworkUuid).studyUuid(studyEntity.getId()).userId(USER_ID).build());

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

        RootNetworkEntity rootNetworkEntity = updatedStudyEntity.getRootNetworks().stream().filter(rne -> rne.getId().equals(newRootNetworkUuid)).findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
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

    @Test
    void testUpdateRootNetworkCase() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        studyEntity.getFirstRootNetwork().getImportParameters();
        //Update the root network case
        UUID newCaseUuid = UUID.randomUUID();
        String newCaseFormat = "updatedCaseFormat";
        Map<String, String> importParameters = new HashMap<>();

        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
                .willReturn(WireMock.ok())).getId();

        // Perform the PUT request to update the root network case
        mockMvc.perform(put("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}", studyEntity.getId(), studyEntity.getFirstRootNetwork().getId())
                        .header("userId", "userId")
                        .param("caseUuid", newCaseUuid.toString()) // Pass the caseUuid as a query parameter
                        .param("caseFormat", newCaseFormat) // Pass the caseFormat as a query parameter
                        .content(objectMapper.writeValueAsString(importParameters)) // Pass the importParameters as JSON
                        .contentType(MediaType.APPLICATION_JSON)) // Set content type to JSON
                .andExpect(status().isOk());

        // get study from database and check that root network has been updated with new case
        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        RootNetworkEntity updatedRootNetwork = updatedStudyEntity.getFirstRootNetwork();
//        assertEquals(newCaseUuid, updatedRootNetwork.getCaseUuid());
        assertEquals(newCaseFormat, updatedRootNetwork.getCaseFormat());
        assertFalse(caseService.caseExists(CASE_UUID));
    }

}
