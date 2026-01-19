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
import org.gridsuite.study.server.repository.StudyRepository;
import org.springframework.web.client.HttpServerErrorException;
import org.gridsuite.study.server.service.NadConfigService;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.junit.jupiter.api.Assertions.*;

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
    }

    @Test
    @Transactional
    void testSaveNewNadConfig() throws Exception {
        // Test removed - NAD config management moved to workspace panels
    }

    @Test
    @Transactional
    void testUpdateExistingNadConfig() throws Exception {
        // Test removed - NAD config management moved to workspace panels
    }

    @Test
    @Transactional
    void testDeleteNadConfig() throws Exception {
        // Test removed - NAD config management moved to workspace panels
    }

    @Test
    void testNadConfigServiceDeleteConfigWithNull() {
        nadConfigService.deleteNadConfigs(null);

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
    void testNadConfigServiceDeleteMultipleConfigsWithEmptyList() {
        nadConfigService.deleteNadConfigs(List.of());

        // Verify no external calls were made
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testNadConfigServiceDeleteWithError() {
        UUID nadConfigUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.serverError()));

        List<UUID> nadConfigUuids = List.of(nadConfigUuid);
        assertThrows(HttpServerErrorException.class, () -> nadConfigService.deleteNadConfigs(nadConfigUuids));
    }
}
