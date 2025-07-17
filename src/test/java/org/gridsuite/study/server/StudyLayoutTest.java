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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

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
class StudyLayoutTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudyConfigService studyConfigService;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
        String baseUrlWireMock = wireMockServer.baseUrl();

        studyConfigService.setStudyConfigServerBaseUri(baseUrlWireMock);
    }

    @Test
    void testCreateStudyLayout() throws Exception {
        String payload = "PAYLOAD";
        UUID expectedStudyLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).build());

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout")
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.ok()
            .withBody(objectMapper.writeValueAsString(expectedStudyLayoutUuid))
            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/study-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID studyLayoutUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertEquals(expectedStudyLayoutUuid, studyLayoutUuidResult);
    }

    @Test
    void testUpdateStudyLayout() throws Exception {
        String payload = "PAYLOAD";
        UUID existingStudyLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).studyLayoutUuid(existingStudyLayoutUuid).build());

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + existingStudyLayoutUuid)
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.ok()
                .withBody(objectMapper.writeValueAsString(existingStudyLayoutUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/study-layout", studyUuid)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andReturn();

        UUID studyLayoutUuidResult = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), UUID.class);

        assertEquals(existingStudyLayoutUuid, studyLayoutUuidResult);
    }

    @Test
    void testGetStudyLayout() throws Exception {
        String studyLayout = "studyLayout";
        UUID studyLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).studyLayoutUuid(studyLayoutUuid).build());

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .willReturn(WireMock.ok()
                .withBody(studyLayout)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            ));

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/study-layout", studyUuid))
            .andExpect(status().isOk())
            .andReturn();

        String studyLayoutResult = mvcResult.getResponse().getContentAsString();

        assertEquals(studyLayout, studyLayoutResult);
    }

    @Test
    void testGetStudyLayoutNoContent() throws Exception {
        UUID studyLayoutUuid = UUID.randomUUID();
        UUID studyUuid = UUID.randomUUID();
        studyRepository.save(StudyEntity.builder().id(studyUuid).studyLayoutUuid(studyLayoutUuid).build());

        wireMockServer.stubFor(WireMock.get(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .willReturn(WireMock.notFound()));

        mockMvc.perform(get("/v1/studies/{studyUuid}/study-layout", studyUuid))
            .andExpect(status().isNoContent());
    }

    @Test
    void testDeleteStudyLayout() {
        UUID studyLayoutUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .willReturn(WireMock.ok()));

        studyConfigService.deleteStudyLayout(studyLayoutUuid);

        RequestPatternBuilder requestBuilder = WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid));
        wireMockServer.verify(1, requestBuilder);
    }

    @Test
    void testDeleteStudyLayoutWithError() {
        UUID studyLayoutUuid = UUID.randomUUID();

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.deleteStudyLayout(studyLayoutUuid);
        });

        assertEquals(StudyException.Type.STUDY_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.delete(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.deleteStudyLayout(studyLayoutUuid);
        });
    }

    @Test
    void testSaveStudyLayoutWithError() {
        String payload = "PAYLOAD";

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout")
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.saveStudyLayout(payload);
        });

        assertEquals(StudyException.Type.STUDY_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.post(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout")
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.saveStudyLayout(payload);
        });
    }

    @Test
    void testUpdateStudyLayoutWithError() {
        UUID studyLayoutUuid = UUID.randomUUID();
        String payload = "PAYLOAD";

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.notFound()));

        StudyException exception = assertThrows(StudyException.class, () -> {
            studyConfigService.updateStudyLayout(studyLayoutUuid, payload);
        });

        assertEquals(StudyException.Type.STUDY_LAYOUT_NOT_FOUND, exception.getType());

        wireMockServer.stubFor(WireMock.put(DELIMITER + STUDY_CONFIG_API_VERSION + "/study-layout/" + studyLayoutUuid)
            .withRequestBody(equalTo(payload))
            .willReturn(WireMock.serverError()));

        assertThrows(Exception.class, () -> {
            studyConfigService.updateStudyLayout(studyLayoutUuid, payload);
        });
    }

}
