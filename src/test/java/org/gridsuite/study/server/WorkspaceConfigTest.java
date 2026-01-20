/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.WireMockUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.messaging.Message;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class WorkspaceConfigTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private StudyConfigService studyConfigService;

    private WireMockServer wireMockServer;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final UUID NETWORK_UUID = UUID.fromString("052f64fd-775f-4eb8-89ae-de76b713e349");
    private static final UUID CASE_UUID = UUID.fromString("619c0d0b-acdb-4578-a612-1bc17cc62411");
    private static final UUID WORKSPACES_CONFIG_UUID = UUID.fromString("bd239d4c-b9dc-4d25-9093-4f1362e4b731");
    private static final UUID WORKSPACE_ID = UUID.fromString("3d75aab3-158d-4f94-922e-d96facd9871b");
    private static final UUID PANEL_ID = UUID.fromString("f8b6c4d2-9a1e-4c5d-8f3b-2a7e1d9c5b4a");

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        studyConfigService.setStudyConfigServerBaseUri(baseUrlWireMock);
    }

    @Test
    void testGetWorkspaces() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces";

        String responseBody = "response";

        UUID stubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.ok(responseBody))).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/workspaces", studyEntity.getId())
                        .header("content-type", "application/json"))
                .andExpect(status().isOk())
                .andReturn();

        WireMockUtils.verifyGetRequest(wireMockServer, stubId, configServerUrl, Map.of());
    }

    @Test
    void testGetWorkspace() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID;

        String responseBody = "response";

        UUID stubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.ok(responseBody))).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/workspaces/{workspaceId}", studyEntity.getId(), WORKSPACE_ID)
                        .header("content-type", "application/json"))
                .andExpect(status().isOk())
                .andReturn();

        WireMockUtils.verifyGetRequest(wireMockServer, stubId, configServerUrl, Map.of());
    }

    @Test
    void testRenameWorkspace() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/name";

        UUID stubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String body = objectMapper.writeValueAsString("new name");
        mockMvc.perform(put("/v1/studies/{studyUuid}/workspaces/{workspaceId}/name", studyEntity.getId(), WORKSPACE_ID)
                        .header("content-type", "application/json")
                        .content(body))
                .andExpect(status().isNoContent())
                .andReturn();

        checkWorkspaceUpdateMessageReceived(studyEntity.getId());
        WireMockUtils.verifyPutRequest(wireMockServer, stubId, configServerUrl, false, Map.of(), body);
    }

    @Test
    void testGetPanels() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels";

        String responseBody = "response";

        UUID stubId = wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.ok(responseBody))).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels", studyEntity.getId(), WORKSPACE_ID)
                        .header("content-type", "application/json"))
                .andExpect(status().isOk())
                .andReturn();

        WireMockUtils.verifyGetRequest(wireMockServer, stubId, configServerUrl, Map.of());
    }

    @Test
    void testGetPanelsWithIds() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String panelId1 = UUID.randomUUID().toString();
        String panelId2 = UUID.randomUUID().toString();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels";

        String responseBody = "response";

        wireMockServer.stubFor(WireMock.get(WireMock.urlPathMatching(configServerUrl + ".*"))
                .willReturn(WireMock.ok(responseBody)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels", studyEntity.getId(), WORKSPACE_ID)
                        .param("panelIds", panelId1, panelId2)
                        .header("content-type", "application/json"))
                .andExpect(status().isOk())
                .andReturn();

        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo(configServerUrl))
                .withQueryParam("panelIds", WireMock.equalTo(panelId1))
                .withQueryParam("panelIds", WireMock.equalTo(panelId2)));
    }

    @Test
    void testCreateOrUpdatePanels() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels";

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String body = "body";

        mockMvc.perform(post("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels", studyEntity.getId(), WORKSPACE_ID)
                        .header("content-type", "application/json")
                        .content(body))
                .andExpect(status().isNoContent())
                .andReturn();

        checkWorkspacePanelsUpdateMessageReceived(studyEntity.getId());
        WireMockUtils.verifyPostRequest(wireMockServer, stubId, configServerUrl, Map.of(), 1);
    }

    @Test
    void testDeletePanels() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels";

        UUID stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String body = "body";

        mockMvc.perform(delete("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels", studyEntity.getId(), WORKSPACE_ID)
                        .header("content-type", "application/json")
                        .content(body))
                .andExpect(status().isNoContent())
                .andReturn();

        checkWorkspacePanelsDeletedMessageReceived(studyEntity.getId());
        WireMockUtils.verifyDeleteRequest(wireMockServer, stubId, configServerUrl, false, Map.of());
    }

    @Test
    void testSaveNadConfig() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels/" + PANEL_ID + "/current-nad-config";

        UUID savedConfigUuid = UUID.randomUUID();
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.status(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + savedConfigUuid + "\""))).getId();

        Map<String, Object> nadConfigData = Map.of("key", "value");
        String body = objectMapper.writeValueAsString(nadConfigData);

        mockMvc.perform(post("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels/{panelId}/current-nad-config",
                        studyEntity.getId(), WORKSPACE_ID, PANEL_ID)
                        .header("content-type", "application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        checkWorkspaceNadConfigUpdateMessageReceived(studyEntity.getId());
        WireMockUtils.verifyPostRequest(wireMockServer, stubId, configServerUrl, Map.of(), 1);
    }

    @Test
    void testDeleteNadConfig() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/workspaces-configs/" + WORKSPACES_CONFIG_UUID + "/workspaces/" + WORKSPACE_ID + "/panels/" + PANEL_ID + "/current-nad-config";

        UUID stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        mockMvc.perform(delete("/v1/studies/{studyUuid}/workspaces/{workspaceId}/panels/{panelId}/current-nad-config",
                        studyEntity.getId(), WORKSPACE_ID, PANEL_ID)
                        .header("content-type", "application/json"))
                .andExpect(status().isNoContent())
                .andReturn();

        WireMockUtils.verifyDeleteRequest(wireMockServer, stubId, configServerUrl, false, Map.of());
    }

    private StudyEntity insertStudy() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, CASE_UUID, "", "", UUID.randomUUID());
        studyEntity.setWorkspacesConfigUuid(WORKSPACES_CONFIG_UUID);
        return studyRepository.save(studyEntity);
    }

    private void checkWorkspaceUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> message = output.receive(1000, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_WORKSPACE_RENAMED, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkWorkspaceNadConfigUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> message = output.receive(1000, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_WORKSPACE_NAD_CONFIG, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkWorkspacePanelsUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> message = output.receive(1000, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_WORKSPACE_PANELS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    private void checkWorkspacePanelsDeletedMessageReceived(UUID studyUuid) {
        Message<byte[]> message = output.receive(1000, STUDY_UPDATE_DESTINATION);
        assertEquals(studyUuid, message.getHeaders().get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.DELETE_WORKSPACE_PANELS, message.getHeaders().get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
        studyRepository.deleteAll();
    }
}
