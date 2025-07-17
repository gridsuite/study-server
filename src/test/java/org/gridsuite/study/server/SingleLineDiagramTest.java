/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.VariantManagerConstants;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.network.store.client.NetworkStoreService;
import com.powsybl.network.store.model.VariantInfos;
import jakarta.servlet.ServletException;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.RootNetworkNodeInfo;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@ExtendWith(MockWebServerExtension.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class SingleLineDiagramTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleLineDiagramTest.class);

    private static final long TIMEOUT = 1000;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String VARIANT_ID = "variant_1";
    private static final String NETWORK_UUID_VARIANT_ERROR_STRING = "88400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String VARIANT_ERROR_ID = "noVariant";
    private static final String BODY_CONTENT = "bodyContent";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString("0c0f1efd-bd22-4a75-83d3-9e530245c7f4");

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ObjectMapper objectMapper;

    private ObjectWriter objectWriter;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private SingleLineDiagramService singleLineDiagramService;

    @Autowired
    private GeoDataService geoDataService;

    @Autowired
    private NetworkMapService networkMapService;

    @Autowired
    private StudyRepository studyRepository;

    //output destinations
    private static final String STUDY_UPDATE_DESTINATION = "study.update";

    @MockBean
    private NetworkStoreService networkStoreService;

    @MockBean
    private LoadFlowService loadFlowService;
    @Autowired
    private RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;
    @Autowired
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Autowired
    private StudyService studyService;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup(final MockWebServer server) {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);

        // Start the server.
        wireMockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrl);
        geoDataService.setGeoDataServerBaseUri(baseUrl);

        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        when(networkStoreService.getVariantsInfos(UUID.fromString(NETWORK_UUID_STRING)))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0),
                new VariantInfos(VARIANT_ID, 1)));

        when(networkStoreService.getVariantsInfos(UUID.fromString(NETWORK_UUID_VARIANT_ERROR_STRING)))
            .thenReturn(List.of(new VariantInfos(VariantManagerConstants.INITIAL_VARIANT_ID, 0)));

        when(loadFlowService.getLoadFlowParameters(LOADFLOW_PARAMETERS_UUID))
            .thenReturn(LoadFlowParametersInfos.builder()
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build());

        when(loadFlowService.getLoadFlowParametersOrDefaultsUuid(any()))
            .thenReturn(LOADFLOW_PARAMETERS_UUID);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                switch (path) {
                    case "/v1/svg/" + NETWORK_UUID_STRING
                        + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "byte");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING
                            + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=STATE_VARIABLE&language=en":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "svgandmetadata");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING
                        + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=STATE_VARIABLE&language=en&variantId=" + VARIANT_ID:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "svgandmetadata");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING
                            + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=FEEDER_POSITION&language=en":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "FEEDER_POSITION");

                    case "/v1/substation-svg/" + NETWORK_UUID_STRING
                            + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "substation-byte");

                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING
                            + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "substation-svgandmetadata");

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en":
                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=STATE_VARIABLE&language=en":
                    case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en":
                        return new MockResponse(404);

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en":
                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en":
                    case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en":
                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en":
                        return new MockResponse(500, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"tmp\",\"path\":\"/v1/networks\"}");
                    case "/v1/network-area-diagram/" + NETWORK_UUID_STRING :
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "nad-svg");

                    case "/v1/svg-component-libraries":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "[\"GridSuiteAndConvergence\",\"Convergence\"]");
                    case "/v1/networks/" + NETWORK_UUID_STRING:
                    case "/v1/lines/infos?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/substations/infos?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/lines/infos?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID:
                    case "/v1/substations/infos?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/all":
                        return new MockResponse(200);
                    default:
                        LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                        return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testDiagramsAndGraphics(final MockWebServer server) throws Exception {
        MvcResult mvcResult;

        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID randomUuid = UUID.randomUUID();

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();

        //get the voltage level diagram svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false&language=en",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_XML),
                        content().string("byte"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en",
                NETWORK_UUID_STRING)));

        //get the voltage level diagram svg without language
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_XML),
                content().string("byte"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en",
                NETWORK_UUID_STRING)));

        //get the voltage level diagram svg on a variant node
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, "voltageLevelId")).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON),
            content().string("svgandmetadata"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
            "/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=STATE_VARIABLE&language=en&variantId=%s",
            NETWORK_UUID_STRING, VARIANT_ID)));

        //get the voltage level diagram svg from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg",
                randomUuid, randomUuid, rootNodeUuid, "voltageLevelId")).andExpect(status().isNotFound());

        //get the voltage level diagram svg and metadata sldDisplayMode = STATE_VARIABLE
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false&language=en",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON),
                        content().string("svgandmetadata"));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=%s&language=en",
                NETWORK_UUID_STRING, StudyConstants.SldDisplayMode.STATE_VARIABLE)));

        //get the voltage level diagram svg and metadata sldDisplayMode = FEEDER_POSITION
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false&sldDisplayMode=" + StudyConstants.SldDisplayMode.FEEDER_POSITION + "&language=en",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string("FEEDER_POSITION"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=%s&language=en",
                NETWORK_UUID_STRING, StudyConstants.SldDisplayMode.FEEDER_POSITION)));

        // get the voltage level diagram svg and metadata from a study that doesn't
        // exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata",
                randomUuid, randomUuid, rootNodeUuid, "voltageLevelId")).andExpect(status().isNotFound());

        // get the substation diagram svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationId")).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_XML),
                                content().string("substation-byte"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/substation-svg/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal",
                NETWORK_UUID_STRING)));

        // get the substation diagram svg from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg",
                randomUuid, randomUuid, rootNodeUuid, "substationId")).andExpect(status().isNotFound());

        // get the substation diagram svg and metadata
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationId")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON),
                content().string("substation-svgandmetadata"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/substation-svg-and-metadata/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en",
                NETWORK_UUID_STRING)));

        // get the substation diagram svg and metadata from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata",
                        randomUuid, randomUuid, rootNodeUuid, "substationId")).andExpect(status().isNotFound());

        // get the network area diagram
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
                content().contentType(MediaType.APPLICATION_JSON),
                status().isOk(),
                content().string("nad-svg")
            );

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/network-area-diagram/" + NETWORK_UUID_STRING)));

        // get the network area diagram from a study that doesn't exist
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram", randomUuid, randomUuid, rootNodeUuid)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON))
                 .andExpect(status().isNotFound());

        //get voltage levels
        mvcResult = getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "VOLTAGE_LEVEL", null, objectMapper.writeValueAsString(List.of()), TestUtils.resourceToString("/network-voltage-levels-infos.json"));
        List<VoltageLevelInfos> vliListResponse = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), new TypeReference<>() { });
        assertThat(vliListResponse, new MatcherJson<>(objectMapper, List.of(
                VoltageLevelInfos.builder().id("BBE1AA1").name("BBE1AA1").substationId("BBE1AA").build(),
                VoltageLevelInfos.builder().id("BBE2AA1").name("BBE2AA1").substationId("BBE2AA").build(),
                VoltageLevelInfos.builder().id("DDE1AA1").name("DDE1AA1").substationId("DDE1AA").build(),
                VoltageLevelInfos.builder().id("DDE2AA1").name("DDE2AA1").substationId("DDE2AA").build(),
                VoltageLevelInfos.builder().id("DDE3AA1").name("DDE3AA1").substationId("DDE3AA").build(),
                VoltageLevelInfos.builder().id("FFR1AA1").name("FFR1AA1").substationId("FFR1AA").build(),
                VoltageLevelInfos.builder().id("FFR3AA1").name("FFR3AA1").substationId("FFR3AA").build(),
                VoltageLevelInfos.builder().id("NNL1AA1").name("NNL1AA1").substationId("NNL1AA").build(),
                VoltageLevelInfos.builder().id("NNL2AA1").name("NNL2AA1").substationId("NNL2AA").build(),
                VoltageLevelInfos.builder().id("NNL3AA1").name("NNL3AA1").substationId("NNL3AA").build())));

        //get the lines-graphics of a network
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/lines", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/lines/infos?networkUuid=%s", NETWORK_UUID_STRING)));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/lines", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)
                .content("[\"LINEID1\", \"LINEID2\"]")
                .contentType(MediaType.APPLICATION_JSON)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/lines/infos?networkUuid=%s&variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));

        //get the substation-graphics of a network
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/substations", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substations/infos?networkUuid=%s", NETWORK_UUID_STRING)));

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/geo-data/substations", studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)
                .content("[\"BBE1AA\", \"BBE2AA\"]")
                .contentType(MediaType.APPLICATION_JSON)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substations/infos?networkUuid=%s&variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));

        //get the lines map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "LINE", null, objectMapper.writeValueAsString(List.of()), "[]");
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "LINE", null, objectMapper.writeValueAsString(List.of("BBE1AA")), "[]");

        //get the substation map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "SUBSTATION", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the 2 windings transformers map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "TWO_WINDINGS_TRANSFORMER", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the 3 windings transformers map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "THREE_WINDINGS_TRANSFORMER", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the generators map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "GENERATOR", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the batteries map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "BATTERY", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the dangling lines map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "DANGLING_LINE", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the hvdc lines map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "HVDC_LINE", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the lcc converter stations map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "LCC_CONVERTER_STATION", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the vsc converter stations map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "VSC_CONVERTER_STATION", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the loads map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "LOAD", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the shunt compensators map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "SHUNT_COMPENSATOR", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the static var compensators map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "STATIC_VAR_COMPENSATOR", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get the voltage levels map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "VOLTAGE_LEVEL", null, objectMapper.writeValueAsString(List.of()), "[]");

        //get all map data of a network
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "all", null, objectMapper.writeValueAsString(List.of()), "[]");

        // get the svg component libraries
        mockMvc.perform(get("/v1/svg-component-libraries")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains("/v1/svg-component-libraries"));

        // Test getting non existing voltage level or substation svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg/%s/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=%s&language=en", NETWORK_UUID_STRING, StudyConstants.SldDisplayMode.STATE_VARIABLE)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg/%s/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg-and-metadata/%s/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en", NETWORK_UUID_STRING)));

        // Test other errors when getting voltage level or substation svg
        assertThrows(ServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg/%s/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&language=en", NETWORK_UUID_STRING)));

        assertThrows(ServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "voltageLevelErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&sldDisplayMode=%s&language=en", NETWORK_UUID_STRING, StudyConstants.SldDisplayMode.STATE_VARIABLE)));

        assertThrows(ServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg/%s/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        assertThrows(ServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "substationErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg-and-metadata/%s/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal&language=en", NETWORK_UUID_STRING)));
    }

    @Test
    void testDiagramsVariantError() throws Exception {
        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_VARIANT_ERROR_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ERROR_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();

        //get the voltage level diagram svg on a non existing variant
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, "voltageLevelId")).andExpectAll(
            status().isNoContent());

        //get the voltage level diagram svg and metadata on a non existing variant
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, "voltageLevelId")).andExpectAll(
            status().isNoContent());

        //get the substation diagram svg on a non existing variant
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, "substationId")).andExpectAll(
            status().isNoContent());

        //get the substation diagram svg and metadata on a non existing variant
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid, "substationId")).andExpectAll(
            status().isNoContent());

        //get the network area diagram on a non existing variant
        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-area-diagram",
            studyNameUserIdUuid, firstRootNetworkUuid, modificationNodeUuid)
                        .content(BODY_CONTENT).contentType(MediaType.APPLICATION_JSON))
                .andExpectAll(
            status().isNoContent());
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        NonEvacuatedEnergyParametersEntity defaultNonEvacuatedEnergyParametersEntity = NonEvacuatedEnergyService.toEntity(NonEvacuatedEnergyService.getDefaultNonEvacuatedEnergyParametersInfos());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null,
                LOADFLOW_PARAMETERS_UUID, null, null, null, null,
                defaultNonEvacuatedEnergyParametersEntity, false);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
            modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .nodeBuildStatus(NodeBuildStatus.from(buildStatus))
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON).header("userId", "userId"))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, STUDY_UPDATE_DESTINATION);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));

        rootNetworkNodeInfoService.updateRootNetworkNode(modificationNode.getId(), studyTestUtils.getOneRootNetworkUuid(studyUuid),
            RootNetworkNodeInfo.builder().variantId(variantId).nodeBuildStatus(NodeBuildStatus.from(buildStatus)).build());

        return modificationNode;
    }

    private MvcResult getNetworkElementsInfos(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String infoType, String elementType, List<Double> nominalVoltages, String requestBody, String responseBody) throws Exception {
        List<String> nominalVoltageStrings = new ArrayList<>();

        if (nominalVoltages != null && !nominalVoltages.isEmpty()) {
            nominalVoltageStrings = nominalVoltages.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }
        String nominalVoltagesParam = nominalVoltageStrings.isEmpty() ? null : objectMapper.writeValueAsString(nominalVoltageStrings);
        UUID stubUuid = wireMockUtils.stubNetworkElementsInfosPost(NETWORK_UUID_STRING, infoType, elementType, nominalVoltages, responseBody);

        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements", studyUuid, rootNetworkUuid, rootNodeUuid)
                .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                .queryParam(QUERY_PARAM_NOMINAL_VOLTAGES, nominalVoltagesParam)
                .queryParam(String.format(QUERY_FORMAT_OPTIONAL_PARAMS, QUERY_PARAM_DC_POWERFACTOR), Double.toString(LoadFlowParameters.DEFAULT_DC_POWER_FACTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);

        MvcResult mvcResult = mockMvc.perform(mockHttpServletRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        wireMockUtils.verifyNetworkElementsInfosPost(stubUuid, NETWORK_UUID_STRING, infoType, elementType, requestBody);

        return mvcResult;
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        List<String> destinations = List.of(STUDY_UPDATE_DESTINATION);
        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
