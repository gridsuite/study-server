/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Souissi Maissa <maissa.souissi at rte-france.com>
 */
@DisableElasticsearch
@SpringBootTest
@AutoConfigureMockMvc
class ModificationSearchTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModificationSearchTest.class);

    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final String CASE_NAME = "caseName";
    private static final String CASE_FORMAT = "caseFormat";
    private static final UUID REPORT_UUID = UUID.randomUUID();

    private static final String NODE_1_NAME = "node1";

    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private TestUtils testUtils;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private RootNetworkService rootNetworkService;
    private WireMockServer wireMockServer;

    @Autowired
    private NetworkModificationService networkModificationService;

    StudyEntity studyEntity;
    RootNetworkEntity rootNetworkEntity;
    NetworkModificationNode node1;
    private WireMockUtils wireMockUtils;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);

        // start server
        wireMockServer.start();

        String baseUrlWireMock = wireMockServer.baseUrl();
        networkModificationService.setNetworkModificationServerBaseUri(baseUrlWireMock);

        createStudyAndNode();
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private void createStudyAndNode() {
        studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, CASE_NAME, CASE_FORMAT, REPORT_UUID);
        studyRepository.save(studyEntity);
        rootNetworkEntity = testUtils.getOneRootNetwork(studyEntity.getId());
        NodeEntity rootNodeEntity = networkModificationTreeService.createRoot(studyEntity);
        node1 = networkModificationTreeService.createNode(studyEntity, rootNodeEntity.getIdNode(), createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);
    }

    @Test
    void testSearchModifications() throws Exception {
        UUID rootNetworkUuid = rootNetworkEntity.getId();

        Map<UUID, Object> modificationsSearchResultByGroup = new HashMap<>();
        modificationsSearchResultByGroup.put(networkModificationTreeService.getModificationGroupUuid(node1.getId()), List.of());

        String jsonBody = mapper.writeValueAsString(modificationsSearchResultByGroup);

        UUID stubUuid = wireMockUtils.stubSearchModifications(rootNetworkService.getNetworkUuid(rootNetworkUuid).toString(), "B", jsonBody);
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/modifications/indexation-infos?userInput=B",
                        studyEntity.getId(), rootNetworkUuid))
                .andExpect(status().isOk())
                .andReturn();

        wireMockUtils.verifySearchModifications(stubUuid, rootNetworkService.getNetworkUuid(rootNetworkUuid).toString(), "B");
    }
}
