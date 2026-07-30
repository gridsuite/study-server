/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.DynamicMappingService;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.client.util.UrlUtil;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.doAnswer;
import static org.mockito.BDDMockito.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class StudyControllerDynamicMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyControllerDynamicMappingTest.class);

    private static final String API_VERSION = StudyApi.API_VERSION;
    private static final String DELIMITER = "/";
    private static final String STUDY_END_POINT = "studies";

    private static final String STUDY_BASE_URL = UrlUtil.buildEndPointUrl("", API_VERSION, STUDY_END_POINT);
    private static final String STUDY_NETWORK_VALUES_END_POINT = "{studyUuid}/dynamic-mapping/network/values";
    private static final String STUDY_NETWORK_MATCHES_END_POINT = "{studyUuid}/dynamic-mapping/network/matches";

    private static final String HEADER_USER_ID_NAME = "userId";
    private static final String HEADER_USER_ID_VALUE = "userId";

    private static final UUID CASE_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID STUDY_UUID = UUID.randomUUID();

    private static final String RULE_TO_MATCH_JSON = "{\"filter\":null,\"ruleIndex\":0}";
    private static final String NETWORK_VALUES_JSON = "{\"propertyValues\":[]}";
    private static final String NETWORK_MATCHES_JSON = "[\"GEN1\",\"GEN2\"]";

    @Autowired
    private MockMvc studyClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @MockitoBean
    private LoadFlowService mockLoadFlowService;

    @MockitoBean
    private UserAdminService userAdminService;

    @MockitoSpyBean
    private DynamicMappingService spyDynamicMappingService;

    @Autowired
    private StudyRepository studyRepository;

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", UUID.randomUUID(), UUID.randomUUID(), null, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    @Test
    void testGetNetworkValuesFromStudy() throws Exception {
        // create a study in the db
        org.gridsuite.study.server.repository.StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // setup DynamicMappingService spy — stop at the service, do not call the remote server
        doAnswer(invocation -> NETWORK_VALUES_JSON)
                .when(spyDynamicMappingService).getNetworkValues(eq(NETWORK_UUID));

        // --- call endpoint to be tested --- //
        LOGGER.info("Calling getNetworkValuesFromStudy for studyUuid={}", studyUuid);
        MvcResult result = studyClient.perform(get(STUDY_BASE_URL + DELIMITER + STUDY_NETWORK_VALUES_END_POINT, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // --- check result --- //
        String resultJson = result.getResponse().getContentAsString();
        LOGGER.info("Network values result = {}", resultJson);
        assertThat(resultJson).isEqualTo(NETWORK_VALUES_JSON);

        // --- verify spy was called --- //
        verify(spyDynamicMappingService).getNetworkValues(NETWORK_UUID);
    }

    @Test
    void testGetNetworkMatchesFromStudy() throws Exception {
        // create a study in the db
        org.gridsuite.study.server.repository.StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();

        // setup DynamicMappingService spy — stop at the service, do not call the remote server
        doAnswer(invocation -> NETWORK_MATCHES_JSON)
                .when(spyDynamicMappingService).getNetworkMatches(eq(NETWORK_UUID), eq(RULE_TO_MATCH_JSON));

        // --- call endpoint to be tested --- //
        LOGGER.info("Calling getNetworkMatchesFromStudy for studyUuid={}", studyUuid);
        MvcResult result = studyClient.perform(post(STUDY_BASE_URL + DELIMITER + STUDY_NETWORK_MATCHES_END_POINT, studyUuid)
                        .header(HEADER_USER_ID_NAME, HEADER_USER_ID_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(RULE_TO_MATCH_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        // --- check result --- //
        String resultJson = result.getResponse().getContentAsString();
        LOGGER.info("Network matches result = {}", resultJson);
        assertThat(resultJson).isEqualTo(NETWORK_MATCHES_JSON);

        // --- verify spy was called --- //
        verify(spyDynamicMappingService).getNetworkMatches(NETWORK_UUID, RULE_TO_MATCH_JSON);
    }
}
