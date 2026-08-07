/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TreeExportArchiveTest extends StudyTestBase {

    @Autowired
    private ObjectMapper objectMapper;

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
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/export", studyUuid).header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=" + studyUuid + ".gz"))
                .andExpect(header().string("Content-Type", "application/gzip"))
                .andReturn();
        // Verify the response contains data
        byte[] archiveContent = result.getResponse().getContentAsByteArray();
        assertNotNull(archiveContent);
        assertTrue(archiveContent.length > 0);
        TreeExportInfos exportInfos = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("tree.json".equals(entry.getName())) {
                    exportInfos = objectMapper.readValue(zis.readAllBytes(), TreeExportInfos.class);
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

        mockMvc.perform(get("/v1/studies/{studyUuid}/export", studyUuid).header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Disposition", nullValue()));
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
    }
}
