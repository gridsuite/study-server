/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.gridsuite.study.server.service.UserAdminService;
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
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SpreadsheetConfigCollectionTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpreadsheetConfigCollectionTest.class);

    private static final String CASE_LOADFLOW_UUID_STRING = "11a91c11-2c2d-83bb-b45f-20b83e4ef00c";
    private static final UUID CASE_LOADFLOW_UUID = UUID.fromString(CASE_LOADFLOW_UUID_STRING);
    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

    private static final String SPREADSHEET_CONFIG_COLLECTION_UUID_STRING = "5218bc26-1196-4ac5-a860-d7342359bca7";
    private static final UUID SPREADSHEET_CONFIG_COLLECTION_UUID = UUID.fromString(SPREADSHEET_CONFIG_COLLECTION_UUID_STRING);
    private static final String NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING = "6329cd37-2287-5bd6-b971-e8453fa9cdb8";
    private static final String NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON = "\"" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "\"";
    private static final UUID NEW_SPREADSHEET_CONFIG_COLLECTION_UUID = UUID.fromString(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING);
    private static final String APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING = "882e7f07-0b02-4f34-990d-54fa0831deda";
    private static final UUID APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID = UUID.fromString(APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING);
    private static final String SPREADSHEET_CONFIG_UUID_STRING = "b9b7f99e-0491-4b30-b555-0d201b14d005";
    private static final UUID SPREADSHEET_CONFIG_UUID = UUID.fromString(SPREADSHEET_CONFIG_UUID_STRING);
    private static final String NEW_SPREADSHEET_CONFIG_UUID_STRING = "86c94a57-c296-4eb1-b378-eea2fa524fd4";
    private static final String NEW_SPREADSHEET_CONFIG_UUID_JSON = "\"" + NEW_SPREADSHEET_CONFIG_UUID_STRING + "\"";

    private static final String NO_PROFILE_USER_ID = "noProfileUser";
    private static final String VALID_PROFILE_USER_ID = "validProfileUser";
    private static final String USER_PROFILE_VALID_PARAMS_JSON = "{\"id\":\"97bb1890-a90c-43c3-a004-e631246d42d6\",\"name\":\"Profile with valid params\",\"spreadsheetConfigCollectionId\":\"" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "\"}";

    // UUID for testing delete failure
    private static final String ERROR_DELETE_COLLECTION_UUID_STRING = "7715da48-3390-47cb-8d9a-f936c8ca6a71";
    private static final UUID ERROR_DELETE_COLLECTION_UUID = UUID.fromString(ERROR_DELETE_COLLECTION_UUID_STRING);

    private static final String SPREADSHEET_CONFIG_COLLECTION_JSON;
    private static final String NEW_SPREADSHEET_CONFIG_COLLECTION_JSON;
    private static final String SPREADSHEET_CONFIG_COLLECTION_UUID_JSON = "\"" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "\"";
    private static final String SPREADSHEET_CONFIG_JSON;

    static {
        try {
            SPREADSHEET_CONFIG_COLLECTION_JSON = TestUtils.resourceToString("/spreadsheet-config-collection.json");
            NEW_SPREADSHEET_CONFIG_COLLECTION_JSON = TestUtils.resourceToString("/spreadsheet-config-updated-collection.json");
            SPREADSHEET_CONFIG_JSON = TestUtils.resourceToString("/spreadsheet-config.json");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final long TIMEOUT = 1000;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private StudyConfigService studyConfigService;
    @Autowired
    private UserAdminService userAdminService;

    @BeforeEach
    void setup(final MockWebServer server) {
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        studyConfigService.setStudyConfigServerBaseUri(baseUrl);
        userAdminService.setUserAdminServerBaseUri(baseUrl);

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                String method = request.getMethod();
                if (path.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)) {
                    if ("GET".equals(method)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SPREADSHEET_CONFIG_COLLECTION_JSON);
                    } else if ("PUT".equals(method)) {
                        return new MockResponse(200);
                    }
                } else if (path.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/reorder")) {
                    return new MockResponse(204);
                } else if ("DELETE".equals(method) && path.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/spreadsheet-configs" + SPREADSHEET_CONFIG_UUID)) {
                    return new MockResponse(204);
                } else if ("POST".equals(method) && path.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/spreadsheet-configs")) {
                    return new MockResponse(201, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), NEW_SPREADSHEET_CONFIG_UUID_JSON);
                } else if (path.equals("/v1/spreadsheet-config-collections/" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)) {
                    if ("GET".equals(method)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), NEW_SPREADSHEET_CONFIG_COLLECTION_JSON);
                    } else if ("PUT".equals(method)) {
                        return new MockResponse(200);
                    } else if ("DELETE".equals(method)) {
                        return new MockResponse(200);
                    }
                } else if (path.equals("/v1/spreadsheet-config-collections/non-existing-collection")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=.*") && "POST".equals(method)) {
                    String collectionId = path.substring(path.lastIndexOf("=") + 1);
                    if (collectionId.equals("non-existing-collection")) {
                        return new MockResponse(404);
                    }
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                } else if (path.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING + "/append\\?sourceCollection=.*")) {
                    return new MockResponse(200);
                } else if (path.matches("/v1/spreadsheet-config-collections/.*") && "DELETE".equals(method)) {
                    // Return an error for the specific UUID that should trigger a delete error
                    if (path.contains(ERROR_DELETE_COLLECTION_UUID_STRING)) {
                        return new MockResponse(HttpStatus.INTERNAL_SERVER_ERROR.value());
                    }
                    return new MockResponse(200);
                } else if (path.equals("/v1/spreadsheet-config-collections/default")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                } else if (path.equals("/v1/spreadsheet-config-collections") && "POST".equals(method)) {
                    String body = null;
                    try {
                        body = request.getBody().readUtf8();
                    } catch (Exception e) {
                        LOGGER.error("Error reading request body", e);
                    }
                    if (body != null && body.equals(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON)) {
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                    }
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SPREADSHEET_CONFIG_COLLECTION_UUID_JSON);
                } else if (path.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(404);
                } else if (path.matches("/v1/users/" + VALID_PROFILE_USER_ID + "/profile")) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), USER_PROFILE_VALID_PARAMS_JSON);
                }
                LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testUpdateSpreadsheetConfigCollection(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test successful update
        MvcResult mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .param("collectionUuid", SPREADSHEET_CONFIG_COLLECTION_UUID.toString())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        JSONAssert.assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        // Test that the study has been updated
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify the HTTP requests made to the server
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + SPREADSHEET_CONFIG_COLLECTION_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testAppendSpreadsheetConfigCollection(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test successful update in append mode
        MvcResult mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .param("collectionUuid", APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID.toString())
                        .param("append", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        JSONAssert.assertEquals(SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        // Test that the collectionId has not changed (updated only)
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify the HTTP requests made to the server
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/append\\?sourceCollection=" + APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID)));
    }

    @Test
    void testAppendSpreadsheetConfigCollectionEmptyCase(final MockWebServer server) throws Exception {
        // Create a study with no collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyUuid = studyEntity.getId();

        // Test successful update in append mode
        MvcResult mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .param("collectionUuid", APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID.toString())
                        .param("append", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        JSONAssert.assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        // Test that the collectionId has been set
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify the HTTP requests made to the server
        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + APPENDED_SPREADSHEET_CONFIG_COLLECTION_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testUpdateSpreadsheetConfigCollectionWithDeleteError(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        // Use the special UUID that will trigger a delete error
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, ERROR_DELETE_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test update - despite the delete error, this should succeed
        MvcResult mvcResult = mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .param("collectionUuid", SPREADSHEET_CONFIG_COLLECTION_UUID.toString())
                        .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn();
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        // Verify the response contains the new collection
        JSONAssert.assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        // Test that the study has been updated despite the delete error
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify the HTTP requests made to the server
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=" + SPREADSHEET_CONFIG_COLLECTION_UUID)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + ERROR_DELETE_COLLECTION_UUID_STRING)));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testGetSpreadsheetCollection(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyNameUserIdUuid)).andExpectAll(
                status().isOk()).andReturn();

        JSONAssert.assertEquals(SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().allMatch(r -> r.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testGetSpreadsheetCollectionNotFound(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyNameUserIdUuid = studyEntity.getId();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyNameUserIdUuid)).andExpectAll(
            status().isOk()).andReturn();

        JSONAssert.assertEquals(SPREADSHEET_CONFIG_COLLECTION_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        var requests = TestUtils.getRequestsDone(2, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/spreadsheet-config-collections/default")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testSetSpreadsheetConfigCollection(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test setting a new spreadsheet config collection with body
        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .content(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(StudyConstants.HEADER_USER_ID, NO_PROFILE_USER_ID))
                .andExpect(status().isOk());
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        // Check that the study still has the same collection UUID
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify HTTP requests made to the server - look for the PUT to update the existing collection
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r ->
                r.contains("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING)));
    }

    @Test
    void testSetSpreadsheetConfigCollectionWithNonExistingCollection(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, null);
        UUID studyUuid = studyEntity.getId();

        // Test setting a new spreadsheet config collection with body
        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .content(NEW_SPREADSHEET_CONFIG_COLLECTION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(StudyConstants.HEADER_USER_ID, NO_PROFILE_USER_ID))
                .andExpect(status().isOk());
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        // Check that the study have the new created collection
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify HTTP requests made to the server - look for the POST to create the new collection
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/spreadsheet-config-collections")));
    }

    @Test
    void testResetToDefaultWithUserProfile(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test resetting to default (empty body)
        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(StudyConstants.HEADER_USER_ID, VALID_PROFILE_USER_ID))
                .andExpect(status().isOk());
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        // Check that the study has been updated with the new collection from user profile
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(NEW_SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify HTTP requests made to the server - should duplicate from a profile collection
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/users/" + VALID_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections\\?duplicateFrom=.*")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID_STRING))); // delete old collection

    }

    @Test
    void testResetToDefaultWithoutUserProfile(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, NEW_SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();

        // Test resetting to default when user profile attempt fails (empty body)
        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config-collection", studyUuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(StudyConstants.HEADER_USER_ID, NO_PROFILE_USER_ID))
                .andExpect(status().isOk());
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);

        // Check that the study has been updated with the system default collection
        StudyEntity updatedStudy = studyRepository.findById(studyUuid).orElseThrow();
        assertEquals(SPREADSHEET_CONFIG_COLLECTION_UUID, updatedStudy.getSpreadsheetConfigCollectionUuid());

        // Verify HTTP requests made to the server - should call default and delete
        var requests = TestUtils.getRequestsDone(3, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/users/" + NO_PROFILE_USER_ID + "/profile")));
        assertTrue(requests.stream().anyMatch(r -> r.equals("/v1/spreadsheet-config-collections/default")));
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + NEW_SPREADSHEET_CONFIG_COLLECTION_UUID_STRING))); // delete old collection
    }

    @Test
    void testReorderCollection(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();
        String newOrder = objectMapper.writeValueAsString(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config-collection/{collectionUuid}/reorder", studyUuid, SPREADSHEET_CONFIG_COLLECTION_UUID)
                        .content(newOrder)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andReturn();
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/reorder")));
    }

    @Test
    void testRemoveConfig(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();
        mockMvc.perform(delete("/v1/studies/{studyUuid}/spreadsheet-config-collection/{collectionUuid}/spreadsheet-configs/{configId}", studyUuid, SPREADSHEET_CONFIG_COLLECTION_UUID, SPREADSHEET_CONFIG_UUID))
                .andExpect(status().isNoContent());
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID)));
    }

    @Test
    void testAddConfig(final MockWebServer server) throws Exception {
        // Create a study with an existing spreadsheet config collection
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_LOADFLOW_UUID, SPREADSHEET_CONFIG_COLLECTION_UUID);
        UUID studyUuid = studyEntity.getId();
        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config-collection/{collectionUuid}/spreadsheet-configs", studyUuid, SPREADSHEET_CONFIG_COLLECTION_UUID)
                        .content(SPREADSHEET_CONFIG_JSON)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();
        JSONAssert.assertEquals(NEW_SPREADSHEET_CONFIG_UUID_JSON, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);
        checkSpreadsheetCollectionUpdateMessageReceived(studyUuid);
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/spreadsheet-config-collections/" + SPREADSHEET_CONFIG_COLLECTION_UUID + "/spreadsheet-configs")));
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

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid, UUID spreadsheetConfigCollectionUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", spreadsheetConfigCollectionUuid);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private void checkSpreadsheetCollectionUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> messageStudyUpdate = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_SPREADSHEET_COLLECTION, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }
}
