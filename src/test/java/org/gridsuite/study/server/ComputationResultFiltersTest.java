/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ComputationResultFiltersTest {
    private static final String BASE_URI = "/v1/computation-result-filters/";
    public static final String DEFAULT = "default";
    private static final String COMPUTATION_FILTERS_JSON = "{\"computationResultFilters\":[]}";
    private static final UUID COMPUTATION_FILTERS_UUID = UUID.randomUUID();
    private static final String COMPUTATION_TYPE = "LoadFlow";
    private static final String COMPUTATION_SUB_TYPE = "LoadFlowResultsVoltageViolations";
    private WireMockServer wireMockServer;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudyConfigService studyConfigService;
    @Autowired
    private StudyRepository studyRepository;

    private void stubGetGlobalFilters() {
        stubGet(BASE_URI + COMPUTATION_FILTERS_UUID + "/" + ComputationResultFiltersTest.COMPUTATION_TYPE);
    }

    private void stubGetColumnFilters() {
        stubGet(BASE_URI + COMPUTATION_FILTERS_UUID + "/" + ComputationResultFiltersTest.COMPUTATION_TYPE + "/" + COMPUTATION_SUB_TYPE);
    }

    private void stubGet(String url) {
        wireMockServer.stubFor(WireMock.get(urlEqualTo(url))
                .willReturn(okJson(COMPUTATION_FILTERS_JSON)));
    }

    private void stubCreateDefaultFilters() {
        wireMockServer.stubFor(WireMock.post(urlEqualTo(BASE_URI + DEFAULT)).willReturn(okJson("\"" + COMPUTATION_FILTERS_UUID + "\"")));
    }

    private void stubSetGlobalFilters() {
        wireMockServer.stubFor(WireMock.post(urlEqualTo(BASE_URI + COMPUTATION_FILTERS_UUID +
                "/" + COMPUTATION_TYPE + "/global-filters")).willReturn(noContent()));
    }

    private void stubUpdateColumns() {
        wireMockServer.stubFor(WireMock.put(urlEqualTo(BASE_URI + COMPUTATION_FILTERS_UUID + "/" +
                        COMPUTATION_TYPE + "/" + COMPUTATION_SUB_TYPE + "/columns")).willReturn(noContent()));
    }

    private void verifyDefaultFiltersCalledOnce() {
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(BASE_URI + DEFAULT)));
    }

    private void verifyDefaultFiltersNotCalled() {
        wireMockServer.verify(0, postRequestedFor(urlEqualTo(BASE_URI + DEFAULT)));
    }

    private void verifyColumnFiltersCalledOnce() {
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(BASE_URI + COMPUTATION_FILTERS_UUID +
                "/" + COMPUTATION_TYPE + "/" + COMPUTATION_SUB_TYPE)));
    }

    private void verifyGlobalFiltersCalledOnce() {
        wireMockServer.verify(1, getRequestedFor(urlEqualTo(BASE_URI + COMPUTATION_FILTERS_UUID + "/" + COMPUTATION_TYPE)));
    }

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        studyConfigService.setStudyConfigServerBaseUri(wireMockServer.baseUrl());
        stubCreateDefaultFilters();
        stubGetGlobalFilters();
        stubGetColumnFilters();
        stubSetGlobalFilters();
        stubUpdateColumns();
    }

    @Test
    void getComputationResultFilters() throws Exception {
        StudyEntity study = insertDummyStudy(null);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters/{computationType}/{computationSubType}",
                study.getId(), COMPUTATION_TYPE, COMPUTATION_SUB_TYPE)).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        verifyDefaultFiltersCalledOnce();
        verifyColumnFiltersCalledOnce();
        wireMockServer.resetRequests();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters/{computationType}/{computationSubType}",
                study.getId(), COMPUTATION_TYPE, COMPUTATION_SUB_TYPE)).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        verifyDefaultFiltersNotCalled();
        verifyColumnFiltersCalledOnce();
        wireMockServer.resetRequests();

        study = insertDummyStudy(null);
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters/{computationType}",
                study.getId(), COMPUTATION_TYPE)).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        verifyDefaultFiltersCalledOnce();
        verifyGlobalFiltersCalledOnce();
        wireMockServer.resetRequests();

        study = insertDummyStudy(COMPUTATION_FILTERS_UUID);
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters/{computationType}",
                study.getId(), COMPUTATION_TYPE)).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        verifyDefaultFiltersNotCalled();
        verifyGlobalFiltersCalledOnce();
    }

    @Test
    void setGlobalFilters() throws Exception {
        StudyEntity study = insertDummyStudy(COMPUTATION_FILTERS_UUID);
        String json = "{\"globalFilters\":[]}";
        mockMvc.perform(post("/v1/studies/{studyUuid}/computation-result-filters/{computationType}/global-filters", study.getId(), COMPUTATION_TYPE)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNoContent());
    }

    @Test
    void updateColumn() throws Exception {
        StudyEntity study = insertDummyStudy(COMPUTATION_FILTERS_UUID);
        String json = "{\"columnsFilters\":[]}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/computation-result-filters/{computationType}/{computationSubType}/columns", study.getId(),
                COMPUTATION_TYPE, COMPUTATION_SUB_TYPE).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNoContent());
    }

    private StudyEntity insertDummyStudy(UUID computationResultFiltersUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID()).computationResultFiltersUuid(computationResultFiltersUuid).build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName")
                .tag("dum").caseFormat("").caseUuid(UUID.randomUUID()).caseName("").networkId(String.valueOf(UUID.randomUUID())).networkUuid(UUID.randomUUID()).build();
        studyEntity.addRootNetwork(rootNetworkEntity);
        return studyRepository.save(studyEntity);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
