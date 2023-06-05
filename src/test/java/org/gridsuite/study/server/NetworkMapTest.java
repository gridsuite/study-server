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
import org.gridsuite.study.server.service.ShortCircuitService;
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

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
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

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    private static final String LOAD_ID_1 = "LOAD_ID_1";
    private static final String LINE_ID_1 = "LINE_ID_1";
    private static final String HVDC_LINE_ID_1 = "HVDC_LINE_ID_1";
    private static final String GENERATOR_ID_1 = "GENERATOR_ID_1";
    private static final String SHUNT_COMPENSATOR_ID_1 = "SHUNT_COMPENSATOR_ID_1";
    private static final String TWO_WINDINGS_TRANSFORMER_ID_1 = "2WT_ID_1";
    private static final String SUBSTATION_ID_1 = "SUBSTATION_ID_1";
    private static final String VL_ID_1 = "VL_ID_1";
    private static final String VOLTAGE_LEVEL_ID = "VOLTAGE_LEVEL_ID";
    private static final String VOLTAGE_LEVELS_EQUIPMENTS_JSON = "[{\"voltageLevel\":{\"id\":\"V1\",\"name\":\"VLGEN\",\"substationId\":\"P1\",\"nominalVoltage\":24,\"topologyKind\":\"BUS_BREAKER\"},\"equipments\":[{\"id\":\"GEN\",\"name\":\"GEN\",\"type\":\"GENERATOR\"},{\"id\":\"GEN2\",\"name\":\"GEN2\",\"type\":\"GENERATOR\"},{\"id\":\"LCC1\",\"name\":\"LCC1\",\"type\":\"HVDC_CONVERTER_STATION\"},{\"id\":\"SVC1\",\"name\":\"SVC1\",\"type\":\"STATIC_VAR_COMPENSATOR\"},{\"id\":\"NGEN_NHV1\",\"name\":\"NGEN_NHV1\",\"type\":\"TWO_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT\",\"name\":\"TWT\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT21\",\"name\":\"TWT21\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT32\",\"name\":\"TWT32\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"DL1\",\"name\":\"DL1\",\"type\":\"DANGLING_LINE\"},{\"id\":\"LINE3\",\"name\":\"LINE3\",\"type\":\"LINE\"}]}]";
    private static final String VOLTAGE_LEVEL_EQUIPMENTS_JSON = "[{\"id\":\"GEN\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"GEN2\",\"name\":null,\"type\":\"GENERATOR\"},{\"id\":\"LCC1\",\"name\":\"LCC1\",\"type\":\"HVDC_CONVERTER_STATION\"},{\"id\":\"SVC1\",\"name\":\"SVC1\",\"type\":\"STATIC_VAR_COMPENSATOR\"},{\"id\":\"NGEN_NHV1\",\"name\":null,\"type\":\"TWO_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT\",\"name\":\"TWT\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT21\",\"name\":\"TWT21\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"TWT32\",\"name\":\"TWT32\",\"type\":\"THREE_WINDINGS_TRANSFORMER\"},{\"id\":\"DL1\",\"name\":\"DL1\",\"type\":\"DANGLING_LINE\"},{\"id\":\"LINE3\",\"name\":null,\"type\":\"LINE\"}]";
    private static final String VOLTAGE_LEVEL_TOPOLOGY_JSON = "[{\"id\":\"MTAUBP3\",\"nominalVoltage\":0.0,\"topologyKind\":\"NODE_BREAKER\"}]";
    private static final String EQUIPMENTS_IDS_ENDPOINT = "/equipments-ids";
    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

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

        // Start the server.
        server.start();

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

        String loadDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(LOAD_ID_1).name("LOAD_NAME_1").build());
        String lineDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(LINE_ID_1).name("LINE_NAME_1").build());
        String hvdcLineDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(HVDC_LINE_ID_1).name("HVDC_LINE_NAME_1").build());
        String generatorDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(GENERATOR_ID_1).name("GENERATOR_NAME_1").build());
        String shuntCompensatorDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(SHUNT_COMPENSATOR_ID_1).name("SHUNT_COMPENSATOR_NAME_1").build());
        String twoWindingsTransformerDataAsString = mapper.writeValueAsString(
                IdentifiableInfos.builder().id(TWO_WINDINGS_TRANSFORMER_ID_1).name("2WT_NAME_1").build());
        String twtIdsAsString = List.of("twt1", "twt2", "twt3").toString();
        String voltageLevelDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id(VL_ID_1).name("VL_NAME_1").build()));
        String voltageLevelIdsAsString = List.of("voltageLevel1", "voltageLevel2", "voltageLevel3").toString();
        String substationDataAsString = mapper.writeValueAsString(List.of(
                IdentifiableInfos.builder().id(SUBSTATION_ID_1).name("SUBSTATION_NAME_1").build()));
        String substationIdsAsString = List.of("substation1", "substation2", "substation3").toString();
        String hvdcLineIdsAsString = List.of("hvdc-line1", "hvdc-line2", "hvdc-line3").toString();

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

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/loads/" + LOAD_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(loadDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines/" + LINE_ID_1:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/branch-or-3wt/" + LINE_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(lineDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/hvdc-lines/" + HVDC_LINE_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(hvdcLineDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/generators/" + GENERATOR_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(generatorDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators/" + SHUNT_COMPENSATOR_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(shuntCompensatorDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/2-windings-transformers/" + TWO_WINDINGS_TRANSFORMER_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(twoWindingsTransformerDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + EQUIPMENTS_IDS_ENDPOINT + "?equipmentType=TWO_WINDINGS_TRANSFORMER":
                        return new MockResponse().setResponseCode(200).setBody(twtIdsAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/substations/" + SUBSTATION_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(substationDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + EQUIPMENTS_IDS_ENDPOINT + "?equipmentType=SUBSTATION":
                        return new MockResponse().setResponseCode(200).setBody(substationIdsAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + EQUIPMENTS_IDS_ENDPOINT + "?equipmentType=HVDC_LINE":
                        return new MockResponse().setResponseCode(200).setBody(hvdcLineIdsAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/" + VL_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels/ids":
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelIdsAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels-equipments":
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_LEVELS_EQUIPMENTS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-level-equipments/" + VL_ID_1:
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_LEVEL_EQUIPMENTS_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels-topology":
                        return new MockResponse().setResponseCode(200).setBody(VOLTAGE_LEVEL_TOPOLOGY_JSON)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/map-substations":
                        return new MockResponse().setResponseCode(200).setBody(substationDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/map-lines":
                        return new MockResponse().setResponseCode(200).setBody(lineDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/networks/" + NETWORK_UUID_STRING + "/map-hvdc-lines":
                        return new MockResponse().setResponseCode(200).setBody(hvdcLineDataAsString)
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
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the load map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/{loadId}", studyNameUserIdUuid,
                rootNodeUuid, LOAD_ID_1)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));
        assertTrue(
                TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/loads/%s", NETWORK_UUID_STRING, LOAD_ID_1)));
    }

    @Test
    public void testGetLineMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the line map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/{lineId}", studyNameUserIdUuid,
                        rootNodeUuid, LINE_ID_1))
            .andExpectAll(
                    status().isOk(),
                    content().contentType(MediaType.APPLICATION_JSON));
        assertTrue(
                TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/lines/%s", NETWORK_UUID_STRING, LINE_ID_1)));
    }

    @Test
    public void testGetHvdcLineMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc line map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/{hvdcLineId}", studyNameUserIdUuid,
                        rootNodeUuid, HVDC_LINE_ID_1))
                .andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON));
        assertTrue(
                TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/hvdc-lines/%s", NETWORK_UUID_STRING, HVDC_LINE_ID_1)));
    }

    @Test
    public void testGetGeneratorMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the generator map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/{generatorId}",
                        studyNameUserIdUuid, rootNodeUuid, GENERATOR_ID_1)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/generators/%s", NETWORK_UUID_STRING, GENERATOR_ID_1)));
    }

    @Test
    public void testGetHvdcLinesMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the hvdc lines ids of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/equipments-ids",
                studyNameUserIdUuid, rootNodeUuid).queryParam("equipmentType", "HVDC_LINE")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/equipments-ids?equipmentType=" + "HVDC_LINE", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGet2wtMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the 2wt map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/{2wtId}",
                        studyNameUserIdUuid, rootNodeUuid, TWO_WINDINGS_TRANSFORMER_ID_1)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/2-windings-transformers/%s",
                NETWORK_UUID_STRING, TWO_WINDINGS_TRANSFORMER_ID_1)));

        //get the 2wt ids of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/equipments-ids",
                studyNameUserIdUuid, rootNodeUuid).queryParam("equipmentType", "TWO_WINDINGS_TRANSFORMER")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/equipments-ids?equipmentType=" + "TWO_WINDINGS_TRANSFORMER",
                NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetShuntCompensatorMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the shunt compensator map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/{shuntCompensatorId}",
                        studyNameUserIdUuid, rootNodeUuid, SHUNT_COMPENSATOR_ID_1)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(
                String.format("/v1/networks/%s/shunt-compensators/%s", NETWORK_UUID_STRING, SHUNT_COMPENSATOR_ID_1)));
    }

    @Test
    public void testGetSubstationMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the substation map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/{substationId}",
                studyNameUserIdUuid, rootNodeUuid, SUBSTATION_ID_1)).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/substations/%s", NETWORK_UUID_STRING, SUBSTATION_ID_1)));

        //get the substation ids of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/equipments-ids",
                studyNameUserIdUuid, rootNodeUuid).queryParam("equipmentType", "SUBSTATION")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/equipments-ids?equipmentType=" + "SUBSTATION", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetVoltageLevelsMapServer() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage level map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/{voltageLevelId}",
                        studyNameUserIdUuid, rootNodeUuid, VL_ID_1)).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels/%s", NETWORK_UUID_STRING, VL_ID_1)));

        //get the voltage level ids of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels/ids",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels/ids", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetVoltageLevelsAdnEquipments() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage levels and its equipments
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels-equipments",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels-equipments", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetVoltageLevelsTopology() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the voltage levels and its equipments
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels-topology",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels-topology", NETWORK_UUID_STRING)));
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
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the substations with it's voltage levels
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/map-substations",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/map-substations", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetMapLines() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the lines
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/map-lines",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/map-lines", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetMapHvdcLines() throws Exception {
        //create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        //get the lines
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/map-hvdc-lines",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/map-hvdc-lines", NETWORK_UUID_STRING)));
    }

    @Test
    public void testGetBranchOr3WTMapServer() throws Exception {
        // Create study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        // Get the line / 2WT / 3WT map data info of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/branch-or-3wt/{equipmentId}", studyNameUserIdUuid,
                        rootNodeUuid, LINE_ID_1))
                .andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON));
        assertTrue(
                TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/branch-or-3wt/%s", NETWORK_UUID_STRING, LINE_ID_1)));
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
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity, null);
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

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        cleanDB();

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
