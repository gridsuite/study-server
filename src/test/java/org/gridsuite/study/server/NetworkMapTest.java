/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.loadflow.LoadFlowParameters;
import lombok.SneakyThrows;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkMapService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
public class NetworkMapTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkMapTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String VARIANT_ID = "variant_1";

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
    private static final String VOLTAGE_LEVELS_EQUIPMENTS_JSON = "[{\"voltageLevel\":{\"id\":\"V1\",\"name\":\"VLGEN\",\"substationId\":\"P1\",\"nominalVoltage\":24,\"topologyKind\":\"BUS_BREAKER\"},\"equipments\":[{\"id\":\"GEN\",\"name\":\"GEN\",\"type\":\"GENERATOR\"},{\"id\":\"GEN2\",\"name\":\"GEN2\",\"type\":\"GENERATOR\"},{\"id\":\"LCC1\",\"name\":\"LCC1\",\"type\":\"HVDC_CONVERTER_STATION\"},{\"id\":\"SVC1\",\"name\":\"SVC1\",\"type\":\"STATIC_VAR_COMPENSATOR\"},{\"id\":\"NGEN_NHV1\",\"name\":\"NGEN_NHV1\",\"type\":\"TWO_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT\",\"name\":\"TWT\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT21\",\"name\":\"TWT21\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT32\",\"name\":\"TWT32\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"DL1\",\"name\":\"DL1\",\"type\":\"DANGLING_LINE\"},{\"id\":\"LINE3\",\"name\":\"LINE3\",\"type\":\"LINE\"}]}]";
    private static final String VOLTAGE_LEVEL_EQUIPMENTS_JSON = "[{\"id\":\"GEN\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"GEN2\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"LCC1\",\"name\":\"LCC1\",\"type\":\"HVDC_CONVERTER_STATION\"},{\"id\":\"SVC1\",\"name\":\"SVC1\",\"type\":\"STATIC_VAR_COMPENSATOR\"},{\"id\":\"NGEN_NHV1\",\"name\":null,\"type\":\"TWO_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT\",\"name\":\"TWT\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT21\",\"name\":\"TWT21\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT32\",\"name\":\"TWT32\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"DL1\",\"name\":\"DL1\",\"type\":\"DANGLING_LINE\"},{\"id\":\"LINE3\",\"name\":null,\"type\":\"LINE\"}]";

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    private WireMockServer wireMockServer;

    private WireMockUtils wireMockUtils;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private NetworkMapService networkMapService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Before
    public void setup() throws IOException {
        server = new MockWebServer();

        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);

        // Start the server.
        server.start();
        wireMockServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        networkMapService.setNetworkMapServerBaseUri(baseUrl);

        String busesDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
                IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build()));

        String busbarSectionsDataAsString = mapper.writeValueAsString(List.of(
            IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
            IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build()));

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                switch (path) {
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/configured-buses":
                        return new MockResponse().setResponseCode(200).setBody(busesDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/busbar-sections":
                        return new MockResponse().setResponseCode(200).setBody(busbarSectionsDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-level-equipments/" + VL_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_LEVEL_EQUIPMENTS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    default:
                        LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                        return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    @SneakyThrows
    @Test
    public void testGetLoadMapServer() {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the load map data info of a network
        String loadDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(LOAD_ID_1).name("LOAD_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "LOAD", "LIST", LOAD_ID_1, loadDataAsString);

        //get data info of an unknown load
        getNetworkElementInfosNotFound(studyNameUserIdUuid, rootNodeUuid, "LOAD", "LIST", "UnknownLoadId");
        getNetworkElementInfosWithError(studyNameUserIdUuid, rootNodeUuid, "LOAD", "LIST", "UnknownLoadId");
    }

    @Test
    public void testGetLineMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the line map data info of a network
        String lineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "LINE", "LIST", LINE_ID_1, lineDataAsString);
    }

    @Test
    public void testGetHvdcLineMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc line map data info of a network
        String hvdcLineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(HVDC_LINE_ID_1).name("HVDC_LINE_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "HVDC_LINE", "LIST", HVDC_LINE_ID_1, hvdcLineDataAsString);
    }

    @Test
    public void testGetGeneratorMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the generator map data info of a network network/elements/{elementId}
        String generatorDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(GENERATOR_ID_1).name("GENERATOR_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "GENERATOR", "FORM", GENERATOR_ID_1, generatorDataAsString);
    }

    @Test
    public void testGetHvdcLinesMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc lines ids of a network
        String hvdcLineIdsAsString = List.of("hvdc-line1", "hvdc-line2", "hvdc-line3").toString();
        getNetworkElementsIds(studyNameUserIdUuid, rootNodeUuid, "HVDC_LINE", List.of(), hvdcLineIdsAsString);
        getNetworkElementsIds(studyNameUserIdUuid, rootNodeUuid, "HVDC_LINE", List.of("S1"), hvdcLineIdsAsString);
    }

    @Test
    public void testGet2wtMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the 2wt map data info of a network
        String twoWindingsTransformerDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(TWO_WINDINGS_TRANSFORMER_ID_1).name("2WT_NAME_1").build());
        getNetworkElementInfosNotFound(studyNameUserIdUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", "Unknown2wtId");
        getNetworkElementInfosWithError(studyNameUserIdUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", "Unknown2wtId");
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", "LIST", TWO_WINDINGS_TRANSFORMER_ID_1, twoWindingsTransformerDataAsString);

        //get the 2wt ids of a network
        String twtIdsAsString = List.of("twt1", "twt2", "twt3").toString();
        getNetworkElementsIds(studyNameUserIdUuid, rootNodeUuid, "TWO_WINDINGS_TRANSFORMER", List.of(), twtIdsAsString);
    }

    @Test
    public void testGetShuntCompensatorMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the shunt compensator map data info of a network
        String shuntCompensatorDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(SHUNT_COMPENSATOR_ID_1).name("SHUNT_COMPENSATOR_NAME_1").build());
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "SHUNT_COMPENSATOR", "MAP", SHUNT_COMPENSATOR_ID_1, shuntCompensatorDataAsString);

    }

    @Test
    public void testGetSubstationMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the substation map data info of a network
        String substationDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "SUBSTATION", "LIST", SUBSTATION_ID_1, substationDataAsString);

        //get the substation ids of a network
        String substationIdsAsString = List.of("substation1", "substation2", "substation3").toString();
        getNetworkElementsIds(studyNameUserIdUuid, rootNodeUuid, "SUBSTATION", List.of(), substationIdsAsString);
    }

    @Test
    public void testGetVoltageLevelsMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage level map data info of a network
        String voltageLevelDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(VL_ID_1).name("VL_NAME_1").build()));
        getNetworkElementInfos(studyNameUserIdUuid, rootNodeUuid, "VOLTAGE_LEVEL", "LIST", VL_ID_1, voltageLevelDataAsString);
    }

    @Test
    public void testGetVoltageLevelsTopology() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage levels and its equipments
        getNetworkElementsInfos(studyNameUserIdUuid, rootNodeUuid, "VOLTAGE_LEVEL", "LIST", List.of(), "[{\"id\":\"MTAUBP3\",\"nominalVoltage\":0.0,\"topologyKind\":\"NODE_BREAKER\"}]");
    }

    @Test
    public void testGetVoltageLevelEquipments() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage levels and its equipments
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-level-equipments/{voltageLevelId}",
                studyNameUserIdUuid, rootNodeUuid, VL_ID_1)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-level-equipments/%s", NETWORK_UUID_STRING, VL_ID_1)));
    }

    @Test
    public void testGetMapSubstations() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the substations with it's voltage levels
        String substationDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));
        getNetworkElementsInfos(studyNameUserIdUuid, rootNodeUuid, "SUBSTATION", "MAP", List.of(), substationDataAsString);
    }

    @Test
    public void testGetMapLines() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the lines
        String lineDataAsString = mapper.writeValueAsString(List.of(IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build()));
        getNetworkElementsInfos(studyNameUserIdUuid, rootNodeUuid, "LINE", "MAP", List.of(), lineDataAsString);
        getNetworkElementsInfos(studyNameUserIdUuid, rootNodeUuid, "LINE", "MAP", List.of("S1"), lineDataAsString);
    }

    @Test
    public void testGetMapHvdcLines() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the lines
        String hvdcLineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(HVDC_LINE_ID_1).name("HVDC_LINE_NAME_1").build());
        getNetworkElementsInfos(studyNameUserIdUuid, rootNodeUuid, "HVDC_LINE", "MAP", List.of(), hvdcLineDataAsString);
    }

    @Test
    public void testGetBranchOr3WTMapServer() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());

        // Create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        // Get the line / 2WT / 3WT map data info of a network
        String lineDataAsString = mapper.writeValueAsString(IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build());
        getNetworkEquipmentInfos(studyNameUserIdUuid, rootNodeUuid, "branch-or-3wt", LINE_ID_1, lineDataAsString);
    }

    @Test
    public void testGetHvdcLineShuntCompensators() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        final String responseBody = "{\"id\":\"HVDC1\",\"hvdcType\":\"LCC\",\"mcsOnside1\":[],\"mcsOnside2\":[]}";
        UUID stubUuid = wireMockUtils.stubHvdcLinesShuntCompensatorsGet(NETWORK_UUID_STRING, HVDC_LINE_ID_1, responseBody);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators",
                        studyNameUserIdUuid, rootNodeUuid, HVDC_LINE_ID_1))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockUtils.verifyHvdcLinesShuntCompensatorsGet(stubUuid, NETWORK_UUID_STRING, HVDC_LINE_ID_1);
    }

    @Test
    public void testGetHvdcLineShuntCompensatorsError() throws Exception {
        networkMapService.setNetworkMapServerBaseUri(wireMockServer.baseUrl());
        UUID stubUuid = wireMockUtils.stubHvdcLinesShuntCompensatorsGetError(NETWORK_UUID_STRING, HVDC_LINE_ID_ERR);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcId}/shunt-compensators",
                        studyNameUserIdUuid, rootNodeUuid, HVDC_LINE_ID_ERR))
                .andExpect(status().is5xxServerError())
                .andReturn();
        wireMockUtils.verifyHvdcLinesShuntCompensatorsGet(stubUuid, NETWORK_UUID_STRING, HVDC_LINE_ID_ERR);
    }

    @Test
    public void testGetBusesOrBusbarSections() throws Exception {
        MvcResult mvcResult;
        String resultAsString;
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/buses",
                        studyNameUserIdUuid, rootNodeUuid, VOLTAGE_LEVEL_ID))
            .andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        List<IdentifiableInfos> iiList = mapper.readValue(resultAsString, new TypeReference<List<IdentifiableInfos>>() { });

        assertThat(iiList, new MatcherJson<>(mapper, List.of(IdentifiableInfos.builder().id("BUS_1").name("BUS_1").build(),
                        IdentifiableInfos.builder().id("BUS_2").name("BUS_2").build())));

        var requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/configured-buses")));

        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/busbar-sections",
                studyNameUserIdUuid, rootNodeUuid, VOLTAGE_LEVEL_ID))
                    .andExpect(status().isOk())
                    .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();

        iiList = mapper.readValue(resultAsString, new TypeReference<List<IdentifiableInfos>>() { });

        assertThat(iiList, new MatcherJson<>(mapper,
                        List.of(IdentifiableInfos.builder().id("BUSBAR_SECTION_1").name("BUSBAR_SECTION_1").build(),
                                IdentifiableInfos.builder().id("BUSBAR_SECTION_2").name("BUSBAR_SECTION_2").build())));

        requests = TestUtils.getRequestsDone(1, server);
        assertTrue(requests.stream().anyMatch(r -> r.matches(
                "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VOLTAGE_LEVEL_ID + "/busbar-sections")));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
            .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
            .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitService.toEntity(ShortCircuitService.getDefaultShortCircuitParameters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null, null);
        var study = studyRepository.save(studyEntity);
        networkModificationTreeService.createRoot(studyEntity, null);
        return study;
    }

    private RootNode getRootNode(UUID study) throws Exception {

        return objectMapper.readValue(mockMvc.perform(get("/v1/studies/{uuid}/tree", study))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString(), new TypeReference<>() { });
    }

    @SneakyThrows
    private MvcResult getNetworkElementsIds(UUID studyUuid, UUID rootNodeUuid, String elementType, List<String> substationsIds, String responseBody) {
        UUID stubUuid = wireMockUtils.stubNetworkElementsIdsGet(NETWORK_UUID_STRING, elementType, responseBody);
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/equipments-ids", studyUuid, rootNodeUuid)
                .queryParam(QUERY_PARAM_EQUIPMENT_TYPE, elementType);
        if (!substationsIds.isEmpty()) {
            mockHttpServletRequestBuilder.queryParam(QUERY_PARAM_SUBSTATIONS_IDS, substationsIds.stream().toArray(String[]::new));
        }
        MvcResult mvcResult = mockMvc.perform(mockHttpServletRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        wireMockUtils.verifyNetworkElementsIdsGet(stubUuid, NETWORK_UUID_STRING, elementType);

        return mvcResult;
    }

    @SneakyThrows
    private MvcResult getNetworkElementsInfos(UUID studyUuid, UUID rootNodeUuid, String elementType, String infoType, List<String> substationsIds, String responseBody) {
        UUID stubUuid = wireMockUtils.stubNetworkElementsInfosGet(NETWORK_UUID_STRING, elementType, infoType, responseBody);
        MockHttpServletRequestBuilder mockHttpServletRequestBuilder = get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/elements", studyUuid, rootNodeUuid)
                .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                .queryParam(QUERY_PARAM_INFO_TYPE, infoType);
        if (!substationsIds.isEmpty()) {
            mockHttpServletRequestBuilder.queryParam(QUERY_PARAM_SUBSTATIONS_IDS, substationsIds.stream().toArray(String[]::new));
        }
        MvcResult mvcResult = mockMvc.perform(mockHttpServletRequestBuilder)
                .andExpect(status().isOk())
                .andReturn();
        wireMockUtils.verifyNetworkElementsInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType);

        return mvcResult;
    }

    @SneakyThrows
    private MvcResult getNetworkElementInfos(UUID studyUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId, String responseBody) {
        UUID stubUuid = wireMockUtils.stubNetworkElementInfosGet(NETWORK_UUID_STRING, elementType, infoType, elementId, responseBody);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                )
                .andExpect(status().isOk())
                .andReturn();
        wireMockUtils.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);

        return mvcResult;
    }

    @SneakyThrows
    private MvcResult getNetworkElementInfosNotFound(UUID studyUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId) {
        UUID stubUuid = wireMockUtils.stubNetworkElementInfosGetNotFound(NETWORK_UUID_STRING, elementType, infoType, elementId);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                )
                .andExpect(status().isNotFound())
                .andReturn();
        wireMockUtils.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);

        return mvcResult;
    }

    @SneakyThrows
    private void getNetworkElementInfosWithError(UUID studyUuid, UUID rootNodeUuid, String elementType, String infoType, String elementId) {
        UUID stubUuid = wireMockUtils.stubNetworkElementInfosGetWithError(NETWORK_UUID_STRING, elementType, infoType, elementId);
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/elements/{elementId}", studyUuid, rootNodeUuid, elementId)
                        .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                        .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                )
                .andExpectAll(status().isInternalServerError(), content().string("Internal Server Error"));
        wireMockUtils.verifyNetworkElementInfosGet(stubUuid, NETWORK_UUID_STRING, elementType, infoType, elementId);
    }

    @SneakyThrows
    private MvcResult getNetworkEquipmentInfos(UUID studyUuid, UUID rootNodeUuid, String infoTypePath, String equipmentId, String responseBody) {
        UUID stubUuid = wireMockUtils.stubNetworkEquipmentInfosGet(NETWORK_UUID_STRING, infoTypePath, equipmentId, responseBody);
        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/{infoTypePath}/{equipmentId}", studyUuid, rootNodeUuid, infoTypePath, equipmentId))
                .andExpect(status().isOk())
                .andReturn();
        wireMockUtils.verifyNetworkEquipmentInfosGet(stubUuid, NETWORK_UUID_STRING, infoTypePath, equipmentId);

        return mvcResult;
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        cleanDB();

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
