/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.CaseInfos;
import org.gridsuite.study.server.dto.RootNetworkLoadStatus;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRequestRepository;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid1.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid1));
        wireMockStubs.caseServer.stubDuplicateCaseWithBody(caseUuid2.toString(), objectMapper.writeValueAsString(duplicatedCaseUuid2));

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

        // Import is fully synchronous, the only notification sent is the study creation finished one
        Message<byte[]> message = TestUtils.receiveStudyUpdate(output, studyUpdateDestination);
        assertNotNull(message);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(USER_ID, message.getHeaders().get(HEADER_USER_ID));
        assertEquals(NotificationService.UPDATE_TYPE_STUDY_CREATION_FINISHED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
        assertNull(output.receive(TIMEOUT, studyUpdateDestination));
        assertNull(output.receive(TIMEOUT, elementUpdateDestination));

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

        // Root networks are created directly synchronously
        assertEquals(0, rootNetworkRequestRepository.countAllByStudyUuid(studyUuid));
        List<RootNetworkEntity> rootNetworks = rootNetworkRepository.findAllByStudyId(studyUuid);
        assertEquals(2, rootNetworks.size());

        RootNetworkEntity rn1 = rootNetworkRepository.findByNameAndStudyId("rn1", studyUuid).orElseThrow();
        assertEquals("1", rn1.getTag());
        assertEquals(duplicatedCaseUuid1, rn1.getCaseUuid());
        assertNull(rn1.getOriginalCaseUuid());
        assertEquals(RootNetworkLoadStatus.UNLOADED, rn1.getLoadStatus());
        // Network is not actually imported during a study import: networkInfos is only a placeholder,
        // the real network will be loaded later on demand to recreate network
        assertNotNull(rn1.getNetworkUuid());
        assertEquals("", rn1.getNetworkId());

        RootNetworkEntity rn2 = rootNetworkRepository.findByNameAndStudyId("rn2", studyUuid).orElseThrow();
        assertEquals("2", rn2.getTag());
        assertEquals(duplicatedCaseUuid2, rn2.getCaseUuid());
        assertNull(rn2.getOriginalCaseUuid());
        assertEquals(RootNetworkLoadStatus.UNLOADED, rn2.getLoadStatus());

        verifyDuplicateCaseRequest(caseUuid1);
        verifyDuplicateCaseRequest(caseUuid2);
        verifyDefaultParametersCreation();
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

    private void verifyDuplicateCaseRequest(UUID caseUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/cases/" + caseUuid + "/duplicate",
                Map.of("withExpiration", WireMock.matching(".*")));
    }
}
