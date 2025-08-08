package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import mockwebserver3.MockWebServer;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David BRAQUART <david.braquart at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SpreadsheetConfigTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OutputDestination output;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;
    @Autowired
    private StudyConfigService studyConfigService;

    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;

    private static final String STUDY_UPDATE_DESTINATION = "study.update";
    private static final UUID NETWORK_UUID = UUID.fromString("052f64fd-775f-4eb8-89ae-de76b713e349");
    private static final UUID CASE_UUID = UUID.fromString("619c0d0b-acdb-4578-a612-1bc17cc62411");
    private static final UUID SPREADSHEET_CONFIG_UUID = UUID.fromString("bd239d4c-b9dc-4d25-9093-4f1362e4b731");
    private static final UUID SPREADSHEET_CONFIG_COLUMN_UUID = UUID.fromString("3d75aab3-158d-4f94-922e-d96facd9871b");

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();
        studyConfigService.setStudyConfigServerBaseUri(baseUrlWireMock);
        wireMockUtils = new WireMockUtils(wireMockServer);
    }

    @Test
    void testReorder() throws Exception {
        StudyEntity studyEntity = insertStudy();
        final String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/reorder";

        UUID stubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String newOrder = objectMapper.writeValueAsString(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/reorder", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json")
                        .content(newOrder))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPutRequest(stubId, configServerUrl, false, Map.of(), newOrder);
    }

    @Test
    void testUpdateColumnsStates() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/states";

        UUID stubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String columnsStates = objectMapper.writeValueAsString(List.of(
                Map.of("columnId", "col1", "visible", true, "order", 1),
                Map.of("columnId", "col2", "visible", false, "order", 2),
                Map.of("columnId", "col3", "visible", true, "order", 0)
        ));
        mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/states", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json")
                        .content(columnsStates))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPutRequest(stubId, configServerUrl, false, Map.of(), columnsStates);
    }

    @Test
    void testRename() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/name";

        UUID stubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String body = objectMapper.writeValueAsString("new name");
        mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/name", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json")
                        .content(body))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPutRequest(stubId, configServerUrl, false, Map.of(), body);
    }

    @Test
    void testDeleteColumn() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/" + SPREADSHEET_CONFIG_COLUMN_UUID;

        UUID stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        mockMvc.perform(delete("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/{columnUuid}", studyEntity.getId(), SPREADSHEET_CONFIG_UUID, SPREADSHEET_CONFIG_COLUMN_UUID)
                        .header("content-type", "application/json"))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyDeleteRequest(stubId, configServerUrl, false, Map.of());
    }

    @Test
    void testDuplicateColumn() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/" + SPREADSHEET_CONFIG_COLUMN_UUID + "/duplicate";

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/{columnUuid}/duplicate", studyEntity.getId(), SPREADSHEET_CONFIG_UUID, SPREADSHEET_CONFIG_COLUMN_UUID)
                        .header("content-type", "application/json"))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPostRequest(stubId, configServerUrl, Map.of(), 1);
    }

    @Test
    void testUpdateColumn() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/" + SPREADSHEET_CONFIG_COLUMN_UUID;

        UUID stubId = wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String columnDto = "{\"name\": \"Substation ID\",\"id\": \"substationuuid\",\"type\": \"TEXT\",\"precision\": null,\"formula\": \"substationId\"}";
        mockMvc.perform(put("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/{columnUuid}", studyEntity.getId(), SPREADSHEET_CONFIG_UUID, SPREADSHEET_CONFIG_COLUMN_UUID)
                        .header("content-type", "application/json")
                        .content(columnDto))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPutRequest(stubId, configServerUrl, false, Map.of(), columnDto);
    }

    @Test
    void testCreateColumn() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns";
        String newColumnUuidJsonResult = objectMapper.writeValueAsString(SPREADSHEET_CONFIG_COLUMN_UUID);
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.created().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(newColumnUuidJsonResult))).getId();

        String columnDto = "{\"name\": \"Substation ID\",\"id\": \"substationuuid\",\"type\": \"TEXT\",\"precision\": null,\"formula\": \"substationId\"}";
        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json")
                        .content(columnDto))
                .andExpect(status().isCreated())
                .andReturn();
        JSONAssert.assertEquals(newColumnUuidJsonResult, mvcResult.getResponse().getContentAsString(), JSONCompareMode.NON_EXTENSIBLE);

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPostRequest(stubId, configServerUrl, false, Map.of(), columnDto);
    }

    @Test
    void testSetGlobalFilters() throws Exception {
        StudyEntity studyEntity = insertStudy();
        String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/global-filters";

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        String globalFilters = "[{" +
                "\"uuid\":\"" + UUID.randomUUID() + "\"," +
                "\"filterId\":\"" + UUID.randomUUID() + "\"," +
                "\"name\":\"Voltage Level Filter\"" +
                "}]";

        mockMvc.perform(post("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/global-filters", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json")
                        .content(globalFilters))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyPostRequest(stubId, configServerUrl, false, Map.of(), globalFilters);
    }

    @Test
    void testResetFilters(final MockWebServer server) throws Exception {
        StudyEntity studyEntity = insertStudy();
        final String configServerUrl = "/v1/spreadsheet-configs/" + SPREADSHEET_CONFIG_UUID + "/columns/filters";

        UUID stubId = wireMockServer.stubFor(WireMock.delete(WireMock.urlPathEqualTo(configServerUrl))
                .willReturn(WireMock.noContent())).getId();

        mockMvc.perform(delete("/v1/studies/{studyUuid}/spreadsheet-config/{configUuid}/columns/filters", studyEntity.getId(), SPREADSHEET_CONFIG_UUID)
                        .header("content-type", "application/json"))
                .andExpect(status().isNoContent())
                .andReturn();

        checkSpreadsheetTabUpdateMessageReceived(studyEntity.getId());
        wireMockUtils.verifyDeleteRequest(stubId, configServerUrl, false, Map.of(), 1);
    }

    private StudyEntity insertStudy() {
        StudyEntity studyEntity = TestUtils.createDummyStudy(NETWORK_UUID, "netId", CASE_UUID, "", "", SPREADSHEET_CONFIG_UUID);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private void checkSpreadsheetTabUpdateMessageReceived(UUID studyUuid) {
        Message<byte[]> messageStudyUpdate = output.receive(1000, STUDY_UPDATE_DESTINATION);
        MessageHeaders headersStudyUpdate = messageStudyUpdate.getHeaders();
        assertEquals(studyUuid, headersStudyUpdate.get(NotificationService.HEADER_STUDY_UUID));
        assertEquals(NotificationService.UPDATE_SPREADSHEET_TAB, headersStudyUpdate.get(NotificationService.HEADER_UPDATE_TYPE));
    }

    @AfterEach
    void tearDown() {
        TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        TestUtils.assertQueuesEmptyThenClear(List.of(STUDY_UPDATE_DESTINATION), output);
    }
}
