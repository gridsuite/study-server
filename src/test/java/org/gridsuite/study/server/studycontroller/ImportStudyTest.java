/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.caseimport.CaseImportAction;
import org.gridsuite.study.server.dto.caseimport.CaseImportReceiver;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.service.ConsumerService;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.gridsuite.study.server.StudyConstants.HEADER_IMPORT_PARAMETERS;
import static org.gridsuite.study.server.StudyConstants.HEADER_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.BAD_NODE_TYPE;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class ImportStudyTest extends StudyTestBase {

    private static final String IMPORT_URL = "/v1/studies/import";
    private static final String USER_ID = "testUser";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RootNetworkRequestRepository rootNetworkRequestRepository;
    @Autowired
    private ConsumerService consumerService;

    @Test
    void testImportStudy() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid1 = UUID.randomUUID();
        UUID caseUuid2 = UUID.randomUUID();
        UUID duplicatedCaseUuid1 = UUID.randomUUID();
        UUID duplicatedCaseUuid2 = UUID.randomUUID();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        UUID modificationGroupUuid2 = UUID.randomUUID();

        stubDefaultParametersCreation();
        UUID stubCaseExists1Id = wireMockStubs.caseServer.stubCaseExists(caseUuid1.toString(), true);
        UUID stubCaseExists2Id = wireMockStubs.caseServer.stubCaseExists(caseUuid2.toString(), true);
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid1.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid1));
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid2.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid2));
        stubImportNetworkOnly();
        UUID stubImportNetworkModificationsId = wireMockStubs.stubImportNetworkModifications(objectMapper.writeValueAsString(Map.of()));

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of(
                new NodeTreeExportInfos("N1", "NETWORK_MODIFICATION", modificationGroupUuid1, "SECURITY", List.of(
                        new NodeTreeExportInfos("N2", "NETWORK_MODIFICATION", modificationGroupUuid2, "CONSTRUCTION", List.of())
                ))
        ));
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid1),
                rootNetworkExportInfos("rn2", "2", 1, caseUuid2)
        ), nodeTree);

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());

        checkRootNetworkRequestNotifications(2, studyUuid);
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));

        assertTrue(studyRepository.findById(studyUuid).isPresent());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid, null);
        assertNotNull(rootNode);
        assertEquals(1, rootNode.getChildren().size());
        AbstractNode n1 = rootNode.getChildren().getFirst();
        assertEquals("N1", n1.getName());
        assertInstanceOf(NetworkModificationNode.class, n1);
        assertEquals("SECURITY", ((NetworkModificationNode) n1).getNodeType().name());
        assertEquals(1, n1.getChildren().size());
        AbstractNode n2 = n1.getChildren().getFirst();
        assertEquals("N2", n2.getName());
        assertEquals("CONSTRUCTION", ((NetworkModificationNode) n2).getNodeType().name());
        assertNotEquals(modificationGroupUuid1, ((NetworkModificationNode) n1).getModificationGroupUuid());
        assertNotEquals(modificationGroupUuid2, ((NetworkModificationNode) n2).getModificationGroupUuid());
        wireMockStubs.verifyImportNetworkModifications(stubImportNetworkModificationsId, 2);

        assertEquals(2, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists1Id, caseUuid1.toString());
        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists2Id, caseUuid2.toString());
        verifyDuplicateCaseRequest(caseUuid1);
        verifyDuplicateCaseRequest(caseUuid2);
        verifyImportNetworkRequest(duplicatedCaseUuid1);
        verifyImportNetworkRequest(duplicatedCaseUuid2);

        verifyDefaultParametersCreation();
    }

    @Test
    void testImportStudyWithExportedOrder() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid1 = UUID.randomUUID();
        UUID caseUuid2 = UUID.randomUUID();
        UUID duplicatedCaseUuid1 = UUID.randomUUID();
        UUID duplicatedCaseUuid2 = UUID.randomUUID();

        stubDefaultParametersCreation();
        UUID stubCaseExists1Id = wireMockStubs.caseServer.stubCaseExists(caseUuid1.toString(), true);
        UUID stubCaseExists2Id = wireMockStubs.caseServer.stubCaseExists(caseUuid2.toString(), true);
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid1.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid1));
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid2.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid2));
        stubImportNetworkOnly();
        UUID stubDisableExpiration1Id = wireMockStubs.caseServer.stubDisableCaseExpiration(duplicatedCaseUuid1.toString());
        UUID stubDisableExpiration2Id = wireMockStubs.caseServer.stubDisableCaseExpiration(duplicatedCaseUuid2.toString());

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of());
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid1),
                rootNetworkExportInfos("rn2", "2", 1, caseUuid2)
        ), nodeTree);

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());

        checkRootNetworkRequestNotifications(2, studyUuid);
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));

        List<RootNetworkRequestEntity> requests = rootNetworkRequestRepository.findAllByStudyUuid(studyUuid);
        assertEquals(2, requests.size());
        RootNetworkRequestEntity request1 = requests.stream().filter(r -> "rn1".equals(r.getName())).findFirst().orElseThrow();
        RootNetworkRequestEntity request2 = requests.stream().filter(r -> "rn2".equals(r.getName())).findFirst().orElseThrow();
        List<UUID> rootNetworkOrder = studyRepository.findWithRootNetworksById(studyUuid).orElseThrow().getRootNetworkOrder();
        assertEquals(List.of(request1.getId(), request2.getId()), rootNetworkOrder);

        completeRootNetworkCreation(studyUuid, request2, duplicatedCaseUuid2, caseUuid2);
        Message<byte[]> afterFirstCompletion = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(afterFirstCompletion);
        assertNotEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, afterFirstCompletion.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));

        completeRootNetworkCreation(studyUuid, request1, duplicatedCaseUuid1, caseUuid1);
        Message<byte[]> afterLastCompletion = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(afterLastCompletion);
        assertNotEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, afterLastCompletion.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        Message<byte[]> finished = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(finished);
        assertEquals(studyUuid, finished.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, finished.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));

        StudyEntity studyEntity = studyRepository.findWithRootNetworksById(studyUuid).orElseThrow();
        assertEquals(List.of("rn1", "rn2"), studyEntity.getRootNetworks().stream().map(RootNetworkEntity::getName).toList());

        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists1Id, caseUuid1.toString());
        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists2Id, caseUuid2.toString());
        verifyDuplicateCaseRequest(caseUuid1);
        verifyDuplicateCaseRequest(caseUuid2);
        verifyImportNetworkRequest(duplicatedCaseUuid1);
        verifyImportNetworkRequest(duplicatedCaseUuid2);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableExpiration1Id, duplicatedCaseUuid1.toString());
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableExpiration2Id, duplicatedCaseUuid2.toString());
        verifyDefaultParametersCreation();
    }

    private void completeRootNetworkCreation(UUID studyUuid, RootNetworkRequestEntity request, UUID duplicatedCaseUuid, UUID originalCaseUuid) throws Exception {
        Consumer<Message<String>> messageConsumer = consumerService.consumeCaseImportSucceeded();
        CaseImportReceiver caseImportReceiver = new CaseImportReceiver(studyUuid, request.getId(), duplicatedCaseUuid, originalCaseUuid,
                UUID.randomUUID(), USER_ID, 0L, CaseImportAction.ROOT_NETWORK_CREATION_FOR_STUDY_IMPORT);
        Map<String, Object> headers = new HashMap<>();
        headers.put("networkUuid", UUID.randomUUID().toString());
        headers.put("networkId", "networkId");
        headers.put("caseFormat", "UCTE");
        headers.put("caseName", "caseName");
        headers.put(HEADER_RECEIVER, objectMapper.writeValueAsString(caseImportReceiver));
        headers.put(HEADER_IMPORT_PARAMETERS, Map.of());
        messageConsumer.accept(new GenericMessage<>("", headers));
    }

    @Test
    void testImportStudyWithRootNetworkFailure() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid1 = UUID.randomUUID();
        UUID caseUuid2 = UUID.randomUUID();
        UUID duplicatedCaseUuid1 = UUID.randomUUID();

        stubDefaultParametersCreation();
        UUID stubCaseExists1Id = wireMockStubs.caseServer.stubCaseExists(caseUuid1.toString(), true);
        UUID stubCaseExists2Id = wireMockStubs.caseServer.stubCaseExists(caseUuid2.toString(), true);
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid1.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid1));
        stubImportNetworkOnly();
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid2 + "/duplicate"))
                .withQueryParam("withExpiration", WireMock.matching(".*"))
                .willReturn(WireMock.serverError()));

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of());
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid1),
                rootNetworkExportInfos("rn2", "2", 1, caseUuid2)
        ), nodeTree);

        mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isOk());

        checkRootNetworkRequestNotifications(1, studyUuid);
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));

        assertTrue(studyRepository.findById(studyUuid).isPresent());
        assertNotNull(networkModificationTreeService.getStudyTree(studyUuid, null));
        assertEquals(1, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists1Id, caseUuid1.toString());
        wireMockStubs.caseServer.verifyCaseExists(stubCaseExists2Id, caseUuid2.toString());
        verifyDuplicateCaseRequest(caseUuid1);
        verifyImportNetworkRequest(duplicatedCaseUuid1);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/cases/" + caseUuid2 + "/duplicate",
                Map.of("withExpiration", WireMock.matching(".*")));
        verifyDefaultParametersCreation();
    }

    @Test
    void testImportStudyWithModificationGroupOnFailure() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        UUID modificationGroupUuid1 = UUID.randomUUID();
        UUID modificationGroupUuid2 = UUID.randomUUID();

        UUID stubImportNetworkModificationsId = wireMockStubs.stubImportNetworkModifications(objectMapper.writeValueAsString(Map.of()));
        UUID stubDeleteGroupId = wireMockStubs.stubNetworkModificationDeleteGroup();

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of(
                new NodeTreeExportInfos("N1", "NETWORK_MODIFICATION", modificationGroupUuid1, "SECURITY", List.of(
                        new NodeTreeExportInfos("N2", "NETWORK_MODIFICATION", modificationGroupUuid2, null, List.of())
                ))
        ));
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid)
        ), nodeTree);

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden())
                .andReturn();
        PowsyblWsProblemDetail problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals(BAD_NODE_TYPE.value(), problemDetail.getBusinessErrorCode());
        wireMockStubs.verifyImportNetworkModifications(stubImportNetworkModificationsId, 1);
        wireMockStubs.verifyNetworkModificationDeleteGroup(stubDeleteGroupId, false);

        assertTrue(studyRepository.findById(studyUuid).isEmpty());
        assertEquals(0, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
    }

    @Test
    void testImportStudyWithInvalidNodeType() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();
        UUID modificationGroupUuid = UUID.randomUUID();

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of(
                new NodeTreeExportInfos("N1", "NETWORK_MODIFICATION", modificationGroupUuid, null, List.of())
        ));
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid)
        ), nodeTree);

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isForbidden())
                .andReturn();
        PowsyblWsProblemDetail problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals(BAD_NODE_TYPE.value(), problemDetail.getBusinessErrorCode());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());
        assertEquals(0, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/duplicate")));
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists")));
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlPathMatching("/v1/groups/.*/network-modifications/import")));
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/users/" + USER_ID + "/profile")));
    }

    @Test
    void testImportStudyWithNoRootNetworks() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(), new NodeTreeExportInfos("Root", "ROOT", null, null, List.of()));

        MvcResult result = mockMvc.perform(multipart(IMPORT_URL)
                        .file(treeExportInfosPart(treeExportInfos))
                        .file(emptyModificationsArchivePart())
                        .header(HEADER_USER_ID, USER_ID))
                .andExpect(status().isNotFound())
                .andReturn();
        PowsyblWsProblemDetail problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals(NOT_FOUND.value(), problemDetail.getBusinessErrorCode());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());
    }

    private RootNetworkExportInfos rootNetworkExportInfos(String name, String tag, int index, UUID caseUuid) {
        return new RootNetworkExportInfos(name, tag, index, new CaseInfos(caseUuid, null, "caseName", "UCTE"), Map.of());
    }

    private MockMultipartFile treeExportInfosPart(TreeExportInfos treeExportInfos) throws Exception {
        return new MockMultipartFile("treeExportInfos", "treeExportInfos", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(treeExportInfos));
    }

    private MockMultipartFile emptyModificationsArchivePart() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(baos)) {
            for (String fileName : List.of("network-modification.json", "network-modification-filters.json", "network-modification-load-flow-parameters.json")) {
                zipOut.putNextEntry(new ZipEntry(fileName));
                zipOut.write("{}".getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
        return new MockMultipartFile("modificationsArchive", "modifications.zip", "application/zip", baos.toByteArray());
    }

    private void stubDefaultParametersCreation() throws Exception {
        ReflectionTestUtils.setField(caseService, "caseServerBaseUri", wireMockServer.baseUrl());
        wireMockStubs.userAdminServer.stubGetUserProfile(USER_ID);
        setupCreateParametersStubs();
    }

    private void verifyDefaultParametersCreation() {
        wireMockStubs.userAdminServer.verifyGetUserProfile(USER_ID);
        verifyCreateParameters(1, 9, 1, 1, 1);
    }

    private void checkRootNetworkRequestNotifications(int successfulRootNetworkRequests, UUID studyUuid) {
        Message<byte[]> studyCreationStarted = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(studyCreationStarted);
        assertEquals(studyUuid, studyCreationStarted.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_STARTED, studyCreationStarted.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        for (int i = 0; i < successfulRootNetworkRequests; i++) {
            Message<byte[]> rootNetworksUpdated = output.receive(TIMEOUT, studyUpdateDestination);
            assertNotNull(rootNetworksUpdated);
            assertEquals(studyUuid, rootNetworksUpdated.getHeaders().get(NotificationService.HEADER_STUDY_UUID));

            Message<byte[]> elementUpdated = output.receive(TIMEOUT, elementUpdateDestination);
            assertNotNull(elementUpdated);
            assertEquals(studyUuid, elementUpdated.getHeaders().get(NotificationService.HEADER_ELEMENT_UUID));
            assertEquals(ImportStudyTest.USER_ID, elementUpdated.getHeaders().get(NotificationService.HEADER_MODIFIED_BY));
        }
    }

    private void stubImportNetworkOnly() {
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
                .willReturn(WireMock.ok()));
    }

    private void verifyDuplicateCaseRequest(UUID caseUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/cases/" + caseUuid + "/duplicate",
                Map.of("withExpiration", WireMock.matching(".*")));
    }

    private void verifyImportNetworkRequest(UUID caseUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/networks",
                Map.of("caseUuid", WireMock.equalTo(caseUuid.toString()), "receiver", WireMock.matching(".*")));
    }
}
