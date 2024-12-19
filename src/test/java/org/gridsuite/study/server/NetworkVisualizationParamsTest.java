/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David BRAQUART <david.braquart at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NetworkVisualizationParamsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkVisualizationParamsTest.class);

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String NO_PROFILE_USER_ID = "noProfileUser";

    private static final String NETWORK_VISU_PARAMETERS_UUID_STRING = "5218bc26-1196-4ac5-a860-d7342359bca7";
    private static final UUID NETWORK_VISU_PARAMETERS_UUID = UUID.fromString(NETWORK_VISU_PARAMETERS_UUID_STRING);
    private static final String NETWORK_VISU_DEFAULT_PARAMETERS_JSON;

    static {
        try {
            NETWORK_VISU_DEFAULT_PARAMETERS_JSON = TestUtils.resourceToString("/network-visulization-default-parameters.json");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final long TIMEOUT = 1000;
    private static final String STUDY_UPDATE_DESTINATION = "study.update";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private StudyConfigService studyConfigService;

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
                String method = Objects.requireNonNull(request.getMethod());
                if (path.startsWith("/v1/network-visualizations-params")) {
                    if (method.equals("GET")) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), NETWORK_VISU_DEFAULT_PARAMETERS_JSON);
                    } else {
                        return new MockResponse(200);
                    }
                } else {
                    LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                    return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testGetParameters(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, NETWORK_VISU_PARAMETERS_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        //get dft network visu parameters
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/network-visualizations/parameters", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(NETWORK_VISU_DEFAULT_PARAMETERS_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().allMatch(r -> r.equals("/v1/network-visualizations-params/" + NETWORK_VISU_PARAMETERS_UUID_STRING))); // GET
    }

    @Test
    void testUpdateParameters(final MockWebServer server) throws Exception {
        testSetParameters(server, true);
    }

    @Test
    void testCreateParameters(final MockWebServer server) throws Exception {
        testSetParameters(server, false);
    }

    void testSetParameters(final MockWebServer server, boolean withVisuParams) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, withVisuParams ? NETWORK_VISU_PARAMETERS_UUID : null);
        UUID studyNameUserIdUuid = studyEntity.getId();
        mockMvc.perform(
                        post("/v1/studies/{studyUuid}/network-visualizations/parameters", studyNameUserIdUuid)
                                .header("userId", NO_PROFILE_USER_ID)
                                .contentType(MediaType.ALL)
                                .content(NETWORK_VISU_DEFAULT_PARAMETERS_JSON))
                .andExpect(status().is(HttpStatus.OK.value()));

        // check update notification on visu params
        Message<byte[]> message = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertEquals(studyEntity.getId(), message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        String updateType = (String) message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE);
        assertEquals(NotificationService.UPDATE_NETWORK_VISUALIZATION_PARAMETERS, updateType);

        var requests = TestUtils.getRequestsDone(1, server);
        // PUT or POST
        assertTrue(requests.stream().allMatch(r -> r.equals(String.format("/v1/network-visualizations-params%s", withVisuParams ? "/" + NETWORK_VISU_PARAMETERS_UUID_STRING : ""))));
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION);
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
        TestUtils.assertQueuesEmptyThenClear(destinations, output);
        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID networkVisuParametersUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, networkVisuParametersUuid);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }
}
