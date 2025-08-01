package org.gridsuite.study.server;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.VariantInfos;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.SingleLineDiagramService;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NetworkAreaDiagramTest {

    @Autowired
    protected MockMvc mockMvc;

    private WireMockServer wireMockServer;

    protected WireMockUtils wireMockUtils;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @MockBean
    private NetworkModificationTreeService networkModificationTreeService;

    @MockBean
    private RootNetworkService rootNetworkService;

    @MockBean
    private NetworkStoreService networkStoreService;

    private static final String SINGLE_LINE_DIAGRAM_SERVER_BASE_URL = "/v1/network-area-diagram/";
    private static final String USER1 = "user1";
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final UUID ROOTNETWORK_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final String BODY_CONTENT = "bodyContent";

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);
        wireMockServer.start();
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(wireMockServer.baseUrl());

        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOTNETWORK_UUID)).thenReturn(VariantManagerConstants.INITIAL_VARIANT_ID);
        when(rootNetworkService.getNetworkUuid(ROOTNETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkStoreService.getVariantsInfos(NETWORK_UUID)).thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0)));
    }

    @Test
    void testGetNetworkAreaDiagramFromConfig() throws Exception {
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(SINGLE_LINE_DIAGRAM_SERVER_BASE_URL + ".*"))
                .withRequestBody(equalTo(BODY_CONTENT))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("nad-svg-from-config")
                )).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram",
                        NETWORK_UUID, ROOTNETWORK_UUID, NODE_UUID)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .header("userId", USER1))
                .andExpectAll(status().isOk(), content().string("nad-svg-from-config"))
                .andReturn();
        wireMockUtils.verifyPostRequest(
                stubId,
                SINGLE_LINE_DIAGRAM_SERVER_BASE_URL + NETWORK_UUID,
                Map.of("variantId", WireMock.equalTo(VariantManagerConstants.INITIAL_VARIANT_ID)));
    }

    @Test
    void testGetNetworkAreaDiagramFromConfigVariantError() throws Exception {
        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOTNETWORK_UUID)).thenReturn("Another_variant");
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram",
                        NETWORK_UUID, ROOTNETWORK_UUID, NODE_UUID)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .header("userId", USER1))
                .andExpectAll(status().isNoContent());
    }

    @Test
    void testGetNetworkAreaDiagramFromConfigRootNetworkError() throws Exception {
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram",
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .header("userId", USER1))
                .andExpectAll(status().isNotFound());
    }

    @Test
    void testGetNetworkAreaDiagramFromConfigElementUuidNotFound() throws Exception {
        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathMatching(SINGLE_LINE_DIAGRAM_SERVER_BASE_URL + ".*"))
                .withRequestBody(equalTo(BODY_CONTENT))
                .willReturn(WireMock.notFound())).getId();

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram",
                        NETWORK_UUID, ROOTNETWORK_UUID, NODE_UUID)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON)
                        .header("userId", USER1))
                .andExpectAll(status().isNotFound());

        wireMockUtils.verifyPostRequest(stubId, SINGLE_LINE_DIAGRAM_SERVER_BASE_URL + NETWORK_UUID, Map.of(
                 "variantId", WireMock.equalTo(VariantManagerConstants.INITIAL_VARIANT_ID)));
    }
}
