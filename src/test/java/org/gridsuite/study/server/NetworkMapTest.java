/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import lombok.SneakyThrows;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import mockwebserver3.junit5.internal.MockWebServerExtension;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.LoadFlowService;
import org.gridsuite.study.server.service.NetworkMapService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.ReportService;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.wiremock.WireMockUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.InfoTypeParameters.QUERY_PARAM_DC_POWERFACTOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class NetworkMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMapTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    private static final String LOAD_ID_1 = "LOAD_ID_1";
    private static final String LINE_ID_1 = "LINE_ID_1";
    private static final String HVDC_LINE_ID_1 = "HVDC_LINE_ID_1";
    private static final String HVDC_LINE_ID_ERR = "HVDC_LINE_ID_ERR";
    private static final String GENERATOR_ID_1 = "GENERATOR_ID_1";
    private static final String SHUNT_COMPENSATOR_ID_1 = "SHUNT_COMPENSATOR_ID_1";
    private static final String TWO_WINDINGS_TRANSFORMER_ID_1 = "2WT_ID_1";
    private static final String SUBSTATION_ID_1 = "SUBSTATION_ID_1";
    private static final String VL_ID_1 = "VL_ID_1";
    private static final String VOLTAGE_LEVEL_ID = "VOLTAGE_LEVEL_ID";
    private static final String VOLTAGE_LEVEL_EQUIPMENTS_JSON = "[{\"id\":\"GEN\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"GEN2\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"LCC1\",\"name\":\"LCC1\",\"type\":\"HVDC_CONVERTER_STATION\"},{\"id\":\"SVC1\",\"name\":\"SVC1\",\"type\":\"STATIC_VAR_COMPENSATOR\"},{\"id\":\"NGEN_NHV1\",\"name\":null,\"type\":\"TWO_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT\",\"name\":\"TWT\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT21\",\"name\":\"TWT21\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT32\",\"name\":\"TWT32\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"DL1\",\"name\":\"DL1\",\"type\":\"DANGLING_LINE\"},{\"id\":\"LINE3\",\"name\":null,\"type\":\"LINE\"}]";
    private static final String LOADFLOW_PARAMETERS_UUID_STRING = "0c0f1efd-bd22-4a75-83d3-9e530245c7f4";
    private static final UUID LOADFLOW_PARAMETERS_UUID = UUID.fromString(LOADFLOW_PARAMETERS_UUID_STRING);
    private static final String SWITCHES_INFOS_JSON = "[{\"id\":\".ABRE 6_.ABRE6TR615 SA.1_OC\",\"open\":false\"},{\"id\":\".ABRE 6_.ABRE6SEC..12 SS.1.12_OC\",\"open\":false\"}]";
    private static final String BUSBAR_SECTIONS_INFO_JSON = "{\"topologyKind\":\"NODE_BREAKER\",\"switchKinds\":[\"DISCONNECTOR\"],\"isSymmetrical\":true,\"isBusbarSectionPositionFound\":true,\"busBarSections\":{\"1\":[\"NGEN4\"]}}";
    private static final String FEEDER_BAYS_BUSBAR_SECTIONS_INFO_JSON = "{\"feederBaysInfos\":{\"SHUNT_VLNB\":[{\"busbarSectionId\":\"NGEN4\",\"connectablePositionInfos\":{\"connectionDirection\":null},\"connectionSide\":null}],\"LINE7\":[{\"busbarSectionId\":\"NGEN4\",\"connectablePositionInfos\":{\"connectionDirection\":\"BOTTOM\",\"connectionPosition\":5,\"connectionName\":\"LINE7_Side_VLGEN4\"},\"connectionSide\":\"ONE\"}],\"SHUNT_NON_LINEAR\":[{\"busbarSectionId\":\"NGEN4\",\"connectablePositionInfos\":{\"connectionDirection\":null},\"connectionSide\":null}]},\"busBarSectionsInfos\":{\"topologyKind\":\"NODE_BREAKER\",\"switchKinds\":[\"DISCONNECTOR\"],\"isSymmetrical\":true,\"isBusbarSectionPositionFound\":true,\"busBarSections\":{\"1\":[\"NGEN4\"]}}}";

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

    private WireMockStubs wireMockStubs;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private NetworkMapService networkMapService;

    @Autowired
    private LoadFlowService loadFlowService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup(final MockWebServer server) throws Exception {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockStubs = new WireMockStubs(wireMockServer);

        // Start the server.
        wireMockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkMapService.setNetworkMapServerBaseUri(baseUrl);
        loadFlowService.setLoadFlowServerBaseUri(baseUrl);
        reportService.setReportServerBaseUri(baseUrl);

        String busesDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
                IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build()));

        String busbarSectionsDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
                IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build()));

        LoadFlowParametersInfos loadFlowParametersInfos = LoadFlowParametersInfos.builder()
                .commonParameters(LoadFlowParameters.load())
                .specificParametersPerProvider(Map.of())
                .build();
        String loadFlowParameters = objectMapper.writeValueAsString(loadFlowParametersInfos);

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                if (path.matches("/v1/reports/.*") && "PUT".equals(request.getMethod())) {
                    return new MockResponse(200);
                }
                if (path.matches("/v1/networks/" + NETWORK_UUID_STRING + "/all?variantId=first_variant_id&infoType=TAB") && "POST".equals(request.getMethod())) {
                    return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "");
                }
                switch (path) {
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/all?variantId=first_variant_id&infoType=TAB":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), "");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VL_ID_1 + "/buses-or-busbar-sections?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), busesDataAsString);
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/buses-or-busbar-sections?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), busbarSectionsDataAsString);
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/switches?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), SWITCHES_INFOS_JSON);
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/bus-bar-sections?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), BUSBAR_SECTIONS_INFO_JSON);
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/feeder-bays?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), FEEDER_BAYS_BUSBAR_SECTIONS_INFO_JSON);
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/substation-id?variantId=first_variant_id":
                        return new MockResponse.Builder().code(200).body(SUBSTATION_ID_1).build();
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VL_ID_1 + "/equipments?variantId=first_variant_id":
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), VOLTAGE_LEVEL_EQUIPMENTS_JSON);
                    case "/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING:
                        return new MockResponse(200, Headers.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE), loadFlowParameters);
                    default:
                        LOGGER.error("Unhandled method+path: {} {}", request.getMethod(), request.getPath());
                        return new MockResponse.Builder().code(418).body("Unhandled method+path: " + request.getMethod() + " " + request.getPath()).build();
                }
            }
        };
        server.setDispatcher(dispatcher);
    }

    @Test
    void testGetLoadMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the load map data info of a network
        String loadDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(LOAD_ID_1).name("LOAD_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "LOAD", "LIST", LOAD_ID_1, loadDataAsString);

        //get data info of an unknown load
        getNetworkElementInfosNotFound(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "LOAD", "LIST", "UnknownLoadId");
        getNetworkElementInfosWithError(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "LOAD", "LIST", "UnknownLoadId");
        assertTrue(TestUtils.getRequestsDone(3, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetLineMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the line map data info of a network
        String lineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "LINE", "LIST", LINE_ID_1, lineDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetHvdcLineMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc line map data info of a network
        String hvdcLineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(HVDC_LINE_ID_1).name("HVDC_LINE_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "HVDC_LINE", "LIST", HVDC_LINE_ID_1, hvdcLineDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetGeneratorMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the generator map data info of a network network/elements/{elementId}
        String generatorDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(GENERATOR_ID_1).name("GENERATOR_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "GENERATOR", "FORM", GENERATOR_ID_1, generatorDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetHvdcLinesMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc lines ids of a network
        String hvdcLineIdsAsString = List.of("hvdc-line1", "hvdc-line2", "hvdc-line3").toString();
        getNetworkElementsIds(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "HVDC_LINE", List.of(), hvdcLineIdsAsString, List.of().toString());
        getNetworkElementsIds(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "HVDC_LINE", List.of(24.0), hvdcLineIdsAsString, List.of().toString());
    }

    @Test
    void testGet2wtMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the 2wt map data info of a network
        String twoWindingsTransformerDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(TWO_WINDINGS_TRANSFORMER_ID_1).name("2WT_NAME_1").build());
        getNetworkElementInfosNotFound(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", "Unknown2wtId");
        getNetworkElementInfosWithError(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", "Unknown2wtId");
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", TWO_WINDINGS_TRANSFORMER_ID_1, twoWindingsTransformerDataAsString);

        //get the 2wt ids of a network
        String twtIdsAsString = List.of("twt1", "twt2", "twt3").toString();
        getNetworkElementsIds(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", List.of(), twtIdsAsString, List.of().toString());
        assertTrue(TestUtils.getRequestsDone(3, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetShuntCompensatorMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the shunt compensator map data info of a network
        String shuntCompensatorDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(SHUNT_COMPENSATOR_ID_1).name("SHUNT_COMPENSATOR_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "SHUNT_COMPENSATOR", "MAP", SHUNT_COMPENSATOR_ID_1, shuntCompensatorDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetSubstationMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the substation map data info of a network
        String substationDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));
        getNetworkElementInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "SUBSTATION", "LIST", SUBSTATION_ID_1, substationDataAsString);

        //get the substation ids of a network
        String substationIdsAsString = List.of("substation1", "substation2", "substation3").toString();
        getNetworkElementsIds(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "SUBSTATION", List.of(), substationIdsAsString, "[]");
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetVoltageLevelsMapServer(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the voltage level map data info of a network
        String voltageLevelDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(VL_ID_1).name("VL_NAME_1").build()));
        getNetworkElementInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "VOLTAGE_LEVEL", "LIST", VL_ID_1, voltageLevelDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetVoltageLevelsTopology(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the voltage levels and its equipments
        getNetworkElementsInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "LIST", "VOLTAGE_LEVEL", List.of(24.0), mapper.writeValueAsString(List.of()), "[{\"id\":\"MTAUBP3\",\"nominalVoltage\":24.0,\"topologyKind\":\"NODE_BREAKER\"}]");
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetVoltageLevelEquipments(final MockWebServer server) throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the voltage levels and its equipments
        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}/equipments",
                studyEntity.getId(), firstRootNetworkUuid, node.getId(), VL_ID_1)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels/%s/equipments?variantId=first_variant_id", NETWORK_UUID_STRING, VL_ID_1)));
    }

    @Test
    void testGetMapSubstations(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the substations with it's voltage levels
        String substationDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "SUBSTATION", null, mapper.writeValueAsString(List.of()), substationDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
        getNetworkElementsInfos(studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, "MAP", "SUBSTATION", List.of(24.0), mapper.writeValueAsString(List.of()), substationDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetMapLines(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the lines
        String lineDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build()));
        getNetworkElementsInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "MAP", "LINE", null, mapper.writeValueAsString(List.of()), lineDataAsString);
        getNetworkElementsInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "MAP", "LINE", null, mapper.writeValueAsString(List.of("S1")), lineDataAsString);
        assertTrue(TestUtils.getRequestsDone(2, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetMapHvdcLines(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        //get the lines
        String hvdcLineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(HVDC_LINE_ID_1).name("HVDC_LINE_NAME_1").build());
        getNetworkElementsInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "MAP", "HVDC_LINE", null, mapper.writeValueAsString(List.of()), hvdcLineDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));

        getNetworkElementsInfos(studyEntity.getId(), firstRootNetworkUuid, node.getId(), "MAP", "HVDC_LINE", List.of(24.0), mapper.writeValueAsString(List.of()), hvdcLineDataAsString);
        assertTrue(TestUtils.getRequestsDone(1, server).stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    @Test
    void testGetHvdcLineShuntCompensators(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        final String responseBody = "{\"id\":\"HVDC1\",\"hvdcType\":\"LCC\",\"mcsOnside1\":[],\"mcsOnside2\":[]}";
        UUID stubUuid = wireMockStubs.stubHvdcLinesShuntCompensatorsGet(NETWORK_UUID_STRING, HVDC_LINE_ID_1, responseBody);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), HVDC_LINE_ID_1))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyHvdcLinesShuntCompensatorsGet(stubUuid, NETWORK_UUID_STRING, HVDC_LINE_ID_1);
    }

    @Test
    void testGetBranchOr3WTVoltageLevelId(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubBranchOr3WTVoltageLevelIdGet(NETWORK_UUID_STRING, LINE_ID_1, VL_ID_1);
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        MvcResult mvcResult = mockMvc
            .perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/branch-or-3wt/{equipmentId}/voltage-level-id", studyEntity.getId(), firstRootNetworkUuid, node.getId(), LINE_ID_1)
                .queryParam(QUERY_PARAM_SIDE, TwoSides.ONE.name()))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(VL_ID_1, resultAsString);
        wireMockStubs.verifyBranchOr3WTVoltageLevelIdGet(stubUuid, NETWORK_UUID_STRING, LINE_ID_1);
    }

    @Test
    void testGetHvdcLineShuntCompensatorsError(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubHvdcLinesShuntCompensatorsGetError(NETWORK_UUID_STRING, HVDC_LINE_ID_ERR);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid, HVDC_LINE_ID_ERR))
                .andExpect(status().is5xxServerError())
                .andReturn();
        wireMockStubs.verifyHvdcLinesShuntCompensatorsGet(stubUuid, NETWORK_UUID_STRING, HVDC_LINE_ID_ERR);
    }

    @Test
    void testGetSubstationIdForVoltageLevel(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/substation-id",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VOLTAGE_LEVEL_ID))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(SUBSTATION_ID_1, resultAsString);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/substation-id\\?variantId=first_variant_id")));

    }

    @Test
    void testGetBusesOrBusbarSections(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses-or-busbar-sections",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VOLTAGE_LEVEL_ID))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        List<IdentifiableInfos> iiList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(iiList, new MatcherJson<>(mapper,
                List.of(IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
                        IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build())));

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/buses-or-busbar-sections\\?variantId=first_variant_id")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses-or-busbar-sections",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VL_ID_1))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        iiList = mapper.readValue(resultAsString, new TypeReference<>() { });

        assertThat(iiList, new MatcherJson<>(mapper, List.of(IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
                IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build())));

        requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VL_ID_1 + "/buses-or-busbar-sections\\?variantId=first_variant_id")));
    }

    @Test
    void testGetVoltageLevelSwitches(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/switches",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VOLTAGE_LEVEL_ID))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(SWITCHES_INFOS_JSON, resultAsString);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/switches\\?variantId=first_variant_id")));

    }

    @Test
    void testGetBusBarSectionsInfo(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/bus-bar-sections",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VOLTAGE_LEVEL_ID))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(BUSBAR_SECTIONS_INFO_JSON, resultAsString);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/bus-bar-sections\\?variantId=first_variant_id")));

    }

    @Test
    void testGetFeederBaysBusBarSectionsInfo(final MockWebServer server) throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/feeder-bays",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId(), VOLTAGE_LEVEL_ID))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(FEEDER_BAYS_BUSBAR_SECTIONS_INFO_JSON, resultAsString);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/feeder-bays\\?variantId=first_variant_id")));

    }

    @Test
    void testGetAllNetworkElementsInfos(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        studyEntity.getSpreadsheetParameters().setSpreadsheetLoadBranchOperationalLimitGroup(true);
        studyEntity.getSpreadsheetParameters().setSpreadsheetLoadGeneratorRegulatingTerminal(true);
        studyRepository.save(studyEntity);

        UUID rootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        String expectedJson = """
{
  "BRANCH": {
    "loadOperationalLimitGroups": "true",
    "dcPowerFactor": "1.0"
  },
  "GENERATOR": {
    "loadRegulatingTerminals": "true"
  },
  "BUS" : {
    "loadNetworkComponents" : "false"
  },
  "TIE_LINE": {
    "dcPowerFactor": "1.0"
  },
  "LINE": {
    "loadOperationalLimitGroups": "false",
    "dcPowerFactor": "1.0"
  },
  "TWO_WINDINGS_TRANSFORMER": {
    "loadOperationalLimitGroups": "false",
    "dcPowerFactor": "1.0"
  }
}
            """;

        UUID stubId = wireMockServer.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + NETWORK_UUID_STRING + "/all"))
                .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.equalTo("first_variant_id"))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo("TAB"))
                .willReturn(WireMock.ok().withBody(""))).getId();

        mockMvc.perform(
            get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/all",
                studyEntity.getId(), rootNetworkUuid, node.getId())
        ).andExpect(status().isOk());

        WireMockUtils.verifyPostRequest(
            wireMockServer,
            stubId,
            "/v1/networks/" + NETWORK_UUID_STRING + "/all",
            true,
            Map.of(QUERY_PARAM_VARIANT_ID, WireMock.equalTo("first_variant_id"), QUERY_PARAM_INFO_TYPE, WireMock.equalTo("TAB")),
            expectedJson);

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/parameters/" + LOADFLOW_PARAMETERS_UUID_STRING)));
    }

    private StudyEntity insertDummyStudy(final MockWebServer server, UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, LOADFLOW_PARAMETERS_UUID, null, null, null, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createBasicTree(studyEntity);
        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches("/v1/reports/.*")));
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {
        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(), new TypeReference<>() { });
    }

    private MvcResult getNetworkElementsIds(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String elementType, List<Double> nominalVoltages, String responseBody, String requestBody) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkElementsIdsPost(NETWORK_UUID_STRING, responseBody);
        LinkedMultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add(QUERY_PARAM_EQUIPMENT_TYPE, elementType);
        if (nominalVoltages != null && !nominalVoltages.isEmpty()) {
            List<String> nominalVoltageStrings = nominalVoltages.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            queryParams.addAll(QUERY_PARAM_NOMINAL_VOLTAGES, nominalVoltageStrings);
        }
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/equipments-ids", studyUuid, rootNetworkUuid, rootNodeUuid)
                .queryParams(queryParams)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);
        MvcResult mvcResult = mockMvc.perform(mockHttpServletRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.verifyNetworkElementsIdsPost(stubUuid, NETWORK_UUID_STRING, requestBody);
        return mvcResult;
    }

    private MvcResult getNetworkElementsInfos(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String infoType, String elementType, List<Double> nominalVoltages, String requestBody, String responseBody) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkElementsInfosPost(NETWORK_UUID_STRING, infoType, elementType, nominalVoltages, responseBody);

        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements", studyUuid, rootNetworkUuid, rootNodeUuid)
                .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                .queryParam(String.format(QUERY_FORMAT_OPTIONAL_PARAMS, QUERY_PARAM_DC_POWERFACTOR), Double.toString(LoadFlowParameters.DEFAULT_DC_POWER_FACTOR));
        if (nominalVoltages != null) {
            nominalVoltages.forEach(voltage -> mockHttpServletRequestBuilder.queryParam("nominalVoltages", voltage.toString()));
        }

        mockHttpServletRequestBuilder .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody);

        MvcResult mvcResult = mockMvc.perform(mockHttpServletRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.verifyNetworkElementsInfosPost(stubUuid, NETWORK_UUID_STRING, infoType, elementType, requestBody);
        return mvcResult;
    }

    private MvcResult getNetworkElementInfos(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId, String responseBody) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkElementInfosGet(NETWORK_UUID_STRING, elementType, infoType, elementId, responseBody);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNetworkUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                )
                .andExpect(status().isOk())
                .andReturn();
        wireMockStubs.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);
        return mvcResult;
    }

    private MvcResult getNetworkElementInfosNotFound(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkElementInfosGetNotFound(NETWORK_UUID_STRING, elementType, infoType, elementId);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNetworkUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                        .queryParam(String.format(QUERY_FORMAT_OPTIONAL_PARAMS, QUERY_PARAM_DC_POWERFACTOR), Double.toString(LoadFlowParameters.DEFAULT_DC_POWER_FACTOR))
                )
                .andExpect(status().isNotFound())
                .andReturn();
        wireMockStubs.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);
        return mvcResult;
    }

    private void getNetworkElementInfosWithError(UUID studyUuid, UUID rootNetworkUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId) throws Exception {
        UUID stubUuid = wireMockStubs.stubNetworkElementInfosGetWithError(NETWORK_UUID_STRING, elementType, infoType, elementId);
        var result = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNetworkUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                        .queryParam(String.format(QUERY_FORMAT_OPTIONAL_PARAMS, QUERY_PARAM_DC_POWERFACTOR), Double.toString(LoadFlowParameters.DEFAULT_DC_POWER_FACTOR))
                )
            .andExpect(status().isInternalServerError())
            .andReturn();
        var problemDetail = objectMapper.readValue(result.getResponse().getContentAsString(), PowsyblWsProblemDetail.class);
        assertEquals("Internal Server Error", problemDetail.getTitle());
        wireMockStubs.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);
    }

    @Test
    void testGetCountries(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        final String responseBody = """
                ['FR', 'GB']
            """;
        UUID stubUuid = wireMockStubs.stubCountriesGet(NETWORK_UUID_STRING, responseBody);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/countries",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId()))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyCountriesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testGetCountriesNotFoundError(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubCountriesGetNotFoundError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/countries",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid))
                .andExpect(status().isNotFound())
                .andReturn();

        wireMockStubs.verifyCountriesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testGetCountriesError(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubCountriesGetError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/countries",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid))
                .andExpect(status().is5xxServerError())
                .andReturn();

        wireMockStubs.verifyCountriesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testGetNominalVoltages(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        final String responseBody = """
                [24.0, 380.0]
            """;
        UUID stubUuid = wireMockStubs.stubNominalVoltagesGet(NETWORK_UUID_STRING, responseBody);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        AbstractNode node = getRootNode(studyEntity.getId()).getChildren().stream().findFirst().orElseThrow();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/nominal-voltages",
                        studyEntity.getId(), firstRootNetworkUuid, node.getId()))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyNominalVoltagesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testGetNominalVoltagesNotFoundError(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubNominalVoltagesGetNotFoundError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/nominal-voltages",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid))
                .andExpect(status().isNotFound())
                .andReturn();

        wireMockStubs.verifyNominalVoltagesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testGetNominalVoltagesError(final MockWebServer server) throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockStubs.stubNominalVoltagesGetError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(server, UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/network-map/nominal-voltages",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid))
                .andExpect(status().is5xxServerError())
                .andReturn();

        wireMockStubs.verifyNominalVoltagesGet(stubUuid, NETWORK_UUID_STRING);
    }

    @AfterEach
    void tearDown(final MockWebServer server) {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        output.clear();

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }
}
