package org.gridsuite.study.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.CaseImportReceiver;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.ConsumerService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.RootNetworkService;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class RootNetworkTest {
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

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

        // start server
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        networkConversionService.setNetworkConversionServerBaseUri(baseUrlWireMock);
        wireMockUtils = new WireMockUtils(wireMockServer);
    }

    @Test
    void testCreateRootNetworkRequest() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        Map<String, String> importParameters = new HashMap<>();
        importParameters.put("param1", "value1");
        importParameters.put("param2", "value2");
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
            .willReturn(WireMock.ok())).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}", studyEntity.getId(), caseUuid, caseFormat)
                .header("userId", "userId")
                .header("content-type", "application/json")
                .content(objectMapper.writeValueAsString(importParameters)))
            .andExpect(status().isOk());

        wireMockUtils.verifyPostRequest(stubId, "/v1/networks",
            false,
            Map.of("caseUuid", WireMock.equalTo(caseUuid.toString()),
                "caseFormat", WireMock.equalTo(caseFormat),
                "receiver", WireMock.matching(".*rootNetworkUuid.*")),
                objectMapper.writeValueAsString(importParameters)
        );
    }

    @Test
    void testCreateRootNetworkRequestOnNotExistingStudy() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}", UUID.randomUUID(), caseUuid, caseFormat)
                .header("userId", "userId"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreateRootNetworkConsumer() throws Exception {
        // create study with first root network
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);

        // prepare all headers that will be sent to consumer supposed to receive "caseImportSucceeded" message
        UUID newRootNetworkUuid = UUID.randomUUID();
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, REPORT_UUID2, "userId", 0L);
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
}
