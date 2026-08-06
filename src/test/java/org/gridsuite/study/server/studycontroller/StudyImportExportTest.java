/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.CaseExportInfos;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.StudyExportInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyCreationRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.utils.wiremock.NetworkConversionServerStubs;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.gridsuite.study.server.StudyConstants.CASE_FORMAT;
import static org.gridsuite.study.server.StudyConstants.HEADER_ERROR_MESSAGE;
import static org.gridsuite.study.server.StudyConstants.HEADER_IMPORT_PARAMETERS;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class StudyImportExportTest extends StudyTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyService studyService;

    @Autowired
    private RootNetworkRequestRepository rootNetworkRequestRepository;

    @Test
    void testExportStudyArchive() throws Exception {
        // Create a study
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);
        ReflectionTestUtils.setField(caseService, "caseServerBaseUri", wireMockServer.baseUrl());
        // Stub the read-permission check on the study
        wireMockStubs.directoryServer.stubCheckPermission(List.of(studyUuid), null, "testUser", PermissionType.READ, false, HttpStatus.OK.value());
        // Stub the case content download used during export
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + CASE_UUID))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody("dummy case content".getBytes())));
        // Export as archive
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/export-archive", studyUuid).header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=" + studyUuid + ".gz"))
                .andExpect(header().string("Content-Type", "application/gzip"))
                .andReturn();
        // Verify the response contains data
        byte[] archiveContent = result.getResponse().getContentAsByteArray();
        assertNotNull(archiveContent);
        assertTrue(archiveContent.length > 0);
        StudyExportInfos exportInfos = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("tree.json".equals(entry.getName())) {
                    exportInfos = objectMapper.readValue(zis.readAllBytes(), StudyExportInfos.class);
                    break;
                }
            }
        }
        // Verify export structure
        assertNotNull(exportInfos);
        assertEquals(studyUuid, exportInfos.studyUuid());
        assertNotNull(exportInfos.rootNetworks());
        assertEquals(1, exportInfos.rootNetworks().size());
        assertNotNull(exportInfos.rootNetworks().getFirst().importParameters());
        assertNotNull(exportInfos.nodeTree());
        assertEquals("ROOT", exportInfos.nodeTree().type());
        assertNotNull(exportInfos.nodeTree().children());
        assertEquals(1, exportInfos.nodeTree().children().size());
        // Verify the case content download call
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/cases/" + CASE_UUID, false, Map.of(), 1);
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
    }

    @Test
    void testExportStudyArchiveFailNoPermission() throws Exception {
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);
        wireMockStubs.directoryServer.stubCheckPermission(List.of(studyUuid), null, "testUser", PermissionType.READ, false, HttpStatus.FORBIDDEN.value());

        mockMvc.perform(get("/v1/studies/{studyUuid}/export-archive", studyUuid).header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isForbidden());
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
    }

    @Test
    void testImportStudyWithCaseImportAction() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        String caseFormat = "XIIDM";
        String studyName = "Imported Study";
        String description = "Test import";
        UUID parentDirectoryUuid = UUID.randomUUID();

        StudyExportInfos exportInfos = createSampleStudyExportInfos(studyUuid);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("importParameters", Collections.emptyMap());
        requestBody.put("studyExportInfos", exportInfos);
        String requestJson = objectMapper.writeValueAsString(requestBody);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
                .stubImportNetworkWithPostAction(caseUuid.toString(), FIRST_VARIANT_ID, NETWORK_INFOS, caseFormat, countDownLatch);

        wireMockStubs.userAdminServer.stubGetUserProfile("testUser");
        setupCreateParametersStubs();

        List<UUID> nodeGroupUuids = exportInfos.nodeTree().children().stream().map(NodeTreeExportInfos::modificationGroupUuid).filter(Objects::nonNull).toList();

        for (UUID groupUuid : nodeGroupUuids) {
            wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/groups/" + groupUuid + "/duplicate"))
                    .withQueryParam("groupUuid", WireMock.matching(".*"))
                    .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{}")));
        }

        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/directories/" + parentDirectoryUuid + "/elements"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "elementUuid", studyUuid,
                                "elementName", studyName,
                                "type", "STUDY")))));

        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(caseUuid.toString());

        mockMvc.perform(post("/v1/studies/import-with-case-import-action/{caseUuid}", caseUuid)
                        .param("studyUuid", studyUuid.toString())
                        .param("studyName", studyName)
                        .param("description", description)
                        .param("parentDirectoryUuid", parentDirectoryUuid.toString())
                        .header(HEADER_USER_ID, "testUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS), "Timed out waiting for the case-import callback");

        Message<byte[]> startedMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(startedMessage);
        assertEquals("testUser", startedMessage.getHeaders().get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_STARTED, startedMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        Message<byte[]> finishedMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(finishedMessage);
        assertEquals("testUser", finishedMessage.getHeaders().get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, finishedMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), FIRST_VARIANT_ID);
        wireMockStubs.userAdminServer.verifyGetUserProfile("testUser");
        verifyCreateParameters(1, 9, 1, 1, 1);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, caseUuid.toString());

        for (UUID groupUuid : nodeGroupUuids) {
            WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/groups/" + groupUuid + "/duplicate", false, Map.of("groupUuid", WireMock.matching(".*")), null, 1);
        }
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/directories/" + parentDirectoryUuid + "/elements", false, Map.of(), null, 1);
    }

    @Test
    void testConsumeCaseImportSucceededStudyImportMissingContext() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        String userId = "testUser";

        // Simulate a pending STUDY_IMPORT creation request whose import context is missing at callback time
        studyCreationRequestRepository.save(new StudyCreationRequestEntity(studyUuid, "firstRootNetworkName"));

        CaseImportReceiver receiver = new CaseImportReceiver(studyUuid, null, caseUuid, caseUuid, UUID.randomUUID(),
                userId, System.nanoTime(), CaseImportAction.STUDY_IMPORT, true);

        MessageHeaders messageHeaders = new MessageHeaders(Map.of(
                HEADER_USER_ID, userId,
                "networkUuid", UUID.randomUUID().toString(),
                "networkId", "networkId",
                CASE_FORMAT, "XIIDM",
                "caseName", "caseName",
                HEADER_IMPORT_PARAMETERS, Collections.emptyMap(),
                HEADER_RECEIVER, objectMapper.writeValueAsString(receiver)));

        consumeService.consumeCaseImportSucceeded().accept(MessageBuilder.createMessage("", messageHeaders));

        // the failure must be surfaced to the user, not silently swallowed
        Message<byte[]> errorMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(errorMessage);
        assertEquals(userId, errorMessage.getHeaders().get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, errorMessage.getHeaders().get(HEADER_UPDATE_TYPE));
        assertNotNull(errorMessage.getHeaders().get(NotificationService.HEADER_ERROR));

        // and the pending creation request must be cleaned up, not left dangling
        assertTrue(studyCreationRequestRepository.findById(studyUuid).isEmpty());
        assertTrue(studyRepository.findById(studyUuid).isEmpty());
    }

    @Test
    void testImportStudyStopsOnRootNetworkValidationFailureBeforeAnyDispatch() throws Exception {
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);

        RootNetworkExportInfos firstRootNetwork = new RootNetworkExportInfos("Network 0", "0", "XIIDM",
                new CaseExportInfos(CASE_UUID, "testCase.xiidm"), Collections.emptyMap());
        // second and third share the same tag: the duplicate must be caught before the second
        // one's (otherwise valid) async network conversion gets dispatched
        RootNetworkExportInfos secondRootNetwork = new RootNetworkExportInfos("Network 1", "dup", "XIIDM",
                new CaseExportInfos(UUID.randomUUID(), "case1.xiidm"), Collections.emptyMap());
        RootNetworkExportInfos thirdRootNetworkDuplicateTag = new RootNetworkExportInfos("Network 2", "dup", "XIIDM",
                new CaseExportInfos(UUID.randomUUID(), "case2.xiidm"), Collections.emptyMap());
        NodeTreeExportInfos nodeTreeExportInfos = new NodeTreeExportInfos("Root", "ROOT", null, null,
                NetworkModificationNodeType.CONSTRUCTION, Collections.emptyList());

        StudyExportInfos exportInfos = new StudyExportInfos(studyUuid,
                List.of(firstRootNetwork, secondRootNetwork, thirdRootNetworkDuplicateTag), nodeTreeExportInfos);

        assertThrows(StudyException.class, () -> studyService.importStudy(studyUuid, exportInfos, "testUser"));

        // no async network conversion must have been dispatched for either additional root network
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, NetworkConversionServerStubs.URI_NETWORK, false, Map.of(), null, 0);
    }

    @Test
    void testImportStudyWithCaseImportActionMultipleRootNetworks() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        UUID secondCaseUuid = UUID.randomUUID();
        String caseFormat = "XIIDM";
        String studyName = "Imported Study";
        String description = "Test import";
        UUID parentDirectoryUuid = UUID.randomUUID();

        RootNetworkExportInfos firstRootNetwork = new RootNetworkExportInfos("Network 1", "1", caseFormat,
                new CaseExportInfos(CASE_UUID, "testCase.xiidm"), Collections.emptyMap());
        RootNetworkExportInfos secondRootNetwork = new RootNetworkExportInfos("Network 2", "2", "UCTE",
                new CaseExportInfos(secondCaseUuid, "case2.xiidm"), Collections.emptyMap());
        NodeTreeExportInfos nodeTreeExportInfos = new NodeTreeExportInfos("Root", "ROOT", null, null,
                NetworkModificationNodeType.CONSTRUCTION, Collections.emptyList());
        StudyExportInfos exportInfos = new StudyExportInfos(studyUuid, List.of(firstRootNetwork, secondRootNetwork), nodeTreeExportInfos);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("importParameters", Collections.emptyMap());
        requestBody.put("studyExportInfos", exportInfos);
        String requestJson = objectMapper.writeValueAsString(requestBody);

        CountDownLatch countDownLatch = new CountDownLatch(1);
        UUID postNetworkStubId = wireMockStubs.networkConversionServer
                .stubImportNetworkWithPostAction(caseUuid.toString(), FIRST_VARIANT_ID, NETWORK_INFOS, caseFormat, countDownLatch);

        // the second root network's async conversion only needs to be dispatched successfully here;
        // its own consumer callback (ROOT_NETWORK_CREATION) is already covered by RootNetworkTest
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(NetworkConversionServerStubs.URI_NETWORK))
                .withQueryParam("caseUuid", WireMock.equalTo(secondCaseUuid.toString()))
                .willReturn(WireMock.ok()));

        wireMockStubs.userAdminServer.stubGetUserProfile("testUser");
        setupCreateParametersStubs();

        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/directories/" + parentDirectoryUuid + "/elements"))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "elementUuid", studyUuid,
                                "elementName", studyName,
                                "type", "STUDY")))));

        UUID stubDisableCaseExpirationId = wireMockStubs.caseServer.stubDisableCaseExpiration(caseUuid.toString());

        mockMvc.perform(post("/v1/studies/import-with-case-import-action/{caseUuid}", caseUuid)
                        .param("studyUuid", studyUuid.toString())
                        .param("studyName", studyName)
                        .param("description", description)
                        .param("parentDirectoryUuid", parentDirectoryUuid.toString())
                        .header(HEADER_USER_ID, "testUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        assertTrue(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS), "Timed out waiting for the case-import callback");

        Message<byte[]> startedMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(startedMessage);
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_STARTED, startedMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        Message<byte[]> finishedMessage = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(finishedMessage);
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, finishedMessage.getHeaders().get(HEADER_UPDATE_TYPE));

        // the study must now be flagged multi-root, and the second root network's async
        // conversion must have been registered and dispatched
        var studyEntity = studyRepository.findById(studyUuid).orElseThrow();
        assertFalse(studyEntity.isMonoRoot());
        assertEquals(1, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));

        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), FIRST_VARIANT_ID);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, NetworkConversionServerStubs.URI_NETWORK, false,
                Map.of("caseUuid", WireMock.equalTo(secondCaseUuid.toString())), null, 1);
        wireMockStubs.userAdminServer.verifyGetUserProfile("testUser");
        verifyCreateParameters(1, 9, 1, 1, 1);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, caseUuid.toString());
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/directories/" + parentDirectoryUuid + "/elements", false, Map.of(), null, 1);
    }

    @Test
    void testConsumeCaseImportFailedStudyImport() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        String userId = "testUser";
        String errorMessage = "network conversion failed";

        // Simulate a pending STUDY_IMPORT creation request with a stored import context, whose
        // remote dispatch then fails on network-conversion-server's side
        studyCreationRequestRepository.save(new StudyCreationRequestEntity(studyUuid, "firstRootNetworkName"));

        CaseImportReceiver receiver = new CaseImportReceiver(studyUuid, null, caseUuid, caseUuid, UUID.randomUUID(),
                userId, System.nanoTime(), CaseImportAction.STUDY_IMPORT, true);

        MessageHeaders messageHeaders = new MessageHeaders(Map.of(
                HEADER_RECEIVER, objectMapper.writeValueAsString(receiver),
                HEADER_ERROR_MESSAGE, errorMessage));

        consumeService.consumeCaseImportFailed().accept(MessageBuilder.createMessage("", messageHeaders));

        // the user gets a study creation error, not a "root networks update failed" notification
        Message<byte[]> errorNotification = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(errorNotification);
        assertEquals(userId, errorNotification.getHeaders().get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, errorNotification.getHeaders().get(HEADER_UPDATE_TYPE));
        assertEquals(errorMessage, errorNotification.getHeaders().get(NotificationService.HEADER_ERROR));

        // the pending creation request (and the import context that lived on it) must be cleaned up
        assertTrue(studyCreationRequestRepository.findById(studyUuid).isEmpty());
    }

    private StudyExportInfos createSampleStudyExportInfos(UUID studyUuid) {
        CaseExportInfos caseInfo = new CaseExportInfos(CASE_UUID, "testCase.xiidm");
        List<NodeTreeExportInfos> children = new ArrayList<>();
        children.add(new NodeTreeExportInfos("Test Node 1", "NETWORK_MODIFICATION", UUID.randomUUID(), BuildStatus.BUILT, NetworkModificationNodeType.CONSTRUCTION, Collections.emptyList()));
        children.add(new NodeTreeExportInfos("Test Node 2", "NETWORK_MODIFICATION", UUID.randomUUID(), BuildStatus.BUILT, NetworkModificationNodeType.CONSTRUCTION, Collections.emptyList()));
        NodeTreeExportInfos nodeTreeExportInfos = new NodeTreeExportInfos("Root", "ROOT", null, null, NetworkModificationNodeType.CONSTRUCTION, children);
        RootNetworkExportInfos rootNetwork = new RootNetworkExportInfos("Network 1", "1", "XIIDM", caseInfo, Collections.emptyMap());
        return new StudyExportInfos(studyUuid, Collections.singletonList(rootNetwork), nodeTreeExportInfos);
    }
}
