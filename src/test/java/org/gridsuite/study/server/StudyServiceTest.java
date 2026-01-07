/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(StudyServiceTest.class);

    private WireMockServer wireMockServer;

    private WireMockStubs wireMockStubs;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockStubs = new WireMockStubs(wireMockServer);

        // Start the server.
        wireMockServer.start();

        singleLineDiagramService.setSingleLineDiagramServerBaseUri(wireMockServer.baseUrl());
    }

    @Test
    void testImportCsv() throws Exception {
        String csvContent = """
                voltageLevelId;equipmentType;xPosition;yPosition;xLabelPosition;yLabelPosition
                VL1;4;100;200;110;210""";

        MockMultipartFile file = new MockMultipartFile(
                "file", "positions.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8)
        );
        UUID positionsFromCsvUuid = wireMockStubs.stubCreatePositionsFromCsv();
        mockMvc.perform(MockMvcRequestBuilders.multipart("/v1/studies/network-visualizations/nad-positions-config")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
            .andExpect(status().isOk());

        // assert API calls have been made
        wireMockStubs.verifyStubCreatePositionsFromCsv(positionsFromCsvUuid);
    }

    @AfterEach
    void tearDown() {
        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
