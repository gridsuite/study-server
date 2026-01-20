package org.gridsuite.study.server.studycontroller;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.dto.ElementAttributes;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.DirectoryService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//TODO : merge this file in studyTest once refactoring with wireMockServer is done

@AutoConfigureMockMvc
@SpringBootTest
class TempStudyTest {

    private WireMockServer wireMockServer;

    @MockitoSpyBean
    private DirectoryService directoryService;

    @MockitoSpyBean
    NetworkConversionService conversionService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private MockMvc mockMvc;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00e";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(directoryService).getDirectoryServerServerBaseUri();
        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(conversionService).getNetworkConversionServerBaseUri();

    }

    private void stubForElementExistInDirectory(UUID directoryUuid, String elementName, String type, int status) {

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath("/v1/directories/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(path))
            .withHeader("content-type", WireMock.equalTo("application/json"))
            .willReturn(WireMock.aResponse().withStatus(status)));
    }

    private void stubForCreateElementDirectory(UUID elementUuid, String elementName, String type, UUID directoryUuid, String userId, String description) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements");
        ElementAttributes elementAttributes = new ElementAttributes(elementUuid, elementName, type, userId, 0, description);
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(path))
            .withHeader("userId", WireMock.equalTo(userId))
            .withHeader("content-type", WireMock.equalTo("application/json"))
            .withRequestBody(WireMock.equalTo(String.valueOf(elementAttributes)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.OK.value())));
    }

    private void stubForExportNetworkConversionService(String variantId, String fileName, int status, UUID body) {

        var uriComponentsBuilder = UriComponentsBuilder.fromPath("/v1/networks/{networkUuid}/export/{format}");

        // Adding query parameters if present
        if (!StringUtils.isEmpty(variantId)) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        if (!StringUtils.isEmpty(fileName)) {
            uriComponentsBuilder.queryParam("fileName", fileName);
        }

        // Stubbing POST request instead of HEAD
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(uriComponentsBuilder.toUriString()))
            .withRequestBody(WireMock.equalTo(body.toString())) // Adjust body matching as necessary
            .willReturn(WireMock.aResponse().withStatus(status)));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", UUID.randomUUID(), UUID.randomUUID(), null, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private UUID getRootNodeUuid(UUID studyUuid) {
        return networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
    }

    @Test
    void testExportNetworkSuccess() throws Exception {

        String userId = "userId";
        String description = "description";
        String fileName = "myFileName";

        UUID directoryUuid = UUID.randomUUID();
        UUID exportUuid = UUID.randomUUID();

        StudyEntity studyEntity = insertDummyStudy(NETWORK_UUID, CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyEntity.getRootNetworks().getFirst().getId();
        UUID nodeUuid = getRootNodeUuid(studyUuid);

        stubForElementExistInDirectory(directoryUuid, fileName, DirectoryService.CASE, HttpStatus.NO_CONTENT.value());
        stubForCreateElementDirectory(CASE_UUID, fileName, DirectoryService.CASE, directoryUuid, userId, description);
        stubForExportNetworkConversionService(VARIANT_ID, fileName, HttpStatus.OK.value(), exportUuid);

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
            studyUuid, firstRootNetworkUuid, nodeUuid, "XIIDM")
            .param("fileName", fileName)
            .param("exportToExplorer", Boolean.TRUE.toString())
            .param("parentDirectoryUuid", directoryUuid.toString())
            .param("description", description)
            .header(HEADER_USER_ID, userId)).andExpect(status().isOk());
    }
}
