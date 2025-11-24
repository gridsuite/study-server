/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.commons.report.ReportNode;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyCreationRequestEntity;
import org.gridsuite.study.server.repository.StudyCreationRequestRepository;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_UPDATE_TYPE;
import static org.gridsuite.study.server.service.NetworkModificationTreeService.FIRST_VARIANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyControllerCreationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyControllerCreationTest.class);
    private static final long TIMEOUT = 1000;

    @Autowired
    private StudyRepository studyRepository;
    @MockitoSpyBean
    private CaseService caseService;
    @Autowired
    private NetworkConversionService networkConversionService;

    @MockitoSpyBean
    private StudyService studyService;
    @MockitoBean
    private ReportService reportService;

    // All these computation services need to be mocked because apps try to create default parameters for each computation app on study creation
    @MockitoBean
    private LoadFlowService loadFlowService;
    @MockitoBean
    private ShortCircuitService shortCircuitService;
    @MockitoBean
    private SecurityAnalysisService securityAnalysisService;
    @MockitoBean
    private SensitivityAnalysisService sensitivityAnalysisService;
    @MockitoBean
    private VoltageInitService voltageInitService;
    @MockitoBean
    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    @MockitoBean
    private StateEstimationService stateEstimationService;
    @MockitoBean
    private StudyConfigService studyConfigService;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ConsumerService consumerService;

    private WireMockServer wireMockServer;
    private WireMockStubs wireMockStubs;

    //output destinations
    private final String studyUpdateDestination = "study.update";
    @Autowired
    private TestUtils testUtils;
    @Autowired
    private StudyCreationRequestRepository studyCreationRequestRepository;
    @Autowired
    private RootNetworkService rootNetworkService;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockStubs = new WireMockStubs(wireMockServer);
        wireMockServer.start();

        caseService.setCaseServerBaseUri(wireMockServer.baseUrl());
        networkConversionService.setNetworkConversionServerBaseUri(wireMockServer.baseUrl());
    }

    @Test
    void testSendStudyCreationRequestWithCaseDuplication() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        UUID duplicateCaseUuid = UUID.randomUUID();
        String caseFormat = "UCTE";
        String userId = "userId";

        doReturn(duplicateCaseUuid).when(caseService).duplicateCase(caseUuid, true);
        UUID caseExistsStub = wireMockStubs.caseServer.stubCaseExists(caseUuid.toString(), true);
        UUID importCaseStub = wireMockStubs.networkConversionServer.stubImportNetwork(duplicateCaseUuid.toString(), null, FIRST_VARIANT_ID, caseFormat);

        sendStudyCreationRequest(userId, caseUuid, caseFormat, null, true);

        // assert that all http requests have been sent to remote services
        wireMockStubs.caseServer.verifyCaseExists(caseExistsStub, caseUuid.toString());
        wireMockStubs.networkConversionServer.verifyImportNetwork(importCaseStub, duplicateCaseUuid.toString(), FIRST_VARIANT_ID);

        UUID newStudyCreationRequestId = studyCreationRequestRepository.findAll().getFirst().getId();
        assertStudyUpdateMessageReceived(newStudyCreationRequestId, userId);
    }

    @Test
    void testSendStudyCreationRequestWithImportParameters() throws Exception {
        UUID caseUuid = UUID.randomUUID();
        Map<String, Object> importParameters = prepareImportParameters();
        String caseFormat = "UCTE";
        String userId = "userId";

        UUID caseExistsStub = wireMockStubs.caseServer.stubCaseExists(caseUuid.toString(), true);

        String importParametersAsJson = mapper.writeValueAsString(importParameters);
        UUID importCaseStub = wireMockStubs.networkConversionServer.stubImportNetwork(caseUuid.toString(), importParametersAsJson, FIRST_VARIANT_ID, caseFormat);

        sendStudyCreationRequest(userId, caseUuid, caseFormat, importParameters, false);

        // assert that all http requests have been sent to remote services
        wireMockStubs.caseServer.verifyCaseExists(caseExistsStub, caseUuid.toString());
        wireMockStubs.networkConversionServer.verifyImportNetwork(importCaseStub, caseUuid.toString(), FIRST_VARIANT_ID, importParametersAsJson);

        UUID newStudyCreationRequestId = studyCreationRequestRepository.findAll().getFirst().getId();
        assertStudyUpdateMessageReceived(newStudyCreationRequestId, userId);
    }

    @Test
    void testConsumeCaseImportSucceededWithParameters() throws JsonProcessingException {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        String userId = "userId";

        // Import parameters to pass and check
        Map<String, Object> importParameters = prepareImportParameters();

        MessageHeaders messageHeaders = prepareMessageHeaders(studyUuid, userId, caseUuid, importParameters);

        // Need to insert studyCreationRequestEntity, otherwise study is deleted after being inserted
        studyCreationRequestRepository.save(new StudyCreationRequestEntity(studyUuid, "firstRootNetworkName"));

        UUID disableCaseExpirationStub = wireMockStubs.caseServer.stubDisableCaseExpiration(caseUuid.toString());
        // run consume case import succeeded
        consumerService.consumeCaseImportSucceeded().accept(MessageBuilder.createMessage("", messageHeaders));
        wireMockStubs.caseServer.verifyDisableCaseExpiration(disableCaseExpirationStub, caseUuid.toString());
        verifyMockCallsAfterStudyCreation();

        // check import parameters are saved
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        UUID rootNetworkUUID = testUtils.getOneRootNetworkUuid(studyEntity.getId());

        Map<String, String> expectedImportParameters = new HashMap<>();
        importParameters.forEach((key, value) -> expectedImportParameters.put(key, value.toString()));
        assertThat(rootNetworkService.getImportParameters(rootNetworkUUID)).usingRecursiveComparison().isEqualTo(expectedImportParameters);

        assertStudyUpdateMessageReceived(studyUuid, userId);
        assertTrue(studyRepository.findById(studyUuid).isPresent());
    }

    private void verifyMockCallsAfterStudyCreation() {
        verify(reportService, Mockito.times(1)).sendReport(any(UUID.class), any(ReportNode.class));
        verify(loadFlowService, Mockito.times(1)).createDefaultLoadFlowParameters();
        verify(shortCircuitService, Mockito.times(1)).createParameters(null);
        verify(securityAnalysisService, Mockito.times(1)).createDefaultSecurityAnalysisParameters();
        verify(sensitivityAnalysisService, Mockito.times(1)).createDefaultSensitivityAnalysisParameters();
        verify(voltageInitService, Mockito.times(1)).createVoltageInitParameters(null);
        verify(dynamicSecurityAnalysisService, Mockito.times(1)).createDefaultParameters();
        verify(stateEstimationService, Mockito.times(1)).createDefaultStateEstimationParameters();
        verify(studyConfigService, Mockito.times(1)).createDefaultSpreadsheetConfigCollection();
    }

    private void sendStudyCreationRequest(String userId, UUID caseUuid, String caseFormat, Map<String, Object> importParameters, boolean duplicateCase) throws Exception {
        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                .header(HEADER_USER_ID, userId).contentType(MediaType.APPLICATION_JSON)
                .param(CASE_FORMAT, caseFormat)
                .param("duplicateCase", String.valueOf(duplicateCase))
                .content(mapper.writeValueAsString(importParameters)))
            .andExpect(status().isOk());
    }

    private MessageHeaders prepareMessageHeaders(UUID studyUuid, String userId, UUID caseUuid, Map<String, Object> importParameters) throws JsonProcessingException {
        CaseImportReceiver receiver = new CaseImportReceiver(studyUuid, null, caseUuid, UUID.randomUUID(), UUID.randomUUID(), userId, System.nanoTime(), CaseImportAction.STUDY_CREATION);

        return new MessageHeaders(Map.of(
            HEADER_USER_ID, userId,
            QUERY_PARAM_NETWORK_UUID, UUID.randomUUID().toString(),
            "networkId", "networkId",
            CASE_FORMAT, "UCTE",
            "caseName", "nameOfCase",
            HEADER_IMPORT_PARAMETERS, importParameters,
            HEADER_RECEIVER, mapper.writeValueAsString(receiver)));
    }

    private Map<String, Object> prepareImportParameters() {
        Map<String, Object> importParameters = new HashMap<>();
        ArrayList<String> randomListParam = new ArrayList<>();
        randomListParam.add("paramValue1");
        randomListParam.add("paramValue2");
        importParameters.put("param1", randomListParam);

        return importParameters;
    }

    private void assertStudyUpdateMessageReceived(UUID studyUuid, String userId) {
        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        studyCreationRequestRepository.deleteAll();

        List<String> destinations = List.of(studyUpdateDestination);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
