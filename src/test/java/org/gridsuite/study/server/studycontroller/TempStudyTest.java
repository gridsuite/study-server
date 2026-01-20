package org.gridsuite.study.server.studycontroller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.datasource.ReadOnlyDataSource;
import com.powsybl.commons.datasource.ResourceDataSource;
import com.powsybl.commons.datasource.ResourceSet;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.iidm.serde.XMLImporter;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.iidm.impl.NetworkFactoryImpl;
import com.powsybl.network.store.model.VariantInfos;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.ElementAttributes;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.DirectoryService;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyServerExecutionService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.utils.TestUtils.synchronizeStudyServerExecutionService;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//TODO : merge this file in studyTest once refactoring with wireMockServer is done

@SpringBootTest
@AutoConfigureMockMvc
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class TempStudyTest {

    private WireMockServer wireMockServer;

    @MockitoSpyBean
    private DirectoryService directoryService;

    @MockitoSpyBean
    private NetworkConversionService conversionService;

    @MockitoBean
    private NetworkStoreService networkStoreService;

    @MockitoBean
    private EquipmentInfosService equipmentInfosService;

    @MockitoSpyBean
    private StudyServerExecutionService studyServerExecutionService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00e";
    private static final UUID NETWORK_UUID = UUID.fromString(NETWORK_UUID_STRING);
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String VARIANT_ID = "variant_1";
    private static final String CLONED_NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CLONED_NETWORK_UUID = UUID.fromString(CLONED_NETWORK_UUID_STRING);
    private static final String TEST_FILE = "testCase.xiidm";

    @BeforeEach
    void setup() {
        ReadOnlyDataSource dataSource = new ResourceDataSource("testCase", new ResourceSet("", TEST_FILE));
        Network network = new XMLImporter().importData(dataSource, new NetworkFactoryImpl(), null);
        network.getVariantManager().cloneVariant(VariantManagerConstants.INITIAL_VARIANT_ID, VARIANT_ID);
        network.getVariantManager().setWorkingVariant(VariantManagerConstants.INITIAL_VARIANT_ID);
        initMockBeans(network);

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();

        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(directoryService).getDirectoryServerServerBaseUri();
        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(conversionService).getNetworkConversionServerBaseUri();

    }

    private void initMockBeans(Network network) {
        when(equipmentInfosService.getEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("32"));
        when(equipmentInfosService.getEquipmentInfosCount(NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("16"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount()).then((Answer<Long>) invocation -> Long.parseLong("8"));
        when(equipmentInfosService.getTombstonedEquipmentInfosCount(NETWORK_UUID)).then((Answer<Long>) invocation -> Long.parseLong("4"));

        when(networkStoreService.cloneNetwork(NETWORK_UUID, List.of(VariantManagerConstants.INITIAL_VARIANT_ID))).thenReturn(network);
        when(networkStoreService.getNetworkUuid(network)).thenReturn(NETWORK_UUID);
        when(networkStoreService.getNetwork(NETWORK_UUID)).thenReturn(network);
        when(networkStoreService.getVariantsInfos(NETWORK_UUID))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0),
                new VariantInfos(VARIANT_ID, 1)));
        when(networkStoreService.getVariantsInfos(CLONED_NETWORK_UUID))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0)));

        doNothing().when(networkStoreService).deleteNetwork(NETWORK_UUID);

        // Synchronize for tests
        synchronizeStudyServerExecutionService(studyServerExecutionService);
    }

    private void stubForElementExistInDirectory(UUID directoryUuid, String elementName, String type, int status) {

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath("/v1/directories/{directoryUuid}/elements/{elementName}/types/{type}");
        String path = pathBuilder.buildAndExpand(directoryUuid, elementName, type).toUriString();
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(path))
            .withHeader("content-type", equalTo("application/json"))
            .willReturn(WireMock.aResponse().withStatus(status)));
    }

    private void stubForCreateElementDirectory(UUID elementUuid, String elementName, String type, UUID directoryUuid, String userId, String description) {
        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + DIRECTORY_API_VERSION + "/directories/{directoryUuid}/elements");
        ElementAttributes elementAttributes = new ElementAttributes(elementUuid, elementName, type, userId, 0, description);
        String path = pathBuilder.buildAndExpand(directoryUuid).toUriString();

        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(path))
            .withHeader("userId", equalTo(userId))
            .withHeader("content-type", equalTo("application/json"))
            .withRequestBody(equalTo(String.valueOf(elementAttributes)))
            .willReturn(WireMock.aResponse().withStatus(HttpStatus.OK.value())));
    }

    private void stubForExportNetworkConversionService(UUID networkUuid, String fileName, UUID exportUuid, int status) throws JsonProcessingException {

        var uriComponentsBuilder = UriComponentsBuilder.fromPath("/v1/networks/{networkUuid}/export/{format}");
        // Adding query parameters if present
        String path = uriComponentsBuilder.buildAndExpand(networkUuid, "XIIDM").toUriString();

        // Stubbing POST request instead of HEAD
        wireMockServer.stubFor(WireMock.post(WireMock.urlPathTemplate(path))
            .withQueryParam("fileName", equalTo(fileName))
                    .withHeader("content-type", equalTo("application/json"))
            .willReturn(WireMock.aResponse().withStatus(status).withBody(objectMapper.writeValueAsString(exportUuid))
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

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
        /*String receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NetworkExportReceiver(studyUuid, userId)), StandardCharsets.UTF_8);
        String exportInfosStr = URLEncoder.encode(objectMapper.writeValueAsString(new NodeExportInfos(true, directoryUuid, fileName, description)), StandardCharsets.UTF_8);*/
        stubForExportNetworkConversionService(NETWORK_UUID, fileName, exportUuid, HttpStatus.OK.value());

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/export-network/{format}",
            studyUuid, firstRootNetworkUuid, nodeUuid, "XIIDM")
            .param("fileName", fileName)
            .param("exportToExplorer", Boolean.TRUE.toString())
            .param("parentDirectoryUuid", directoryUuid.toString())
            .param("description", description)
            .header(HEADER_USER_ID, userId)).andExpect(status().isOk());
    }
}
