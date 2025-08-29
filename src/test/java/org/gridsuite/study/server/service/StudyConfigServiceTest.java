package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.diagramgridlayout.DiagramGridLayout;
import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NetworkAreaDiagramLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.STUDY_CONFIG_API_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudyConfigServiceTest {

    private WireMockServer wireMockServer;
    private StudyConfigService studyConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        RestTemplate restTemplate = new RestTemplate();
        studyConfigService = new StudyConfigService(new RemoteServicesProperties(), restTemplate);
        studyConfigService.setStudyConfigServerBaseUri(wireMockServer.baseUrl() + "/");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void testCreateDiagramGridLayoutFromNadConfig() throws JsonProcessingException {
        UUID diagramConfigId = UUID.randomUUID();
        UUID expectedUuid = UUID.randomUUID();
        DiagramGridLayout requestBody = DiagramGridLayout.builder()
            .diagramLayouts(List.of(NetworkAreaDiagramLayout.builder()
                .originalNadConfigUuid(diagramConfigId)
                .currentNadConfigUuid(diagramConfigId)
                .build()))
            .build();

        wireMockServer.stubFor(post(urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout"))
            .withRequestBody(equalToJson(objectMapper.writeValueAsString(requestBody)))
            .willReturn(okJson(objectMapper.writeValueAsString(expectedUuid))));

        UUID result = studyConfigService.createGridLayoutFromNadDiagram(diagramConfigId);

        assertEquals(expectedUuid, result);
        wireMockServer.verify(postRequestedFor(urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/diagram-grid-layout"))
            .withRequestBody(equalToJson(objectMapper.writeValueAsString(requestBody))));
    }
}
