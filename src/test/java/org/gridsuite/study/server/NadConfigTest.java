/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.diagramgridlayout.nad.NadConfigInfos;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NadConfigService;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NadConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @Autowired
    private NadConfigService nadConfigService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();

        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrlWireMock);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
        studyRepository.deleteAll();
    }

    @Test
    @Transactional
    void testSaveNewNadConfig() throws Exception {
        UUID studyUuid = UUID.randomUUID();

        studyRepository.save(StudyEntity.builder().id(studyUuid).nadConfigsUuids(new ArrayList<>()).build());

        NadConfigInfos nadConfigInfos = NadConfigInfos.builder()
            .id(null) // New config
            .build();

        String payload = objectMapper.writeValueAsString(nadConfigInfos);

        wireMockServer.stubFor(WireMock.post(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/network-area-diagrams/configs", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID nadConfigUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertNotNull(nadConfigUuidResult);

        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        assertTrue(studyEntity.isPresent());
        assertTrue(studyEntity.get().getNadConfigsUuids().contains(nadConfigUuidResult));
    }

    @Test
    @Transactional
    void testUpdateExistingNadConfig() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID existingNadConfigUuid = UUID.randomUUID();

        List<UUID> nadConfigsList = new ArrayList<>();
        nadConfigsList.add(existingNadConfigUuid);
        studyRepository.save(StudyEntity.builder().id(studyUuid).nadConfigsUuids(nadConfigsList).build());

        NadConfigInfos nadConfigInfos = NadConfigInfos.builder()
            .id(existingNadConfigUuid) // Existing config
            .build();

        String payload = objectMapper.writeValueAsString(nadConfigInfos);

        wireMockServer.stubFor(WireMock.put(DELIMITER + "v1/network-area-diagram/config/" + existingNadConfigUuid)
            .willReturn(WireMock.ok()));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/network-area-diagrams/configs", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID nadConfigUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertEquals(existingNadConfigUuid, nadConfigUuidResult);

        wireMockServer.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/config/" + existingNadConfigUuid)));

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        assertTrue(studyEntity.isPresent());
        assertEquals(1, studyEntity.get().getNadConfigsUuids().size());
        assertTrue(studyEntity.get().getNadConfigsUuids().contains(existingNadConfigUuid));
    }

    @Test
    @Transactional
    void testDeleteNadConfig() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID nadConfigUuid = UUID.randomUUID();

        List<UUID> nadConfigsList = new ArrayList<>();
        nadConfigsList.add(nadConfigUuid);
        studyRepository.save(StudyEntity.builder().id(studyUuid).nadConfigsUuids(nadConfigsList).build());

        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()));

        mockMvc.perform(delete("/v1/studies/{studyUuid}/network-area-diagrams/configs/{nadConfigUuid}", studyUuid, nadConfigUuid))
            .andExpect(status().isNoContent());

        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        Optional<StudyEntity> studyEntity = studyRepository.findById(studyUuid);
        assertTrue(studyEntity.isPresent());
        assertFalse(studyEntity.get().getNadConfigsUuids().contains(nadConfigUuid));
    }

    @Test
    void testNadConfigServiceDeleteConfigWithNull() {
        nadConfigService.deleteNadConfig(null);

        // Verify no external calls were made
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testNadConfigServiceDeleteMultipleConfigs() {
        List<UUID> nadConfigUuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()));

        nadConfigService.deleteNadConfigs(nadConfigUuids);

        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));
    }

    @Test
    void testNadConfigServiceDeleteMultipleConfigsWithNull() {
        nadConfigService.deleteNadConfigs(null);

        // Verify no external calls were made
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testNadConfigServiceDeleteMultipleConfigsWithEmptyList() {
        nadConfigService.deleteNadConfigs(List.of());

        // Verify no external calls were made
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testNadConfigServiceCreateWithError() {
        NadConfigInfos nadConfigInfos = NadConfigInfos.builder()
            .id(null)
            .build();

        wireMockServer.stubFor(WireMock.post(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.serverError()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            nadConfigService.saveNadConfig(nadConfigInfos);
        });

        assertEquals(StudyException.Type.SAVE_NAD_CONFIG_FAILED, exception.getType());
    }

    @Test
    void testNadConfigServiceUpdateWithError() {
        UUID existingUuid = UUID.randomUUID();
        NadConfigInfos nadConfigInfos = NadConfigInfos.builder()
            .id(existingUuid)
            .build();

        wireMockServer.stubFor(WireMock.put(DELIMITER + "v1/network-area-diagram/config/" + existingUuid)
            .willReturn(WireMock.serverError()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            nadConfigService.saveNadConfig(nadConfigInfos);
        });

        assertEquals(StudyException.Type.SAVE_NAD_CONFIG_FAILED, exception.getType());
    }

    @Test
    void testNadConfigServiceDeleteWithError() {
        UUID nadConfigUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.serverError()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            nadConfigService.deleteNadConfig(nadConfigUuid);
        });

        assertEquals(StudyException.Type.DELETE_NAD_CONFIG_FAILED, exception.getType());
    }
}
