package org.gridsuite.study.server;

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
    UUID NETWORK_UUID = UUID.randomUUID();
    UUID CASE_UUID = UUID.randomUUID();
    String CASE_NAME = "caseName";
    String CASE_FORMAT = "caseFormat";
    UUID REPORT_UUID = UUID.randomUUID();

    // 2nd root network
    UUID NETWORK_UUID2 = UUID.randomUUID();
    String NETWORK_ID2 = "networkId2";
    UUID CASE_UUID2 = UUID.randomUUID();
    String CASE_NAME2 = "caseName2";
    String CASE_FORMAT2 = "caseFormat2";
    UUID REPORT_UUID2 = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

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

    @BeforeEach
    void setUp() throws Exception {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

        // start server
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        networkConversionService.setNetworkConversionServerBaseUri(baseUrlWireMock);
    }

    @Test
    void testCreateRootNetworkRequest() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);

        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "newCaseFormat";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks\\?caseUuid=.*"))
            .willReturn(WireMock.ok()));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks?caseUuid={caseUuid}&caseFormat={caseFormat}", studyEntity.getId(), caseUuid, caseFormat)
                .header("userId", "userId"))
            .andExpect(status().isOk());

//        wireMockUtils.verifyPostRequest();
    }

    @Test
    void testCreateRootNetworkConsumer() throws Exception {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        UUID newRootNetworkUuid = UUID.randomUUID();

        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyEntity.getId(), newRootNetworkUuid, CASE_UUID2, REPORT_UUID2, "userId", 0L);
        Map<String, Object> headers = new HashMap<>();
        headers.put("networkUuid", NETWORK_UUID2.toString());
        headers.put("networkId", NETWORK_ID2);
        headers.put("caseFormat", CASE_FORMAT2);
        headers.put("caseName", CASE_NAME2);
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(caseImportReceiver));
//        headers.put(HEADER_IMPORT_PARAMETERS, ")");

        Mockito.doNothing().when(caseService).disableCaseExpiration(CASE_UUID2);
        messageConsumer.accept(new GenericMessage<>("", headers));

        StudyEntity updatedStudyEntity = studyRepository.findWithRootNetworksById(studyEntity.getId()).orElseThrow(() -> new StudyException(StudyException.Type.STUDY_NOT_FOUND));
        assertEquals(2, updatedStudyEntity.getRootNetworks().size());

        RootNetworkEntity rootNetworkEntity = updatedStudyEntity.getRootNetworks().stream().filter(rne -> rne.getId().equals(newRootNetworkUuid)).findFirst().orElseThrow(() -> new StudyException(StudyException.Type.ROOTNETWORK_NOT_FOUND));
        assertEquals(newRootNetworkUuid, rootNetworkEntity.getId());
        assertEquals(NETWORK_UUID2, rootNetworkEntity.getNetworkUuid());
        assertEquals(NETWORK_ID2, rootNetworkEntity.getNetworkId());
        assertEquals(CASE_FORMAT2, rootNetworkEntity.getCaseFormat());
        assertEquals(CASE_NAME2, rootNetworkEntity.getCaseName());
        assertEquals(CASE_UUID2, rootNetworkEntity.getReportUuid());
        assertEquals(REPORT_UUID2, rootNetworkEntity.getReportUuid());
    }
}
