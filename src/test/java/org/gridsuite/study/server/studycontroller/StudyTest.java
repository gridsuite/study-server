/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.powsybl.network.store.client.PreloadingStrategy;
import com.powsybl.network.store.model.VariantInfos;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.modification.ModificationApplicationContext;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.gridsuite.study.server.dto.modification.NetworkModificationsResult;
import org.gridsuite.study.server.dto.networkexport.NetworkExportReceiver;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.DirectoryService;
import org.gridsuite.study.server.service.StudyServerExecutionService;
import org.gridsuite.study.server.utils.MatcherReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.HEADER_ERROR;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.notification.NotificationService.*;
import static org.gridsuite.study.server.utils.JsonUtils.getModificationContextJsonString;
import static org.gridsuite.study.server.utils.MatcherBasicStudyInfos.createMatcherStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherCreatedStudyBasicInfos.createMatcherCreatedStudyBasicInfos;
import static org.gridsuite.study.server.utils.MatcherStudyInfos.createMatcherStudyInfos;
import static org.gridsuite.study.server.utils.TestUtils.USER_DEFAULT_PROFILE_JSON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudyTest extends StudyTestBase {

    @Test
    void test() throws Exception {
        MvcResult result;
        String resultAsString;
        String userId = "userId";

        //empty list
        mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //empty list
        mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON), content().string("[]"));

        //insert a study
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        // check the study
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        StudyInfos infos = mapper.readValue(resultAsString, StudyInfos.class);

        assertThat(infos, createMatcherStudyInfos(studyUuid));

        //insert a study with a non-existing case and except exception
        UUID stubCaseNotExistsId = wireMockStubs.caseServer.stubCaseExists(NOT_EXISTING_CASE_UUID, false);
        result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}",
                NOT_EXISTING_CASE_UUID, "false").header(USER_ID_HEADER, "userId").param(CASE_FORMAT, "XIIDM"))
            .andExpectAll(status().isNotFound(), content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)).andReturn();
        var problemDetail = mapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals("The case '" + NOT_EXISTING_CASE_UUID + "' does not exist", problemDetail.getDetail());

        wireMockStubs.caseServer.verifyCaseExists(stubCaseNotExistsId, NOT_EXISTING_CASE_UUID);

        result = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(0), createMatcherCreatedStudyBasicInfos(studyUuid));

        //insert the same study but with another user (should work)
        //even with the same name should work
        studyUuid = createStudyWithStubs("userId2", CASE_UUID);

        resultAsString = mockMvc.perform(get("/v1/studies").header("userId", "userId2"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();

        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(createdStudyBasicInfosList.get(1),
            createMatcherCreatedStudyBasicInfos(studyUuid));

        UUID randomUuid = UUID.randomUUID();
        //get a non-existing study -> 404 not found
        result = mockMvc.perform(get("/v1/studies/{studyUuid}", randomUuid).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isNotFound(),
                content().contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            .andReturn();
        problemDetail = mapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals("Study not found", problemDetail.getDetail());

        UUID studyNameUserIdUuid = studyRepository.findAll().get(0).getId();

        // expect only 1 study (public one) since the other is private and we use
        // another userId
        result = mockMvc.perform(get("/v1/studies").header("userId", "a"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();

        resultAsString = result.getResponse().getContentAsString();
        createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(2, createdStudyBasicInfosList.size());

        //get available export format
        UUID stubExportFormatsId = wireMockStubs.networkConversionServer.stubExportFormats("[\"CGMES\",\"UCTE\",\"XIIDM\"]");
        mockMvc.perform(get("/v1/export-network-formats")).andExpectAll(status().isOk(),
            content().string("[\"CGMES\",\"UCTE\",\"XIIDM\"]"));

        wireMockStubs.networkConversionServer.verifyExportFormats(stubExportFormatsId);

        //export a network
        UUID stubNetworkExportId = wireMockStubs.networkConversionServer.stubNetworkExport(NETWORK_UUID_STRING, "XIIDM", UUID.randomUUID().toString());
        UUID rootNodeUuid = getRootNodeUuid(studyNameUserIdUuid);
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName",
            studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM").header(HEADER_USER_ID, userId)).andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("fileName", WireMock.equalTo("myFileName")));

        stubNetworkExportId = wireMockStubs.networkConversionServer.stubNetworkExport(NETWORK_UUID_STRING, "XIIDM", UUID.randomUUID().toString());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName&formatParameters=%7B%22iidm.export.xml.indent%22%3Afalse%7D", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "XIIDM")
            .header(HEADER_USER_ID, userId)).andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("fileName", WireMock.equalTo("myFileName")));

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 3", userId);
        UUID modificationNode1Uuid = modificationNode1.getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName", studyNameUserIdUuid, firstRootNetworkUuid, modificationNode1Uuid, "XIIDM")
            .header(HEADER_USER_ID, userId)).andExpect(status().isInternalServerError());

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode1.getId(), studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid),
            RootNetworkNodeInfo.builder().nodeBuildStatus(NodeBuildStatus.from(BuildStatus.BUILT)).build());

        stubNetworkExportId = wireMockStubs.networkConversionServer.stubNetworkExport(NETWORK_UUID_STRING, "XIIDM", UUID.randomUUID().toString());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}?fileName=myFileName",
                studyNameUserIdUuid,
                firstRootNetworkUuid,
                modificationNode1Uuid,
                "XIIDM").header(HEADER_USER_ID, userId))
            .andExpect(status().isOk());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportId, NETWORK_UUID_STRING, "XIIDM",
            Map.of("variantId", WireMock.equalTo(VARIANT_ID), "fileName", WireMock.equalTo("myFileName")));
    }

    @Test
    void testExportNetworkErrors() throws Exception {
        //insert a study
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        UUID stubNetworkExportErrorId = wireMockStubs.networkConversionServer.stubNetworkExportError(NETWORK_UUID_STRING, "ERROR");

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
                studyUuid, firstRootNetworkUuid, rootNodeUuid, "ERROR")
                .param("fileName", "myFileName")
                .header(HEADER_USER_ID, "userId"))
            .andExpect(status().is5xxServerError());

        wireMockStubs.networkConversionServer.verifyNetworkExport(stubNetworkExportErrorId, NETWORK_UUID_STRING, "ERROR",
            Map.of("fileName", WireMock.equalTo("myFileName")));
    }

    @Test
    void testConsumeNetworkExportFinishedSuccess() throws Exception {
        String userId = "userId";
        UUID studyUuid = createStudyWithStubs(userId, CASE_UUID);
        NetworkExportReceiver receiver = new NetworkExportReceiver(studyUuid, userId);
        String receiverJson = mapper.writeValueAsString(receiver);
        String encodedReceiver = URLEncoder.encode(receiverJson, StandardCharsets.UTF_8);
        String errorMessage = "error";
        Map<String, Object> headers = new HashMap<>();
        headers.put(HEADER_RECEIVER, encodedReceiver);
        headers.put(HEADER_EXPORT_UUID, EXPORT_UUID.toString());
        headers.put(HEADER_ERROR, errorMessage);
        Message<String> message = new GenericMessage<>("", headers);
        consumeService.consumeNetworkExportFinished(message);
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        assertEquals(EXPORT_UUID, mess.getHeaders().get(HEADER_EXPORT_UUID));
    }

    @Test
    void testCreateStudyWithDuplicateCase() throws Exception {
        createStudyWithDuplicateCase("userId", CASE_UUID);
    }

    @Test
    void testDeleteStudy() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setVoltageInitParametersUuid(UUID.randomUUID()); // does not have default params
        studyEntity.setNetworkVisualizationParametersUuid(UUID.randomUUID());
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyEntity.setWorkspacesConfigUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();
        UUID stubDeleteCaseId = wireMockStubs.caseServer.stubDeleteCase(CASE_UUID_STRING);
        DeleteStudyStubs deleteStudyStubs = setupDeleteStudyStubs();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        wireMockStubs.caseServer.verifyDeleteCase(stubDeleteCaseId, CASE_UUID_STRING);
        deleteStudyStubs.verify(wireMockStubs, 7); // voltageInit, loadFlow, securityAnalysis, sensitivityAnalysis, stateEstimation, pccMin, dynamic
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testDeleteStudyWithError(final CapturedOutput capturedOutput) throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyEntity.setLoadFlowParametersUuid(null);
        studyEntity.setSecurityAnalysisParametersUuid(null);
        studyEntity.setVoltageInitParametersUuid(null);
        studyEntity.setSensitivityAnalysisParametersUuid(null);
        studyEntity.setStateEstimationParametersUuid(null);
        studyEntity.setPccMinParametersUuid(null);
        studyEntity.setSpreadsheetConfigCollectionUuid(UUID.randomUUID());
        studyEntity.setWorkspacesConfigUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);

        // We ignore error when remote data async remove
        // Log only
        doAnswer(invocation -> {
            throw new InterruptedException();
        }).when(caseService).deleteCase(any());
        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();
        DeleteStudyStubs deleteStudyStubs = setupDeleteStudyStubs();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk());
        assertTrue(capturedOutput.getOut().contains(StudyServerExecutionService.class.getName() + " - " + CompletionException.class.getName() + ": " + InterruptedException.class.getName()));

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        deleteStudyStubs.verify(wireMockStubs, 1); // loadflow, security, sensitivity, stateEstimation, shortCircuit, pccMin
    }

    @Test
    void testDeleteStudyWithNonExistingCase() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID nonExistingCaseUuid = UUID.randomUUID();

        UUID stubUuid = wireMockStubs.stubNetworkModificationDeleteGroup();

        // Changing the study case uuid with a non-existing case
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElse(null);
        assertNotNull(studyEntity);
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(studyUuid);
        rootNetworkEntity.setCaseUuid(nonExistingCaseUuid);
        rootNetworkRepository.save(rootNetworkEntity);

        UUID stubDeleteCaseId = wireMockStubs.caseServer.stubDeleteCase(nonExistingCaseUuid.toString());
        DeleteStudyStubs deleteStudyStubs = setupDeleteStudyStubs();

        mockMvc.perform(delete("/v1/studies/{studyUuid}", studyUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());

        wireMockStubs.verifyNetworkModificationDeleteGroup(stubUuid, false);
        wireMockStubs.caseServer.verifyDeleteCase(stubDeleteCaseId, nonExistingCaseUuid.toString());
        deleteStudyStubs.verify(wireMockStubs, 7);
    }

    @Test
    void testMetadata() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID oldStudyUuid = studyUuid;

        studyUuid = createStudyWithStubs("userId2", CASE_UUID);

        MvcResult mvcResult = mockMvc
            .perform(get("/v1/studies/metadata?ids="
                + Stream.of(oldStudyUuid, studyUuid).map(Object::toString).collect(Collectors.joining(",")))
                .header(USER_ID_HEADER, "userId"))
            .andExpectAll(status().isOk(), content().contentType(MediaType.APPLICATION_JSON)).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> createdStudyBasicInfosList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertNotNull(createdStudyBasicInfosList);
        assertEquals(2, createdStudyBasicInfosList.size());
        if (!createdStudyBasicInfosList.get(0).getId().equals(oldStudyUuid)) {
            Collections.reverse(createdStudyBasicInfosList);
        }
        assertTrue(createMatcherCreatedStudyBasicInfos(oldStudyUuid)
            .matchesSafely(createdStudyBasicInfosList.get(0)));
        assertTrue(createMatcherCreatedStudyBasicInfos(studyUuid)
            .matchesSafely(createdStudyBasicInfosList.get(1)));
    }

    @Test
    void testNotifyStudyMetadataUpdated() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        mockMvc.perform(post("/v1/studies/{studyUuid}/notification", studyUuid)
                .header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        checkStudyMetadataUpdatedMessagesReceived();
    }

    @Test
    void testLogsReport() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/reports/[^/]+"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(REPORT_TEST))));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/parent-nodes-report?reportType=NETWORK_MODIFICATION", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk()).andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<Report> reports = mapper.readValue(resultAsString, new TypeReference<>() { });
        assertEquals(1, reports.size());
        assertThat(reports.get(0), new MatcherReport(REPORT_TEST));
        wireMockStubs.verifyGetReport();
    }

    @Test
    void testGetSearchTermMatchesInFilteredLogs() throws Exception {
        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        UUID rootNodeUuid = getRootNodeUuid(studyUuid);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        wireMockStubs.stubGetReportsLogs(mapper.writeValueAsString(REPORT_PAGE));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?reportId=" + REPORT_ID + "&searchTerm=testTerm&pageSize=10", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearchWithReportId(REPORT_ID.toString(), "testTerm", 10);

        //test with severityFilter and messageFilter param
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/report/logs/search?reportId=" + REPORT_ID + "&searchTerm=testTerm&pageSize=10&severityLevels=WARN&message=testMsgFilter", studyUuid, firstRootNetworkUuid, rootNodeUuid).header(USER_ID_HEADER, "userId"))
            .andExpect(status().isOk());
        wireMockStubs.verifyGetReportLogsSearchWithReportId(REPORT_ID.toString(), "testTerm", 10, "WARN", "testMsgFilter");
    }

    private UUID createStudyWithDuplicateCase(String userId, UUID caseUuid) throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(userId, null, caseUuid.toString());
        setupCreateParametersStubs();
        UUID stubDisableCaseExpirationClonedId = wireMockStubs.caseServer.stubDisableCaseExpiration(CLONED_CASE_UUID_STRING);
        UUID stubDuplicateCaseId = wireMockStubs.caseServer.stubDuplicateCaseWithBody(CASE_UUID_STRING, mapper.writeValueAsString(CLONED_CASE_UUID));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        // When duplicateCase=true, the network is imported with the cloned case UUID, not the original one
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(CLONED_CASE_UUID_STRING, FIRST_VARIANT_ID, NETWORK_INFOS, "UCTE", countDownLatch);

        MvcResult result = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", caseUuid)
                .param("duplicateCase", "true")
                .param(CASE_FORMAT, "UCTE")
                .header("userId", userId))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = result.getResponse().getContentAsString();
        BasicStudyInfos infos = mapper.readValue(resultAsString, BasicStudyInfos.class);
        UUID studyUuid = infos.getId();

        countDownLatch.await();

        assertStudyCreation(studyUuid, userId);

        // Verify HTTP requests
        // note: it's a new case UUID
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, CLONED_CASE_UUID_STRING, FIRST_VARIANT_ID);
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(1, 7, 1, 1, 1);
        wireMockStubs.caseServer.verifyDuplicateCase(stubDuplicateCaseId, caseUuid.toString());
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationClonedId, CLONED_CASE_UUID_STRING);
        return studyUuid;
    }

    @Test
    void testGetNullNetwork() {
        // just for test coverage
        assertNull(networkService.getNetwork(UUID.randomUUID(), PreloadingStrategy.COLLECTION, null));
    }

    @Test
    void testCreateStudyWithErrorDuringCaseImport() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkServerErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithServerError(CASE_UUID_CAUSING_IMPORT_ERROR, FIRST_VARIANT_ID, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_IMPORT_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_IMPORT_ERROR)
                .header("userId", userId)
                .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().isInternalServerError());

        countDownLatch.await();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");

        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        MvcResult mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        String resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_IMPORT_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkServerErrorId, CASE_UUID_CAUSING_IMPORT_ERROR, FIRST_VARIANT_ID);
    }

    @Test
    void testCreateStudyCreationFailedWithoutErrorMessage() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkConversionErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithError(CASE_UUID_CAUSING_CONVERSION_ERROR, FIRST_VARIANT_ID, null, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_CONVERSION_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_CONVERSION_ERROR)
                .header("userId", userId)
                .param(CASE_FORMAT, "XIIDM"))
            .andExpect(status().isOk());

        countDownLatch.await();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // checks that the error message has a default value set
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(DEFAULT_ERROR_MESSAGE, headers.get(NotificationService.HEADER_ERROR));

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_CONVERSION_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkConversionErrorId, CASE_UUID_CAUSING_CONVERSION_ERROR, FIRST_VARIANT_ID);
    }

    @Test
    void testCreateStudyWithErrorDuringStudyCreation() throws Exception {
        String userId = "userId";
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID stubPostNetworkConversionErrorId = wireMockStubs.networkConversionServer.stubImportNetworkWithError(CASE_UUID_CAUSING_STUDY_CREATION_ERROR, FIRST_VARIANT_ID, STUDY_CREATION_ERROR_MESSAGE, countDownLatch);
        UUID stubCaseExistsId = wireMockStubs.caseServer.stubCaseExists(CASE_UUID_CAUSING_STUDY_CREATION_ERROR, true);

        mockMvc.perform(post("/v1/studies/cases/{caseUuid}", CASE_UUID_CAUSING_STUDY_CREATION_ERROR)
                .header("userId", userId)
                .param(CASE_FORMAT, "UCTE"))
            .andExpect(status().isOk());

        countDownLatch.await();

        // assert that the broker message has been sent a study creation request message
        Message<byte[]> message = output.receive(TIMEOUT, "study.update");
        MessageHeaders headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));

        // study error message
        message = output.receive(TIMEOUT, "study.update");
        headers = message.getHeaders();
        assertEquals(userId, headers.get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDIES, headers.get(HEADER_UPDATE_TYPE));
        assertEquals(STUDY_CREATION_ERROR_MESSAGE, headers.get(NotificationService.HEADER_ERROR));

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExistsId, CASE_UUID_CAUSING_STUDY_CREATION_ERROR);
        wireMockStubs.networkConversionServer.verifyImportNetwork(stubPostNetworkConversionErrorId, CASE_UUID_CAUSING_STUDY_CREATION_ERROR, FIRST_VARIANT_ID);
    }

    @SuppressWarnings({"java:S1481", "java:S1854"}) //TODO found better way to test json result that Sonar wouldn't flag because of unused/useless local variables
    @Test
    void testGetStudyCreationRequests() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        CountDownLatch countDownLatch = new CountDownLatch(1);

        CreateStudyStubs createStudyStubs = setupCreateStudyStubs("userId", USER_DEFAULT_PROFILE_JSON, NEW_STUDY_CASE_UUID);
        setupCreateParametersStubs();

        UUID postNetworkStubId = wireMockStubs.networkConversionServer
            .stubImportNetworkWithPostAction(NEW_STUDY_CASE_UUID, FIRST_VARIANT_ID, NETWORK_INFOS, "UCTE", countDownLatch);
        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(NEW_STUDY_CASE_UUID);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<BasicStudyInfos> bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        // once we checked study creation requests, we can countDown latch to trigger study creation request
        countDownLatch.countDown();

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation
        output.receive(TIMEOUT * 3, studyUpdateDestination);
        // drop the broker message for node creation
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT, studyUpdateDestination);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies").header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

        resultAsString = mvcResult.getResponse().getContentAsString();
        List<CreatedStudyBasicInfos> csbiListResponse = mapper.readValue(resultAsString, new TypeReference<>() { });

        countDownLatch = new CountDownLatch(1);

        //insert a study
        mvcResult = mockMvc.perform(post("/v1/studies/cases/{caseUuid}", NEW_STUDY_CASE_UUID, "false")
                .header(USER_ID_HEADER, "userId")
                .param(CASE_FORMAT, "XIIDM"))
            .andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        BasicStudyInfos bsiResult = mapper.readValue(resultAsString, BasicStudyInfos.class);

        assertThat(bsiResult, createMatcherStudyBasicInfos(studyCreationRequestRepository.findAll().get(0).getId()));

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests", NEW_STUDY_CASE_UUID, "false")
                .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        countDownLatch.countDown();

        // drop the broker message for study creation request (creation)
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation
        output.receive(TIMEOUT * 3);
        // drop the broker message for node creation
        output.receive(TIMEOUT, studyUpdateDestination);
        // drop the broker message for study creation request (deletion)
        output.receive(TIMEOUT, studyUpdateDestination);

        mvcResult = mockMvc.perform(get("/v1/study_creation_requests")
                .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        bsiListResult = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertEquals(List.of(), bsiListResult);

        mvcResult = mockMvc.perform(get("/v1/studies")
                .header(USER_ID_HEADER, "userId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        csbiListResponse = mapper.readValue(resultAsString, new TypeReference<>() { });

        // Verify HTTP requests were sent to remote services
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(1, 7, 1, 1, 1);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, NEW_STUDY_CASE_UUID);
        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, NEW_STUDY_CASE_UUID, FIRST_VARIANT_ID);
    }

    private void checkNodeAliasUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_SPREADSHEET_NODE_ALIASES, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkStudyMetadataUpdatedMessagesReceived() {
        // assert that the broker message has been sent for updating study type
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals("", new String(messageStudyUpdate.getPayload()));
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_METADATA_UPDATED, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasNoParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(NO_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_NO_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();

        createStudy(NO_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(1, 7, 1, 1, 1);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFromNotFound(PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 4, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_LOADFLOW_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultLoadflowUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFrom(PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING, DUPLICATED_LOADFLOW_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_LOADFLOW_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFromNotFound(PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 4, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSecurityAnalysisUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SECURITY_ANALYSIS_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SECURITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFromNotFound(PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 4, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSensitivityAnalysisUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SENSITIVITY_ANALYSIS_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SENSITIVITY_ANALYSIS_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFromNotFound(PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 4, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SHORTCIRCUIT_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultShortcircuitUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFrom(PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING, DUPLICATED_SHORTCIRCUIT_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_SHORTCIRCUIT_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        UUID stubSpreadsheetConfigDuplicateFromNotFoundId = wireMockStubs.stubSpreadsheetConfigDuplicateFromNotFound(PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 1, 0, 1);
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromNotFoundId, PROFILE_SPREADSHEET_CONFIG_COLLECTION_INVALID_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 0, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultSpreadsheetConfigCollectionUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFrom(PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING, DUPLICATED_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, PROFILE_SPREADSHEET_CONFIG_COLLECTION_VALID_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 0, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultNetworkVisualizationsParametersUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        UUID stubNetworkVisualizationParamsDuplicateFromNotFoundId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromNotFound(PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 1, 1);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromNotFoundId, PROFILE_NETWORK_VISUALIZATION_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 1, 0, 0);
    }

    @Test
    void testCreateStudyWithDefaultNetworkVisualizationsParametersUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFrom(PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING, DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, PROFILE_NETWORK_VISUALIZATION_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 5, 1, 0, 0);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasInvalidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(INVALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_INVALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFromNotFound(PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING);

        createStudy(INVALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(1, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_VOLTAGE_INIT_INVALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
    }

    @Test
    void testCreateStudyWithDefaultVoltageInitUserHasValidParamsInProfile() throws Exception {
        CreateStudyStubs createStudyStubs = setupCreateStudyStubs(VALID_PARAMS_IN_PROFILE_USER_ID, USER_PROFILE_VALID_PARAMS_JSON, CASE_UUID_STRING);
        setupCreateParametersStubs();
        DuplicateParameterStubs duplicateParameterStubs = setupDuplicateParametersStubs();
        wireMockStubs.computationServer.stubParametersDuplicateFrom(PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING, DUPLICATED_VOLTAGE_INIT_PARAMS_JSON);

        createStudy(VALID_PARAMS_IN_PROFILE_USER_ID, CASE_UUID);

        // order is important
        createStudyStubs.verify(wireMockStubs);
        verifyCreateParameters(0, 3, 0, 0, 1);
        wireMockStubs.computationServer.verifyParametersDuplicateFrom(PROFILE_VOLTAGE_INIT_VALID_PARAMETERS_UUID_STRING);
        duplicateParameterStubs.verify(wireMockStubs, 4, 1, 1, 0);
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
        studyEntity.setWorkspacesConfigUuid(UUID.randomUUID());
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
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
        studyEntity.setWorkspacesConfigUuid(null);
        studyRepository.save(studyEntity);
        testDuplicateStudy(study1Uuid, firstRootNetworkUuid, "userId");
    }

    @Test
    void testDuplicateStudyWithErrorDuringCaseDuplication() throws Exception {
        UUID stubParametersDuplicateFromId = wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
        UUID stubWorkspacesConfigDuplicateFromId = wireMockStubs.stubWorkspacesConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));

        UUID studyUuid = createStudyWithStubs("userId", CASE_UUID);
        StudyEntity studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        studyRepository.save(studyEntity);

        doAnswer(invocation -> {
            throw new RuntimeException();
        }).when(caseService).duplicateCase(any(), any());

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
        wireMockStubs.verifyWorkspacesConfigDuplicateFromAny(stubWorkspacesConfigDuplicateFromId, 1);
    }

    private StudyEntity duplicateStudy(UUID studyUuid, String userId) throws Exception {
        // Network reindex stubs - using scenarios for stateful behavior
        UUID stubReindexAllId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("indexed")
            .willReturn(WireMock.ok())).getId();
        UUID stubUuid = wireMockStubs.stubDuplicateModificationGroup(mapper.writeValueAsString(Map.of()));
        UUID stubDuplicateCaseId = wireMockStubs.caseServer.stubDuplicateCaseWithBody(CASE_UUID_STRING, mapper.writeValueAsString(CLONED_CASE_UUID));
        UUID stubReportsDuplicateId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/reports/.*/duplicate"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(mapper.writeValueAsString(UUID.randomUUID())))).getId();

        wireMockStubs.stubParametersDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubSpreadsheetConfigDuplicateFromId = wireMockStubs.stubSpreadsheetConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));
        UUID stubNetworkVisualizationParamsDuplicateFromId = wireMockStubs.stubNetworkVisualizationParamsDuplicateFromAny(DUPLICATED_NETWORK_VISUALIZATION_PARAMS_JSON);
        UUID stubWorkspacesConfigDuplicateFromId = wireMockStubs.stubWorkspacesConfigDuplicateFromAny(mapper.writeValueAsString(UUID.randomUUID()));

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
        if (sourceStudy.getWorkspacesConfigUuid() == null) {
            assertNull(duplicatedStudy.getWorkspacesConfigUuid());
        } else {
            assertNotNull(duplicatedStudy.getWorkspacesConfigUuid());
        }

        // Verify HTTP requests
        RootNetworkEntity rootNetworkEntity = studyTestUtils.getOneRootNetwork(duplicatedStudy.getId());
        wireMockStubs.verifyReindexAll(stubReindexAllId, rootNetworkEntity.getNetworkUuid().toString());
        wireMockStubs.caseServer.verifyDuplicateCase(stubDuplicateCaseId, CASE_UUID_STRING, "false");
        if (sourceStudy.getVoltageInitParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getVoltageInitParametersUuid().toString());
        }
        if (sourceStudy.getLoadFlowParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getLoadFlowParametersUuid().toString());
        }
        if (sourceStudy.getShortCircuitParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getShortCircuitParametersUuid().toString());
        }
        if (sourceStudy.getSecurityAnalysisParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getSecurityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getSensitivityAnalysisParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getSensitivityAnalysisParametersUuid().toString());
        }
        if (sourceStudy.getNetworkVisualizationParametersUuid() != null) {
            wireMockStubs.verifyNetworkVisualizationParamsDuplicateFrom(stubNetworkVisualizationParamsDuplicateFromId, sourceStudy.getNetworkVisualizationParametersUuid().toString());
        }
        if (sourceStudy.getStateEstimationParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getStateEstimationParametersUuid().toString());
        }
        if (sourceStudy.getPccMinParametersUuid() != null) {
            wireMockStubs.computationServer.verifyParametersDuplicateFrom(sourceStudy.getPccMinParametersUuid().toString());
        }
        if (sourceStudy.getSpreadsheetConfigCollectionUuid() != null) {
            wireMockStubs.verifySpreadsheetConfigDuplicateFrom(stubSpreadsheetConfigDuplicateFromId, sourceStudy.getSpreadsheetConfigCollectionUuid().toString());
        }
        if (sourceStudy.getWorkspacesConfigUuid() != null) {
            wireMockStubs.verifyWorkspacesConfigDuplicateFromAny(stubWorkspacesConfigDuplicateFromId, 1);
        }
        wireMockStubs.verifyReportsDuplicate(stubReportsDuplicateId);

        return duplicatedStudy;
    }

    @Test
    void testGetDefaultProviders() throws Exception {
        UUID stubDefaultProviderId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/default-provider"))
            .willReturn(WireMock.ok().withBody(DEFAULT_PROVIDER))).getId();
        // related to LoadFlowTest::testGetDefaultProvidersFromProfile but without a user, so it doesn't use profiles
        mockMvc.perform(get("/v1/loadflow-default-provider")).andExpectAll(
            status().isOk(),
            content().string(DEFAULT_PROVIDER));
        mockMvc.perform(get("/v1/security-analysis-default-provider")).andExpectAll(
            status().isOk(),
            content().string(DEFAULT_PROVIDER));
        mockMvc.perform(get("/v1/sensitivity-analysis-default-provider")).andExpectAll(
            status().isOk(),
            content().string(DEFAULT_PROVIDER));

        wireMockStubs.verifyDefaultProvider(stubDefaultProviderId, 3);
    }

    @Test
    void reindexRootNetworkTest() throws Exception {
        // Network reindex stubs - using scenarios for stateful behavior
        // NOT_EXISTING_NETWORK always returns 404
        UUID stubReindexAllNotFoundId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/reindex-all"))
            .willReturn(WireMock.notFound())).getId();
        // First reindex call returns 200 and transitions to "indexed" state
        UUID stubReindexAllId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willSetStateTo("indexed")
            .willReturn(WireMock.ok())).getId();
        // Subsequent reindex calls return 500 (server error)
        UUID stubReindexAllErrorId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/.*/reindex-all"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs("indexed")
            .willReturn(WireMock.serverError())).getId();

        // Indexed equipments stubs - using scenarios for stateful behavior
        // NOT_EXISTING_NETWORK always returns 404
        UUID stubIndexedEquipmentsNotFoundId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathEqualTo("/v1/networks/" + NOT_EXISTING_NETWORK_UUID + "/indexed-equipments"))
            .willReturn(WireMock.notFound())).getId();
        // Before reindex: return 204 (no content = not indexed)
        UUID stubIndexedEquipmentsNoContentId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathMatching("/v1/networks/.*/indexed-equipments"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(WireMock.noContent())).getId();
        // After reindex: return 200 (ok = indexed)
        UUID stubIndexedEquipmentsId = wireMockServer.stubFor(WireMock.head(WireMock.urlPathMatching("/v1/networks/.*/indexed-equipments"))
            .atPriority(10)
            .inScenario("reindex")
            .whenScenarioStateIs("indexed")
            .willReturn(WireMock.ok())).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", UUID.randomUUID(), UUID.randomUUID()))
            .andExpectAll(status().isNotFound());

        UUID notExistingNetworkStudyUuid = createStudyWithStubs("userId", NOT_EXISTING_NETWORK_CASE_UUID, NOT_EXISTING_NETWORK_INFOS);
        UUID notExistingNetworkRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(notExistingNetworkStudyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpect(status().isNotFound());
        Message<byte[]> indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        Message<byte[]> indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        wireMockStubs.verifyReindexAll(stubReindexAllNotFoundId, NOT_EXISTING_NETWORK_UUID.toString());

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", notExistingNetworkStudyUuid, notExistingNetworkRootNetworkUuid))
            .andExpectAll(status().isNotFound());

        wireMockStubs.verifyIndexedEquipments(stubIndexedEquipmentsNotFoundId, NOT_EXISTING_NETWORK_UUID.toString());

        UUID study1Uuid = createStudyWithStubs("userId", CASE_UUID);
        UUID study1RootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(study1Uuid);

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", study1Uuid, study1RootNetworkUuid))
            .andExpectAll(status().isOk(),
                content().string("NOT_INDEXED"));
        indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        wireMockStubs.verifyIndexedEquipments(stubIndexedEquipmentsNoContentId, NETWORK_UUID_STRING);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", study1Uuid, study1RootNetworkUuid))
            .andExpect(status().isOk());

        indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        Message<byte[]> indexationStatusMessageDone = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(study1Uuid, indexationStatusMessageDone.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_INDEXATION_STATUS, indexationStatusMessageDone.getHeaders().get(HEADER_UPDATE_TYPE));
        wireMockStubs.verifyReindexAll(stubReindexAllId, NETWORK_UUID_STRING);

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/indexation/status", study1Uuid, study1RootNetworkUuid))
            .andExpectAll(status().isOk(),
                content().string("INDEXED"));

        wireMockStubs.verifyIndexedEquipments(stubIndexedEquipmentsId, NETWORK_UUID_STRING);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/reindex-all", study1Uuid, study1RootNetworkUuid))
            .andExpect(status().is5xxServerError());
        indexationStatusMessageOnGoing = output.receive(TIMEOUT, studyUpdateDestination);
        indexationStatusMessageNotIndexed = output.receive(TIMEOUT, studyUpdateDestination);

        wireMockStubs.verifyReindexAll(stubReindexAllErrorId, NETWORK_UUID_STRING);
    }

    @Test
    void providerTest() throws Exception {
        UUID stubParametersProviderId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/parameters/.*/provider"))
            .willReturn(WireMock.ok())).getId();
        UUID studyUuid = createStudyWithStubs(USER_ID_HEADER, CASE_UUID);
        assertNotNull(studyUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/loadflow/provider", studyUuid)
                .content("SuperLF")
                .contentType(MediaType.TEXT_PLAIN)
                .header(USER_ID_HEADER, USER_ID_HEADER))
            .andExpect(status().isOk());
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

        assertNotNull(output.receive(TIMEOUT, elementUpdateDestination));

        mockMvc.perform(post("/v1/studies/{studyUuid}/security-analysis/provider", studyUuid)
                .content("SuperSA")
                .contentType(MediaType.TEXT_PLAIN)
                .header(USER_ID_HEADER, USER_ID_HEADER))
            .andExpect(status().isOk());
        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, message.getHeaders().get(HEADER_UPDATE_TYPE));

        message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(UPDATE_TYPE_COMPUTATION_PARAMETERS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertNotNull(output.receive(TIMEOUT, elementUpdateDestination));

        wireMockStubs.verifyParametersProvider(stubParametersProviderId, 2);
    }

    @Test
    void testExportNetworkSuccess() throws Exception {

        String userId = "userId";
        String description = "description";
        String fileName = "myFileName";

        UUID directoryUuid = UUID.randomUUID();
        UUID exportUuid = UUID.randomUUID();

        UUID studyUuid = createStudyWithStubs(USER_ID_HEADER, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        UUID nodeUuid = getRootNodeUuid(studyUuid);

        wireMockStubs.directoryServer.stubElementExists(directoryUuid, fileName, DirectoryService.CASE, HttpStatus.NO_CONTENT.value());
        wireMockStubs.networkConversionServer.stubExportNetwork(NETWORK_UUID, fileName, mapper.writeValueAsString(exportUuid), HttpStatus.OK.value());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
            studyUuid, firstRootNetworkUuid, nodeUuid, "XIIDM")
            .param("fileName", fileName)
            .param("exportToGridExplore", Boolean.TRUE.toString())
            .param("parentDirectoryUuid", directoryUuid.toString())
            .param("description", description)
            .header(USER_ID_HEADER, userId)).andExpect(status().isOk());

        wireMockStubs.directoryServer.verifyElementExists(directoryUuid, fileName, DirectoryService.CASE);
        wireMockStubs.networkConversionServer.verifyExportNetwork(NETWORK_UUID, fileName);
    }

    @Test
    void testExportNetworkFailCaseExists() throws Exception {

        String description = "description";
        String fileName = "myFileName";
        UUID directoryUuid = UUID.randomUUID();

        UUID studyUuid = createStudyWithStubs(USER_ID_HEADER, CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);

        UUID nodeUuid = getRootNodeUuid(studyUuid);

        wireMockStubs.directoryServer.stubElementExists(directoryUuid, fileName, DirectoryService.CASE, HttpStatus.OK.value());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
            studyUuid, firstRootNetworkUuid, nodeUuid, "XIIDM")
            .param("fileName", fileName)
            .param("exportToGridExplore", Boolean.TRUE.toString())
            .param("parentDirectoryUuid", directoryUuid.toString())
            .param("description", description)
            .header(USER_ID_HEADER, "userId")).andExpect(status().isInternalServerError());

        wireMockStubs.directoryServer.verifyElementExists(directoryUuid, fileName, DirectoryService.CASE);
    }
}
