/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.dto.studyexport.TreeExportInfos;
import org.gridsuite.study.server.service.ActionsService;
import org.gridsuite.study.server.service.FilterService;
import org.gridsuite.study.server.utils.wiremock.WireMockUtilsCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
class TreeExportTest extends StudyTestBase {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilterService filterService;

    @Autowired
    private ActionsService actionsService;

    @Test
    void testExportStudy() throws Exception {
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
        // Stub the computation parameters fetches
        computationServerStubs.stubGetParametersAny("{}");
        computationServerStubs.stubGetReferencedUuidsAny();
        // Export as zip
        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/export/{studyName}", studyUuid, "studyName").header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andReturn();
        // Verify the response contains data
        byte[] archiveContent = result.getResponse().getContentAsByteArray();
        assertNotNull(archiveContent);
        assertTrue(archiveContent.length > 0);
        TreeExportInfos exportInfos = null;
        List<String> zipEntryNames = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archiveContent))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipEntryNames.add(entry.getName());
                if ("tree.json".equals(entry.getName())) {
                    exportInfos = objectMapper.readValue(zis.readAllBytes(), TreeExportInfos.class);
                }
            }
        }
        // Verify export structure
        assertNotNull(exportInfos);
        assertEquals(studyUuid, exportInfos.studyUuid());
        assertNotNull(exportInfos.rootNetworks());
        assertEquals(1, exportInfos.rootNetworks().size());
        assertNotNull(exportInfos.rootNetworks().getFirst().importParameters());
        assertEquals(0, exportInfos.rootNetworks().getFirst().index());
        assertNotNull(exportInfos.nodeTree());
        assertEquals("ROOT", exportInfos.nodeTree().type());
        assertNotNull(exportInfos.nodeTree().children());
        assertEquals(1, exportInfos.nodeTree().children().size());
        // Verify the cases/ directory: one subfolder per root network case, named after its UUID,
        // containing the case content file under its exported case name
        UUID rootNetworkCaseUuid = exportInfos.rootNetworks().getFirst().caseInfos().getCaseUuid();
        String rootNetworkCaseName = exportInfos.rootNetworks().getFirst().caseInfos().getCaseName();
        String expectedCaseEntry = "cases/" + rootNetworkCaseUuid + "/" + rootNetworkCaseName;
        assertEquals(List.of(expectedCaseEntry), zipEntryNames.stream().filter(name -> name.startsWith("cases/")).toList());
        assertTrue(zipEntryNames.containsAll(List.of("computationParameters/filters.json", "computationParameters/contingencyList.json")));
        // Verify the case content download call
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/cases/" + CASE_UUID, false, Map.of(), 1);
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
        // Verify the computation parameters fetches
        computationServerStubs.verifyParametersGetAny(10);
        computationServerStubs.verifyReferencedUuidsGetAny(5);
    }

    @Test
    void testExportStudyWithFilterAndContingencyListDefinitions() throws Exception {
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);
        ReflectionTestUtils.setField(caseService, "caseServerBaseUri", wireMockServer.baseUrl());
        filterService.setBaseUri(wireMockServer.baseUrl());
        actionsService.setActionsServerBaseUri(wireMockServer.baseUrl());
        UUID filterA = UUID.randomUUID();
        UUID filterB = UUID.randomUUID();
        UUID filterC = UUID.randomUUID();
        UUID deletedFilter = UUID.randomUUID();
        UUID identifierList = UUID.randomUUID();
        UUID filterBasedList = UUID.randomUUID();
        wireMockStubs.directoryServer.stubCheckPermission(List.of(studyUuid), null, "testUser", PermissionType.READ, false, HttpStatus.OK.value());
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + CASE_UUID))
                .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream").withBody("dummy case content".getBytes())));
        computationServerStubs.stubGetParametersAny("{}");
        stubJsonGet("/v1/parameters/[^/]+/filter-uuids", "[\"" + filterA + "\",\"" + deletedFilter + "\"]");
        stubJsonGet("/v1/parameters/[^/]+/contingency-list-uuids", "[\"" + identifierList + "\",\"" + filterBasedList + "\"]");
        String filterAJson = "{\"id\":\"" + filterA + "\",\"type\":\"EXPERT\",\"rules\":{\"dataType\":\"COMBINATOR\",\"rules\":["
                + "{\"dataType\":\"FILTER_UUID\",\"field\":\"ID\",\"operator\":\"IS_PART_OF\",\"values\":[\"" + filterC + "\"]}]}}";
        String filterBJson = "{\"id\":\"" + filterB + "\",\"type\":\"IDENTIFIER_LIST\"}";
        String filterCJson = "{\"id\":\"" + filterC + "\",\"type\":\"IDENTIFIER_LIST\"}";
        // filterB is referenced by the filter based contingency list, filterC by filterA, deletedFilter does not exist anymore
        stubJsonGet("/v1/contingency-lists/filter-uuids", "[\"" + filterB + "\"]");
        stubJsonGet("/v1/filters/referenced-filter-uuids", "[\"" + filterC + "\"]");
        stubJsonGet("/v1/filters/metadata", "[" + filterAJson + "," + filterBJson + "," + filterCJson + "]");
        String identifierListJson = "{\"id\":\"" + identifierList + "\",\"type\":\"IDENTIFIERS\",\"identifiersListByNetwork\":[]}";
        String filterBasedListJson = "{\"id\":\"" + filterBasedList + "\",\"type\":\"FILTERS\",\"filters\":[{\"id\":\"" + filterB + "\"}],"
                + "\"selectedEquipmentTypesByFilter\":[{\"filterId\":\"" + filterB + "\",\"equipmentTypes\":[\"LINE\"]}]}";
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/contingency-lists"))
                .willReturn(WireMock.okJson("[" + identifierListJson + "," + filterBasedListJson + "]")));
        Map<UUID, String> names = Map.of(filterA, "nameA", filterB, "nameB", filterC, "nameC", identifierList, "nameI", filterBasedList, "nameF");
        wireMockStubs.directoryServer.stubGetElementNames(objectMapper.writeValueAsString(names));

        MvcResult result = mockMvc.perform(get("/v1/studies/{studyUuid}/export/{studyName}", studyUuid, "studyName").header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, String> zipContents = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zipContents.put(entry.getName(), new String(zis.readAllBytes()));
            }
        }
        Set<JsonNode> expectedFilters = Set.of(
                objectMapper.readTree("{\"uuid\":\"" + filterA + "\",\"name\":\"nameA\",\"content\":" + filterAJson + "}"),
                objectMapper.readTree("{\"uuid\":\"" + filterB + "\",\"name\":\"nameB\",\"content\":" + filterBJson + "}"),
                objectMapper.readTree("{\"uuid\":\"" + filterC + "\",\"name\":\"nameC\",\"content\":" + filterCJson + "}"));
        Set<JsonNode> expectedContingencyLists = Set.of(
                objectMapper.readTree("{\"uuid\":\"" + identifierList + "\",\"name\":\"nameI\",\"content\":" + identifierListJson + "}"),
                objectMapper.readTree("{\"uuid\":\"" + filterBasedList + "\",\"name\":\"nameF\",\"content\":" + filterBasedListJson + "}"));
        assertEquals(expectedFilters, readDefinitions(zipContents.get("computationParameters/filters.json")));
        assertEquals(expectedContingencyLists, readDefinitions(zipContents.get("computationParameters/contingencyList.json")));

        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/cases/" + CASE_UUID, false, Map.of(), 1);
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
        computationServerStubs.verifyParametersGetAny(10);
        computationServerStubs.verifyReferencedUuidsGetAny(5);
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/contingency-lists/filter-uuids", false, Map.of("ids", WireMock.matching(".*")), 1);
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/filters/referenced-filter-uuids", false, Map.of("ids", WireMock.matching(".*")), 1);
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/filters/metadata", false, Map.of("ids", WireMock.matching(".*")), 1);
        WireMockUtilsCriteria.verifyPostRequest(wireMockServer, "/v1/contingency-lists", Map.of(), 1);
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/elements/names", false, Map.of("ids", WireMock.matching(".*"), "strictMode", WireMock.equalTo("false")), 1);
    }

    private void stubJsonGet(String urlRegex, String body) {
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching(urlRegex))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(body)));
    }

    private Set<JsonNode> readDefinitions(String json) throws IOException {
        Set<JsonNode> definitions = new HashSet<>();
        objectMapper.readTree(json).forEach(definitions::add);
        return definitions;
    }

    @Test
    void testExportStudyFailNoPermission() throws Exception {
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);
        wireMockStubs.directoryServer.stubCheckPermission(List.of(studyUuid), null, "testUser", PermissionType.READ, false, HttpStatus.FORBIDDEN.value());

        mockMvc.perform(get("/v1/studies/{studyUuid}/export/{studyName}", studyUuid, "studyName").header(HEADER_USER_ID, "testUser"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Disposition", nullValue()));
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
    }

    @Test
    void testExportStudyFailToDeleteTempZipFile() throws Exception {
        // Create a study
        UUID studyUuid = createStudyWithStubs("testUser", CASE_UUID);
        ReflectionTestUtils.setField(caseService, "caseServerBaseUri", wireMockServer.baseUrl());
        wireMockStubs.directoryServer.stubCheckPermission(List.of(studyUuid), null, "testUser", PermissionType.READ, false, HttpStatus.OK.value());
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + CASE_UUID))
                .willReturn(WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream")
                        .withBody("dummy case content".getBytes())));
        computationServerStubs.stubGetParametersAny("{}");
        computationServerStubs.stubGetReferencedUuidsAny();
        // Capture the real zip file path as it is matched, so the test can clean it up itself:
        // the service's own Files.deleteIfExists call on this path is mocked to fail below.
        AtomicReference<Path> capturedZipFile = new AtomicReference<>();
        ArgumentMatcher<Path> isStudyZipFile = path -> {
            boolean matches = path.getFileName().toString().startsWith("study-export-" + studyUuid)
                    && path.getFileName().toString().endsWith(".zip");
            if (matches) {
                capturedZipFile.set(path);
            }
            return matches;
        };
        try {
            try (MockedStatic<Files> mockedFiles = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                mockedFiles.when(() -> Files.newInputStream(argThat(isStudyZipFile), eq(StandardOpenOption.DELETE_ON_CLOSE)))
                        .thenThrow(new IOException("Simulated failure opening exported zip stream"));
                mockedFiles.when(() -> Files.deleteIfExists(argThat(isStudyZipFile)))
                        .thenThrow(new IOException("Simulated failure deleting temp zip file"));

                mockMvc.perform(get("/v1/studies/{studyUuid}/export/{studyName}", studyUuid, "studyName").header(HEADER_USER_ID, "testUser"))
                        .andExpect(status().isInternalServerError())
                        .andExpect(header().string("Content-Disposition", nullValue()));
                assertNotNull(capturedZipFile.get(), "the mocked zip file path was never matched");
            }
        } finally {
            Path zipFile = capturedZipFile.get();
            if (zipFile != null) {
                Files.deleteIfExists(zipFile);
            }
        }
        WireMockUtilsCriteria.verifyGetRequest(wireMockServer, "/v1/cases/" + CASE_UUID, false, Map.of(), 1);
        wireMockStubs.directoryServer.verifyCheckPermission(List.of(studyUuid), null, PermissionType.READ, false);
        computationServerStubs.verifyParametersGetAny(10);
        computationServerStubs.verifyReferencedUuidsGetAny(5);
    }
}
