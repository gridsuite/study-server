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
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.BAD_NODE_TYPE;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class ImportStudyTest extends StudyTestBase {

    private static final String IMPORT_URL = "/v1/studies/import-with-case-import-action";
    private static final String USER_ID = "testUser";

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RootNetworkRequestRepository rootNetworkRequestRepository;

    @Test
    void testImportStudyWithCaseImportAction() throws Exception {
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
        UUID stubDuplicateModificationGroupId = wireMockStubs.stubDuplicateModificationGroup(objectMapper.writeValueAsString(Map.of()));

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of(
                new NodeTreeExportInfos("N1", "NETWORK_MODIFICATION", modificationGroupUuid1, "SECURITY", List.of(
                        new NodeTreeExportInfos("N2", "NETWORK_MODIFICATION", modificationGroupUuid2, "CONSTRUCTION", List.of())
                ))
        ));
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid1),
                rootNetworkExportInfos("rn2", "2", 1, caseUuid2)
        ), nodeTree);

        mockMvc.perform(post(IMPORT_URL).header(HEADER_USER_ID, USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(treeExportInfos)))
                .andExpect(status().isOk());

        checkRootNetworkRequestNotifications(2, studyUuid);
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        MessageHeaders headers = message.getHeaders();
        assertEquals(studyUuid, headers.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, headers.get(NotificationService.HEADER_UPDATE_TYPE));

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
        wireMockStubs.verifyDuplicateModificationGroup(stubDuplicateModificationGroupId, 2);

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
    void testImportStudyWithCaseImportActionRootNetworkFailureIsResilient() throws Exception {
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

        mockMvc.perform(post(IMPORT_URL).header(HEADER_USER_ID, USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(treeExportInfos)))
                .andExpect(status().isOk());

        checkRootNetworkRequestNotifications(1, studyUuid);
        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));

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
    void testImportStudyWithCaseImportActionInvalidNodeType() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID caseUuid = UUID.randomUUID();

        stubDefaultParametersCreation();

        NodeTreeExportInfos nodeTree = new NodeTreeExportInfos("Root", "ROOT", null, null, List.of(
                new NodeTreeExportInfos("N1", "NETWORK_MODIFICATION", UUID.randomUUID(), null, List.of())
        ));
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(
                rootNetworkExportInfos("rn1", "1", 0, caseUuid)
        ), nodeTree);

        MvcResult result = mockMvc.perform(post(IMPORT_URL).header(HEADER_USER_ID, USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(treeExportInfos)))
                .andExpect(status().isForbidden())
                .andReturn();
        PowsyblWsProblemDetail problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals(BAD_NODE_TYPE.value(), problemDetail.getBusinessErrorCode());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());
        assertEquals(0, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/duplicate")));
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists")));
        verifyDefaultParametersCreation();
    }

    @Test
    void testImportStudyWithCaseImportActionNoRootNetworks() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        TreeExportInfos treeExportInfos = new TreeExportInfos(studyUuid, List.of(), new NodeTreeExportInfos("Root", "ROOT", null, null, List.of()));

        MvcResult result = mockMvc.perform(post(IMPORT_URL).header(HEADER_USER_ID, USER_ID)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(treeExportInfos)))
                .andExpect(status().isNotFound())
                .andReturn();
        PowsyblWsProblemDetail problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals(NOT_FOUND.value(), problemDetail.getBusinessErrorCode());

        assertTrue(studyRepository.findById(studyUuid).isEmpty());
    }

    private RootNetworkExportInfos rootNetworkExportInfos(String name, String tag, int index, UUID caseUuid) {
        return new RootNetworkExportInfos(name, tag, index, new CaseInfos(caseUuid, null, "caseName", "UCTE"), Map.of());
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
