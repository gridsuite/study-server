/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.PowsyblException;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.network.store.client.NetworkStoreService;
import org.gridsuite.study.server.dto.BasicStudyInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.CaseService;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.SendInput;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.HEADER_IMPORT_PARAMETERS;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServiceTest.class);

    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutputDestination output;

    @Autowired
    private InputDestination input;

    @Autowired
    private CaseService caseService;

    @Autowired
    private NetworkConversionService networkConversionService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ObjectMapper mapper;
    private ObjectWriter objectWriter;

    private static final long TIMEOUT = 1000;
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String CASE_FORMAT_PARAM = "caseFormat";
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String USER_ID_HEADER = "userId";
    private static final String HEADER_UPDATE_TYPE = "updateType";
    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f4");
    private static final UUID SHORTCIRCUIT_PARAMETERS_UUID = UUID.fromString("00000000-bd22-4a75-83d3-9e530245c7f4");

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private TestUtils studyTestUtils;

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private LoadFlowService loadFlowService;

    @MockBean
    private ShortCircuitService shortCircuitService;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort().extensions(new SendInput(input)));
        wireMockUtils = new WireMockUtils(wireMockServer);

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        wireMockServer.start();

        caseService.setCaseServerBaseUri(wireMockServer.baseUrl());
        networkConversionService.setNetworkConversionServerBaseUri(wireMockServer.baseUrl());
        reportService.setReportServerBaseUri(wireMockServer.baseUrl());

    }

    private static final String STUDY_UPDATE_DESTINATION = "study.update";

    @Test
    void testCheckNetworkExistenceReturnsOk() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");
        String userId = "userId";
        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());
    }

    @Test
    void testCheckNetworkExistenceReturnsNotContent() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");
        String userId = "userId";
        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenThrow(new PowsyblException("Network '" + NETWORK_UUID + "' not found"));
        mockMvc.perform(head("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isNoContent());
    }

    @Test
    void testRecreateStudyNetworkWithStudyCaseAndImportParameters() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");
        String userId = "userId";

        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), true);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockUtils.stubImportNetwork(CASE_UUID.toString(), importParameters, NETWORK_UUID.toString(), "20140116_0830_2D4_UX1_pst", null, "UCTE", "20140116_0830_2D4_UX1_pst.ucte", countDownLatch);
        UUID disableCaseExpirationStubId = wireMockUtils.stubDisableCaseExpiration(CASE_UUID.toString());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isOk());

        countDownLatch.await();

        // study network recreation done notification
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        MessageHeaders headers = message.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_NETWORK_RECREATION_DONE, headers.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));

        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
        wireMockUtils.verifyImportNetwork(postNetworkStubId, CASE_UUID.toString(), null);
        wireMockUtils.verifyDisableCaseExpiration(disableCaseExpirationStubId, CASE_UUID.toString());
    }

    @Test
    void testRecreateStudyNetworkWithMissingStudyCase() throws Exception {
        Map<String, Object> importParameters = new HashMap<>();
        String userId = "userId";
        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);
        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), false);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .header(USER_ID_HEADER, userId))
            .andExpect(status().isFailedDependency());
        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
    }

    @Test
    void testRecreateStudyNetworkFromExistingCase() throws Exception {
        String userId = "userId";
        Map<String, Object> importParameters = new HashMap<>();
        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);

        Map<String, Object> newImportParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), true);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockUtils.stubImportNetwork(CASE_UUID_STRING, newImportParameters, NETWORK_UUID.toString(), "20140116_0830_2D4_UX1_pst", null, "UCTE", "20140116_0830_2D4_UX1_pst.ucte", countDownLatch);
        UUID disableCaseExpirationStubId = wireMockUtils.stubDisableCaseExpiration(CASE_UUID.toString());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .param(HEADER_IMPORT_PARAMETERS, objectWriter.writeValueAsString(newImportParameters))
                .param("caseUuid", CASE_UUID_STRING)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isOk());

        countDownLatch.await();

        // study network recreation done notification
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        MessageHeaders headers = message.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_NETWORK_RECREATION_DONE, headers.get(NotificationService.HEADER_UPDATE_TYPE));
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));

        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
        wireMockUtils.verifyImportNetwork(postNetworkStubId, CASE_UUID_STRING, null);
        wireMockUtils.verifyDisableCaseExpiration(disableCaseExpirationStubId, CASE_UUID.toString());
    }

    @Test
    void testRecreateStudyNetworkFromUnexistingCase() throws Exception {
        String userId = "userId";
        Map<String, Object> importParameters = new HashMap<>();
        UUID studyUuid = createStudy(userId, CASE_UUID, importParameters);
        UUID firstRootNetworkUuid = studyTestUtils.getStudyFirstRootNetworkUuid(studyUuid);

        Map<String, Object> newImportParameters = new HashMap<>();
        importParameters.put("param1", "changedValue1, changedValue2");
        importParameters.put("param2", "changedValue");

        UUID caseExistsStubId = wireMockUtils.stubCaseExists(CASE_UUID.toString(), false);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/network", studyUuid, firstRootNetworkUuid)
                .param(HEADER_IMPORT_PARAMETERS, objectWriter.writeValueAsString(newImportParameters))
                .param("caseUuid", CASE_UUID_STRING)
                .header(USER_ID_HEADER, userId))
            .andExpectAll(status().isFailedDependency());

        wireMockUtils.verifyCaseExists(caseExistsStubId, CASE_UUID.toString());
    }

    private UUID createStudy(String userId, UUID caseUuid, Map<String, Object> importParameters) throws Exception {
        // mock API calls
        UUID caseExistsStubId = wireMockUtils.stubCaseExists(caseUuid.toString(), true);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockUtils.stubImportNetwork(caseUuid.toString(), importParameters, NETWORK_UUID.toString(), "20140116_0830_2D4_UX1_pst", WireMockUtils.FIRST_VARIANT_ID, "UCTE", "20140116_0830_2D4_UX1_pst.ucte", countDownLatch);
        UUID disableCaseExpirationStubId = wireMockUtils.stubDisableCaseExpiration(caseUuid.toString());
        when(loadFlowService.createDefaultLoadFlowParameters()).thenReturn(LOADFLOW_PARAMETERS_UUID);
        when(shortCircuitService.createParameters(null)).thenReturn(SHORTCIRCUIT_PARAMETERS_UUID);

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                        .header("userId", userId)
                        .param(CASE_FORMAT_PARAM, "UCTE"))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        countDownLatch.await();

        assertStudyCreation(studyUuid, userId);

        // assert API calls have been made
        wireMockUtils.verifyCaseExists(caseExistsStubId, caseUuid.toString());
        wireMockUtils.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), WireMockUtils.FIRST_VARIANT_ID);
        wireMockUtils.verifyDisableCaseExpiration(disableCaseExpirationStubId, caseUuid.toString());

        return studyUuid;
    }

    private void assertStudyCreation(UUID studyUuid, String userId, String... errorMessage) {
        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);

        assertEquals("", new String(message.getPayload()));
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // assert that the broker message has been sent a study creation message for creation
        message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals("", new String(message.getPayload()));
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage.length != 0 ? errorMessage[0] : null, headers.get(NotificationService.HEADER_ERROR));

        assertTrue(studyRepository.findById(studyUuid).isPresent());
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
