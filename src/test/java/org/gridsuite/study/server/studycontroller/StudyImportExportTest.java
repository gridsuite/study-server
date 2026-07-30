/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.studyexport.CaseExportInfos;
import org.gridsuite.study.server.dto.studyexport.NodeTreeExportInfos;
import org.gridsuite.study.server.dto.studyexport.RootNetworkExportInfos;
import org.gridsuite.study.server.dto.studyexport.StudyExportInfos;
import org.gridsuite.study.server.service.StudyExportArchiveService;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class StudyImportExportTest extends StudyTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyExportArchiveService studyExportArchiveService;

    @Test
    void testExportStudyArchive() throws Exception {
        // Create a study
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);

        // Point StudyExportArchiveService's case-server base URI to WireMock
        ReflectionTestUtils.setField(studyExportArchiveService, "caseServerBaseUri", wireMockServer.baseUrl());

        // Stub the case content download used during export
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + CASE_UUID))
                .willReturn(WireMock.aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody("dummy case content".getBytes())));

        // Export as archive
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/export-archive", studyUuid).header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=study-" + studyUuid + ".gz"))
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
                if ("study.json".equals(entry.getName())) {
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
                        .param("caseFormat", caseFormat)
                        .param("studyName", studyName)
                        .param("description", description)
                        .param("parentDirectoryUuid", parentDirectoryUuid.toString())
                        .header(HEADER_USER_ID, "testUser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        countDownLatch.await();

        Message<byte[]> message = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(message);

        wireMockStubs.networkConversionServer.verifyImportNetwork(postNetworkStubId, caseUuid.toString(), FIRST_VARIANT_ID);
        wireMockStubs.userAdminServer.verifyGetUserProfile("testUser");
        verifyCreateParameters(1, 9, 1, 1, 1);
        wireMockStubs.caseServer.verifyDisableCaseExpiration(stubDisableCaseExpirationId, caseUuid.toString());

        for (UUID groupUuid : nodeGroupUuids) {
            WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/groups/" + groupUuid + "/duplicate", false, Map.of("groupUuid", WireMock.matching(".*")), null, 1);
        }
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/directories/" + parentDirectoryUuid + "/elements", false, Map.of(), null, 1);
    }

    private StudyExportInfos createSampleStudyExportInfos(UUID studyUuid) {
        CaseExportInfos caseInfo = new CaseExportInfos(CASE_UUID, "testCase.xiidm");
        List<NodeTreeExportInfos> children = new ArrayList<>();
        children.add(new NodeTreeExportInfos(UUID.randomUUID(), "Test Node 1", "NETWORK_MODIFICATION", UUID.randomUUID(), "BUILT", Collections.emptyList()));
        children.add(new NodeTreeExportInfos(UUID.randomUUID(), "Test Node 2", "NETWORK_MODIFICATION", UUID.randomUUID(), "BUILT", Collections.emptyList()));
        NodeTreeExportInfos nodeTreeExportInfos = new NodeTreeExportInfos(UUID.randomUUID(), "Root", "ROOT", null, null, children);
        RootNetworkExportInfos rootNetwork = new RootNetworkExportInfos("Network 1", "1", "XIIDM", caseInfo, Collections.emptyMap());
        return new StudyExportInfos(studyUuid, Collections.singletonList(rootNetwork), nodeTreeExportInfos);
    }
}
