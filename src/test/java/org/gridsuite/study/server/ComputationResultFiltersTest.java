/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class ComputationResultFiltersTest {
    private static final String COMPUTATION_FILTERS_JSON = "{\"computationResultFilters\":[]}";
    private static final UUID COMPUTATION_FILTERS_UUID = UUID.randomUUID();
    private static final UUID COMPUTATION_GLOBAL_FILTERS_UUID = UUID.randomUUID();
    private static final UUID COLUMN_UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudyConfigService studyConfigService;
    @Autowired
    private StudyRepository studyRepository;

    @BeforeEach
    void setup(final MockWebServer server) {
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        studyConfigService.setStudyConfigServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = request.getMethod();
                if ("GET".equals(method) && path.equals("/v1/computation-result-filters/" + COMPUTATION_FILTERS_UUID)) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), COMPUTATION_FILTERS_JSON);
                }
                if ("POST".equals(method) && path.equals("/v1/computation-result-filters/default")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "\"" + COMPUTATION_FILTERS_UUID + "\"");
                }
                if ("POST".equals(method) && path.equals("/v1/computation-result-filters/" + COMPUTATION_GLOBAL_FILTERS_UUID + "/global-filters")) {
                    return new MockResponse(204, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), COMPUTATION_FILTERS_JSON);
                }
                if ("PUT".equals(method) && path.equals("/v1/computation-result-filters/" + COMPUTATION_FILTERS_UUID + "/columns/" + COLUMN_UUID)) {
                    return new MockResponse(204, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), COMPUTATION_FILTERS_JSON);
                }
                return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void getComputationResultFilters(final MockWebServer server) throws Exception {
        StudyEntity study = insertDummyStudy(null);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters", study.getId())).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/computation-result-filters/default")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/computation-result-filters/" + COMPUTATION_FILTERS_UUID)));

        study = insertDummyStudy(COMPUTATION_FILTERS_UUID);
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/computation-result-filters", study.getId())).andExpectAll(status().isOk()).andReturn();
        JSONAssert.assertEquals(COMPUTATION_FILTERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().allMatch(r -> r.equals("/v1/computation-result-filters/" + COMPUTATION_FILTERS_UUID)));
    }

    @Test
    void setGlobalFilters() throws Exception {
        StudyEntity study = insertDummyStudy(COMPUTATION_GLOBAL_FILTERS_UUID);
        String json = "{\"globalFilters\":[]}";
        mockMvc.perform(post("/v1/studies/{studyUuid}/computation-result-filters/{id}/global-filters", study.getId(), COMPUTATION_GLOBAL_FILTERS_UUID)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isNoContent());
    }

    private StudyEntity insertDummyStudy(UUID computationResultFiltersUuid) {
        StudyEntity studyEntity = StudyEntity.builder().id(UUID.randomUUID()).computationResultFiltersUuid(computationResultFiltersUuid).build();
        RootNetworkEntity rootNetworkEntity = RootNetworkEntity.builder().id(UUID.randomUUID()).name("rootNetworkName")
                .tag("dum").caseFormat("").caseUuid(UUID.randomUUID()).caseName("").networkId(String.valueOf(UUID.randomUUID())).networkUuid(UUID.randomUUID()).build();
        studyEntity.addRootNetwork(rootNetworkEntity);
        return studyRepository.save(studyEntity);
    }
}
