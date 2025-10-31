package org.gridsuite.study.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.StudyConfigService;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.AbstractDiagramLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.DiagramPosition;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.MapLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NadVoltageLevelPositionInfos;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayoutDetails;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.gridsuite.study.server.service.DiagramGridLayoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.STUDY_CONFIG_API_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class DiagramGridLayoutTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyConfigService studyConfigService;
    @Autowired
    private SingleLineDiagramService singleLineDiagramService;
    @Autowired
    private DiagramGridLayoutService diagramGridLayoutService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();

        studyConfigService.setStudyConfigServerBaseUri(baseUrlWireMock);
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrlWireMock);
    }

    @Test
    void testCreateDiagramGridLayout() throws Exception {
        String payload = "{}";
        String expectedPayload = "{\"diagramLayouts\":null}";
        UUID expectedDiagramGridLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).build());

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout")
            .withRequestBody(equalTo(expectedPayload))
            .willReturn(WireMock.ok()
            .withBody(objectMapper.writeValueAsString(expectedDiagramGridLayoutUuid))
            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID diagramGridLayoutUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertEquals(expectedDiagramGridLayoutUuid, diagramGridLayoutUuidResult);
    }

    @Test
    void testUpdateDiagramGridLayout() throws Exception {
        String payload = "{}";
        String expectedPayload = "{\"diagramLayouts\":null}";
        UUID existingDiagramGridLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(existingDiagramGridLayoutUuid).build());

        // Mock GET to retrieve existing layout (needed for cleanup logic)
        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody("{\"diagramLayouts\":[]}")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .withRequestBody(equalTo(expectedPayload))
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(existingDiagramGridLayoutUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID diagramGridLayoutUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertEquals(existingDiagramGridLayoutUuid, diagramGridLayoutUuidResult);
    }

    @Test
    void testGetDiagramGridLayout() throws Exception {
        String diagramGridLayoutJson = "{\"diagramLayouts\":[]}";
        UUID diagramGridLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(diagramGridLayoutUuid).build());

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(diagramGridLayoutJson)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid))
            .andExpect(status().isOk())
            .andReturn();

        String diagramGridLayoutResult = mvcResult.getResponse().getContentAsString();

        assertEquals(diagramGridLayoutJson, diagramGridLayoutResult);
    }

    @Test
    void testGetDiagramGridLayoutNoContent() throws Exception {
        UUID diagramGridLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(diagramGridLayoutUuid).build());

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid))
            .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteDiagramGridLayout() {
        UUID diagramGridLayoutUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.ok()));

        studyConfigService.deleteDiagramGridLayout(diagramGridLayoutUuid);

        RequestPatternBuilder requestBuilder = WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid));
        wireMockServer.verify(1, requestBuilder);
    }

    @Test
    void testDeleteDiagramGridLayoutWithError() {
        UUID diagramGridLayoutUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.deleteDiagramGridLayout(diagramGridLayoutUuid);
        });

        assertEquals(StudyException.Type.DIAGRAM_GRID_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.deleteDiagramGridLayout(diagramGridLayoutUuid);
        });
    }

    @Test
    void testSaveDiagramGridLayoutWithError() {
        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder().diagramLayouts(List.of()).build();

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout")
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.saveDiagramGridLayout(diagramGridLayout);
        });

        assertEquals(StudyException.Type.DIAGRAM_GRID_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout")
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.saveDiagramGridLayout(diagramGridLayout);
        });
    }

    @Test
    void testUpdateDiagramGridLayoutWithError() {
        UUID diagramGridLayoutUuid = UUID.randomUUID();
        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder().diagramLayouts(List.of()).build();

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.updateDiagramGridLayout(diagramGridLayoutUuid, diagramGridLayout);
        });

        assertEquals(StudyException.Type.DIAGRAM_GRID_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.updateDiagramGridLayout(diagramGridLayoutUuid, diagramGridLayout);
        });
    }

    @Test
    void testSaveDiagramGridLayoutWithNADLayoutDetails() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID expectedDiagramGridLayoutUuid = UUID.randomUUID();
        UUID expectedNadConfigUuid = UUID.randomUUID();

        studyRepository.save(StudyEntity.builder().id(studyUuid).build());

        // Create NetworkAreaDiagramLayoutDetails
        NetworkAreaDiagramLayoutDetails nadLayoutDetails = NetworkAreaDiagramLayoutDetails.builder()
            .diagramUuid(UUID.randomUUID())
            .voltageLevelIds(Set.of("VL1", "VL2"))
            .positions(List.of(
                NadVoltageLevelPositionInfos.builder()
                    .voltageLevelId("VL1")
                    .xPosition(100.0)
                    .yPosition(200.0)
                    .build()
            ))
            .diagramPositions(Map.of(
                "lg",
                DiagramPosition.builder()
                    .w(10)
                    .h(8)
                    .x(0)
                    .y(0)
                    .build()
            ))
            .build();

        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder()
            .diagramLayouts(List.of(nadLayoutDetails))
            .build();

        // Mock single-line-diagram-server response for NAD config creation
        wireMockServer.stubFor(WireMock.post(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(expectedNadConfigUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock study-config-server response for diagram grid layout save
        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout")
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(expectedDiagramGridLayoutUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        String payload = objectMapper.writeValueAsString(diagramGridLayout);

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID diagramGridLayoutUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(expectedDiagramGridLayoutUuid, diagramGridLayoutUuidResult);

        // Verify that the single-line-diagram-server was called to create NAD config
        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        // Verify that the study-config-server was called with simplified NAD layout
        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout")));
    }

    @Test
    void testRemoveDiagramGridLayout() throws Exception {
        UUID diagramGridLayoutUuid = UUID.randomUUID();
        UUID nadConfigUuid1 = UUID.randomUUID();
        UUID nadConfigUuid2 = UUID.randomUUID();

        // Create proper diagram grid layout objects with NAD configs
        NetworkAreaDiagramLayout nadLayout1 = NetworkAreaDiagramLayout.builder()
            .currentNadConfigUuid(nadConfigUuid1)
            .build();
        NetworkAreaDiagramLayout nadLayout2 = NetworkAreaDiagramLayout.builder()
            .currentNadConfigUuid(nadConfigUuid2)
            .build();

        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder()
            .diagramLayouts(List.of(nadLayout1, nadLayout2))
            .build();

        String diagramGridLayoutJson = objectMapper.writeValueAsString(diagramGridLayout);

        // Mock study-config-server get response
        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(diagramGridLayoutJson)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock single-line-diagram-server delete response for NAD configs
        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()));

        // Mock study-config-server delete response
        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.ok()));

        // Test the service method directly
        diagramGridLayoutService.removeDiagramGridLayout(diagramGridLayoutUuid);

        // Verify that NAD configs were deleted
        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        // Verify that the diagram grid layout was deleted
        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)));
    }

    @Test
    void testRemoveDiagramGridLayoutWithNullUuid() {
        // Test the service method directly with null UUID
        diagramGridLayoutService.removeDiagramGridLayout(null);

        // Verify no external calls were made
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testRemoveDiagramGridLayoutWithError() {
        UUID diagramGridLayoutUuid = UUID.randomUUID();

        // Mock study-config-server get to throw error
        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)
            .willReturn(WireMock.serverError()));

        // Should not throw exception, just log error
        diagramGridLayoutService.removeDiagramGridLayout(diagramGridLayoutUuid);

        // Verify get was attempted but delete was not called due to error
        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + diagramGridLayoutUuid)));
        wireMockServer.verify(0, WireMock.deleteRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testUpdateDiagramGridLayoutWithNadConfigCleanup() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID existingDiagramGridLayoutUuid = UUID.randomUUID();
        UUID oldNadConfigUuid1 = UUID.randomUUID();
        UUID oldNadConfigUuid2 = UUID.randomUUID();
        UUID newNadConfigUuid = UUID.randomUUID();

        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(existingDiagramGridLayoutUuid).build());

        // Create new NAD layout details for update
        NetworkAreaDiagramLayoutDetails nadLayoutDetails = NetworkAreaDiagramLayoutDetails.builder()
            .diagramUuid(UUID.randomUUID())
            .voltageLevelIds(Set.of("VL1"))
            .positions(List.of(
                NadVoltageLevelPositionInfos.builder()
                    .voltageLevelId("VL1")
                    .xPosition(100.0)
                    .yPosition(200.0)
                    .build()
            ))
            .build();

        DiagramGridLayout updatePayload = DiagramGridLayout.builder()
            .diagramLayouts(List.of(nadLayoutDetails))
            .build();

        // Create existing layout with old NAD configs
        NetworkAreaDiagramLayout oldNadLayout1 = NetworkAreaDiagramLayout.builder()
            .currentNadConfigUuid(oldNadConfigUuid1)
            .build();
        NetworkAreaDiagramLayout oldNadLayout2 = NetworkAreaDiagramLayout.builder()
            .currentNadConfigUuid(oldNadConfigUuid2)
            .build();

        DiagramGridLayout existingLayout = DiagramGridLayout.builder()
            .diagramLayouts(List.of(oldNadLayout1, oldNadLayout2))
            .build();

        String existingLayoutJson = objectMapper.writeValueAsString(existingLayout);

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(existingLayoutJson)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock single-line-diagram-server response for new NAD config creation
        wireMockServer.stubFor(WireMock.post(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(newNadConfigUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock study-config-server update response
        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(existingDiagramGridLayoutUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock single-line-diagram-server delete response for old NAD configs cleanup
        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()));

        String payload = objectMapper.writeValueAsString(updatePayload);

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID result = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);
        assertEquals(existingDiagramGridLayoutUuid, result);

        // Verify get existing layout was called
        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)));

        // Verify new NAD config was created
        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        // Verify layout was updated
        wireMockServer.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)));

        // Verify old NAD configs were cleaned up
        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));
    }

    @Test
    void testCleanupOldNadConfigsWithError() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID existingDiagramGridLayoutUuid = UUID.randomUUID();
        UUID oldNadConfigUuid = UUID.randomUUID();

        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(existingDiagramGridLayoutUuid).build());

        // Create update payload with new NAD layout (this will trigger cleanup)
        NetworkAreaDiagramLayoutDetails newNadLayoutDetails = NetworkAreaDiagramLayoutDetails.builder()
            .diagramUuid(UUID.randomUUID())
            .voltageLevelIds(Set.of("VL1"))
            .positions(List.of(
                NadVoltageLevelPositionInfos.builder()
                    .voltageLevelId("VL1")
                    .xPosition(100.0)
                    .yPosition(200.0)
                    .build()
            ))
            .build();

        DiagramGridLayout updatePayload = DiagramGridLayout.builder()
            .diagramLayouts(List.of(newNadLayoutDetails))
            .build();

        // Create existing layout with old NAD config
        NetworkAreaDiagramLayout oldNadLayout = NetworkAreaDiagramLayout.builder()
            .currentNadConfigUuid(oldNadConfigUuid)
            .build();

        DiagramGridLayout existingLayout = DiagramGridLayout.builder()
            .diagramLayouts(List.of(oldNadLayout))
            .build();

        String existingLayoutJson = objectMapper.writeValueAsString(existingLayout);

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(existingLayoutJson)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock single-line-diagram-server response for new NAD config creation
        UUID newNadConfigUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.post(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(newNadConfigUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock study-config-server update response
        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(existingDiagramGridLayoutUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        // Mock single-line-diagram-server delete to return error
        wireMockServer.stubFor(WireMock.delete(DELIMITER + "v1/network-area-diagram/configs")
            .willReturn(WireMock.serverError()));

        String payload = objectMapper.writeValueAsString(updatePayload);

        // Should not throw exception, just log error
        mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk());

        // Verify that the GET was called to retrieve existing configs
        wireMockServer.verify(1, WireMock.getRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)));

        // Verify new NAD config was created
        wireMockServer.verify(1, WireMock.postRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));

        // Verify update was called
        wireMockServer.verify(1, WireMock.putRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout/" + existingDiagramGridLayoutUuid)));

        // Verify cleanup was attempted but failed gracefully
        wireMockServer.verify(1, WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + "v1/network-area-diagram/configs")));
    }

    @Test
    void testCreateDiagramGridLayoutWithTooManyNads() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).build());

        // Create 4 NAD layout details (exceeds the limit of 3)
        List<NetworkAreaDiagramLayoutDetails> nadLayouts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            nadLayouts.add(NetworkAreaDiagramLayoutDetails.builder()
                .diagramUuid(UUID.randomUUID())
                .voltageLevelIds(Set.of("VL" + i))
                .positions(List.of(
                    NadVoltageLevelPositionInfos.builder()
                        .voltageLevelId("VL" + i)
                        .xPosition(100.0 * i)
                        .yPosition(200.0 * i)
                        .build()
                ))
                .build());
        }

        DiagramGridLayout diagramGridLayout = DiagramGridLayout.builder()
            .diagramLayouts(new ArrayList<>(nadLayouts))
            .build();

        String payload = objectMapper.writeValueAsString(diagramGridLayout);

        // Should return forbidden due to too many NAD configs
        mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());

        // Verify no external services were called since validation failed early
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testUpdateDiagramGridLayoutWithTooManyNads() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID existingDiagramGridLayoutUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(existingDiagramGridLayoutUuid).build());

        // Create more than MAX_NAD_CONFIGS_ALLOWED NAD layout details
        List<NetworkAreaDiagramLayoutDetails> nadLayouts = new ArrayList<>();
        for (int i = 0; i < DiagramGridLayoutService.MAX_NAD_CONFIGS_ALLOWED + 1; i++) {
            nadLayouts.add(NetworkAreaDiagramLayoutDetails.builder()
                .diagramUuid(UUID.randomUUID())
                .voltageLevelIds(Set.of("VL" + i))
                .positions(List.of(
                    NadVoltageLevelPositionInfos.builder()
                        .voltageLevelId("VL" + i)
                        .xPosition(100.0 * i)
                        .yPosition(200.0 * i)
                        .build()
                ))
                .build());
        }

        DiagramGridLayout updatePayload = DiagramGridLayout.builder()
            .diagramLayouts(new ArrayList<>(nadLayouts))
            .build();

        String payload = objectMapper.writeValueAsString(updatePayload);

        // Should return forbidden due to too many NAD configs
        mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());

        // Verify no external services were called since validation failed early
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching(".*")));
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlMatching(".*")));
        wireMockServer.verify(0, WireMock.putRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testCreateDiagramGridLayoutWithTooManyMapCards() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).build());

        // Create more than MAX_MAP_CARDS_ALLOWED Map Cards
        List<AbstractDiagramLayout> mapLayouts = new ArrayList<>();
        for (int i = 0; i < DiagramGridLayoutService.MAX_MAP_CARDS_ALLOWED + 1; i++) {
            mapLayouts.add(MapLayout.builder().diagramUuid(UUID.randomUUID()).build());
        }

        DiagramGridLayout diagramGridLayoutWithTooMuchMapCards = DiagramGridLayout.builder().diagramLayouts(mapLayouts).build();

        String payload = objectMapper.writeValueAsString(diagramGridLayoutWithTooMuchMapCards);

        // Should return forbidden due to too many Map Cards
        mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());

        // Verify no external services were called since validation failed early
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlMatching(".*")));
    }

    @Test
    void testUpdateDiagramGridLayoutWithTooManyMapCards() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID existingDiagramGridLayoutUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).diagramGridLayoutUuid(existingDiagramGridLayoutUuid).build());

        // Create more than MAX_MAP_CARDS_ALLOWED Map Cards
        List<AbstractDiagramLayout> mapLayouts = new ArrayList<>();
        for (int i = 0; i < DiagramGridLayoutService.MAX_MAP_CARDS_ALLOWED + 1; i++) {
            mapLayouts.add(MapLayout.builder().diagramUuid(UUID.randomUUID()).build());
        }

        DiagramGridLayout updatePayload = DiagramGridLayout.builder()
            .diagramLayouts(new ArrayList<>(mapLayouts))
            .build();

        String payload = objectMapper.writeValueAsString(updatePayload);

        // Should return forbidden due to too many Map Cards
        mockMvc.perform(post("/v1/studies/{studyUuid}/diagram-grid-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden());

        // Verify no external services were called since validation failed early
        wireMockServer.verify(0, WireMock.getRequestedFor(WireMock.urlMatching(".*")));
        wireMockServer.verify(0, WireMock.postRequestedFor(WireMock.urlMatching(".*")));
        wireMockServer.verify(0, WireMock.putRequestedFor(WireMock.urlMatching(".*")));
    }

}
