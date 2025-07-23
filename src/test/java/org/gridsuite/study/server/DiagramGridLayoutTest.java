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
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.DiagramPosition;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NadVoltageLevelPositionInfos;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayoutDetails;
import org.gridsuite.study.server.service.SingleLineDiagramService;
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

}
