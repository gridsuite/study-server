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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.VoltageLevelInfos;
import org.gridsuite.study.server.dto.VoltageLevelMapData;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.ShortCircuitParametersEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.utils.MatcherJson;
import org.gridsuite.study.server.utils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
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
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.ContextHierarchy;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.NestedServletException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import com.powsybl.iidm.network.TopologyKind;
import com.powsybl.loadflow.LoadFlowParameters;

import lombok.SneakyThrows;
import nl.jqno.equalsverifier.EqualsVerifier;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@SpringBootTest
@ContextHierarchy({@ContextConfiguration(classes = {StudyApplication.class, TestChannelBinderConfiguration.class})})
public class SingleLineDiagramTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleLineDiagramTest.class);

    private static final long TIMEOUT = 1000;

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String VARIANT_ID = "variant_1";

    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);

    @Value("${loadflow.default-provider}")
    String defaultLoadflowProvider;

    @Autowired
    private MockMvc mockMvc;

    private MockWebServer server;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ObjectMapper mapper;

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

    @Autowired
    private ObjectMapper objectMapper;

    //output destinations
    private String studyUpdateDestination = "study.update";

    @Before
    public void setup() throws IOException {
        objectWriter = objectMapper.writer().withDefaultPrettyPrinter();

        server = new MockWebServer();

        objectWriter = mapper.writer().withDefaultPrettyPrinter();

        // Start the server.
        server.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseHttpUrl = server.url("");
        String baseUrl = baseHttpUrl.toString().substring(0, baseHttpUrl.toString().length() - 1);
        singleLineDiagramService.setSingleLineDiagramServerBaseUri(baseUrl);
        networkMapService.setNetworkMapServerBaseUri(baseUrl);
        geoDataService.setGeoDataServerBaseUri(baseUrl);

        // Values used in dispatcher
        String voltageLevelsMapDataAsString = mapper.writeValueAsString(List.of(
                VoltageLevelMapData.builder().id("BBE1AA1").name("BBE1AA1").substationId("BBE1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("BBE2AA1").name("BBE2AA1").substationId("BBE2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("DDE1AA1").name("DDE1AA1").substationId("DDE1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("DDE2AA1").name("DDE2AA1").substationId("DDE2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("DDE3AA1").name("DDE3AA1").substationId("DDE3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("FFR1AA1").name("FFR1AA1").substationId("FFR1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("FFR3AA1").name("FFR3AA1").substationId("FFR3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("NNL1AA1").name("NNL1AA1").substationId("NNL1AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("NNL2AA1").name("NNL2AA1").substationId("NNL2AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build(),
                VoltageLevelMapData.builder().id("NNL3AA1").name("NNL3AA1").substationId("NNL3AA").nominalVoltage(380).topologyKind(TopologyKind.BUS_BREAKER).build()));

        final Dispatcher dispatcher = new Dispatcher() {
            @SneakyThrows
            @Override
            @NotNull
            public MockResponse dispatch(RecordedRequest request) {
                String path = Objects.requireNonNull(request.getPath());
                request.getBody();

                switch (path) {
                    case "/v1/svg/" + NETWORK_UUID_STRING
                        + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("byte")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING
                            + "/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                        return new MockResponse().setResponseCode(200).setBody("svgandmetadata")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/substation-svg/" + NETWORK_UUID_STRING
                            + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(200).setBody("substation-byte")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING
                            + "/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(200).setBody("substation-svgandmetadata")
                            .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                    case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(404);

                    case "/v1/svg/" + NETWORK_UUID_STRING + "/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                    case "/v1/svg-and-metadata/" + NETWORK_UUID_STRING + "/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false":
                    case "/v1/substation-svg/" + NETWORK_UUID_STRING + "/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                    case "/v1/substation-svg-and-metadata/" + NETWORK_UUID_STRING + "/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal":
                        return new MockResponse().setResponseCode(500)
                            .addHeader("Content-Type", "application/json; charset=utf-8")
                            .setBody("{\"timestamp\":\"2020-12-14T10:27:11.760+0000\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"tmp\",\"path\":\"/v1/networks\"}");
                    case "/v1/network-area-diagram/" + NETWORK_UUID_STRING + "?depth=0&voltageLevelsIds=vlFr1A":
                        return new MockResponse().setResponseCode(200).setBody("nad-svg")
                                .addHeader("Content-Type", "application/json; charset=utf-8");

                    case "/v1/svg-component-libraries":
                        return new MockResponse().setResponseCode(200).setBody("[\"GridSuiteAndConvergence\",\"Convergence\"]")
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/networks/" + NETWORK_UUID_STRING:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/voltage-levels":
                        return new MockResponse().setResponseCode(200).setBody(voltageLevelsMapDataAsString)
                                .addHeader("Content-Type", "application/json; charset=utf-8");
                    case "/v1/lines?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/substations?networkUuid=" + NETWORK_UUID_STRING:
                    case "/v1/lines?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID:
                    case "/v1/substations?networkUuid=" + NETWORK_UUID_STRING + "&variantId=" + VARIANT_ID:
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/substations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/2-windings-transformers":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/3-windings-transformers":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/generators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/batteries":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/dangling-lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/hvdc-lines":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/lcc-converter-stations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/vsc-converter-stations":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/loads":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/loads-modification":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/shunt-compensators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/static-var-compensators":
                    case "/v1/networks/" + NETWORK_UUID_STRING + "/all":
                        return new MockResponse().setBody(" ").setResponseCode(200)
                            .addHeader("Content-Type", "application/json; charset=utf-8");
                    default:
                        LOGGER.error("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                        return new MockResponse().setResponseCode(418).setBody("Unhandled method+path: " + request.getMethod() + " " + request.getPath());
                }
            }
        };

        server.setDispatcher(dispatcher);
    }

    @Test
    public void testDiagramsAndGraphics() throws Exception {
        MvcResult mvcResult;
        String resultAsString;

        //insert a study
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        UUID randomUuid = UUID.randomUUID();

        NetworkModificationNode modificationNode1 = createNetworkModificationNode(studyNameUserIdUuid, rootNodeUuid, UUID.randomUUID(), VARIANT_ID, "node 1");
        UUID modificationNodeUuid = modificationNode1.getId();

        //get the voltage level diagram svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false",
                studyNameUserIdUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_XML),
                        content().string("byte"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false",
                NETWORK_UUID_STRING)));

        //get the voltage level diagram svg from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg",
                randomUuid, rootNodeUuid, "voltageLevelId")).andExpect(status().isNotFound());

        //get the voltage level diagram svg and metadata
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false",
                studyNameUserIdUuid, rootNodeUuid, "voltageLevelId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON),
                        content().string("svgandmetadata"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/svg-and-metadata/%s/voltageLevelId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false",
                NETWORK_UUID_STRING)));

        // get the voltage level diagram svg and metadata from a study that doesn't
        // exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata",
                randomUuid, rootNodeUuid, "voltageLevelId")).andExpect(status().isNotFound());

        // get the substation diagram svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false",
                        studyNameUserIdUuid, rootNodeUuid, "substationId")).andExpectAll(
                                status().isOk(),
                                content().contentType(MediaType.APPLICATION_XML),
                                content().string("substation-byte"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/substation-svg/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal",
                NETWORK_UUID_STRING)));

        // get the substation diagram svg from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg",
                randomUuid, rootNodeUuid, "substationId")).andExpect(status().isNotFound());

        // get the substation diagram svg and metadata
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false",
                studyNameUserIdUuid, rootNodeUuid, "substationId")).andExpectAll(
                        status().isOk(),
                        content().contentType(MediaType.APPLICATION_JSON),
                        content().string("substation-svgandmetadata"));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format(
                "/v1/substation-svg-and-metadata/%s/substationId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal",
                NETWORK_UUID_STRING)));

        // get the substation diagram svg and metadata from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata",
                        randomUuid, rootNodeUuid, "substationId")).andExpect(status().isNotFound());

        // get the network area diagram
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram?&depth=0&voltageLevelsIds=vlFr1A", studyNameUserIdUuid, rootNodeUuid))
            .andExpectAll(
                content().contentType(MediaType.APPLICATION_JSON),
                status().isOk(),
                content().string("nad-svg")
            );

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/network-area-diagram/" + NETWORK_UUID_STRING + "?depth=0&voltageLevelsIds=vlFr1A")));

        // get the network area diagram from a study that doesn't exist
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-area-diagram?&depth=0&voltageLevelsIds=vlFr1A", randomUuid, rootNodeUuid))
            .andExpect(status().isNotFound());

        //get voltage levels
        EqualsVerifier.simple().forClass(VoltageLevelMapData.class).verify();
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels", studyNameUserIdUuid,
                        rootNodeUuid)).andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        List<VoltageLevelInfos> vliListResponse = mapper.readValue(resultAsString, new TypeReference<List<VoltageLevelInfos>>() {
        });

        assertThat(vliListResponse, new MatcherJson<>(mapper, List.of(
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

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/voltage-levels", NETWORK_UUID_STRING)));

        //get the lines-graphics of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/lines/", studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/lines?networkUuid=%s", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/lines/", studyNameUserIdUuid, modificationNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/lines?networkUuid=%s&variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));

        //get the substation-graphics of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/substations/", studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substations?networkUuid=%s", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/geo-data/substations/", studyNameUserIdUuid, modificationNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substations?networkUuid=%s&variantId=%s", NETWORK_UUID_STRING, VARIANT_ID)));

        //get the lines map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lines/", studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/lines", NETWORK_UUID_STRING)));

        //get the substation map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/substations/", studyNameUserIdUuid,
                        rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/substations", NETWORK_UUID_STRING)));

        //get the 2 windings transformers map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/2-windings-transformers/",
                        studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/2-windings-transformers", NETWORK_UUID_STRING)));

        //get the 3 windings transformers map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/3-windings-transformers/",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/3-windings-transformers", NETWORK_UUID_STRING)));

        //get the generators map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/generators/", studyNameUserIdUuid,
                rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/generators", NETWORK_UUID_STRING)));

        //get the batteries map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/batteries/", studyNameUserIdUuid,
                rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/batteries", NETWORK_UUID_STRING)));

        //get the dangling lines map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/dangling-lines/", studyNameUserIdUuid,
                rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/dangling-lines", NETWORK_UUID_STRING)));

        //get the hvdc lines map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/hvdc-lines/", studyNameUserIdUuid,
                rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/hvdc-lines", NETWORK_UUID_STRING)));

        //get the lcc converter stations map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/lcc-converter-stations/",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/lcc-converter-stations", NETWORK_UUID_STRING)));

        //get the vsc converter stations map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/vsc-converter-stations/",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/vsc-converter-stations", NETWORK_UUID_STRING)));

        //get the loads map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/loads/", studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
            status().isOk(),
            content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/loads", NETWORK_UUID_STRING)));

        //get the shunt compensators map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/shunt-compensators/", studyNameUserIdUuid,
                rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(
                TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/shunt-compensators", NETWORK_UUID_STRING)));

        //get the static var compensators map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/static-var-compensators/",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/static-var-compensators", NETWORK_UUID_STRING)));

        //get the voltage levels map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/voltage-levels",
                studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server)
                .contains(String.format("/v1/networks/%s/voltage-levels", NETWORK_UUID_STRING)));

        //get all map data of a network
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network-map/all/", studyNameUserIdUuid, rootNodeUuid)).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/networks/%s/all", NETWORK_UUID_STRING)));

        // get the svg component libraries
        mockMvc.perform(get("/v1/svg-component-libraries")).andExpectAll(
                status().isOk(),
                content().contentType(MediaType.APPLICATION_JSON));

        assertTrue(TestUtils.getRequestsDone(1, server).contains("/v1/svg-component-libraries"));

        // Test getting non existing voltage level or substation svg
        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg/%s/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg/%s/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationNotFoundId")).andExpectAll(status().isNotFound());
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg-and-metadata/%s/substationNotFoundId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        // Test other errors when getting voltage level or substation svg
        assertThrows(NestedServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg/%s/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        assertThrows(NestedServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/voltage-levels/{voltageLevelId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "voltageLevelErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/svg-and-metadata/%s/voltageLevelErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false", NETWORK_UUID_STRING)));

        assertThrows(NestedServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg/%s/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));

        assertThrows(NestedServletException.class, () -> mockMvc.perform(get("/v1/studies/{studyUuid}/nodes/{nodeUuid}/network/substations/{substationId}/svg-and-metadata?useName=false", studyNameUserIdUuid, rootNodeUuid, "substationErrorId")));
        assertTrue(TestUtils.getRequestsDone(1, server).contains(String.format("/v1/substation-svg-and-metadata/%s/substationErrorId?useName=false&centerLabel=false&diagonalLabel=false&topologicalColoring=false&substationLayout=horizontal", NETWORK_UUID_STRING)));
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        LoadFlowParametersEntity defaultLoadflowParametersEntity = LoadFlowParametersEntity.builder()
            .voltageInitMode(LoadFlowParameters.VoltageInitMode.UNIFORM_VALUES)
            .balanceType(LoadFlowParameters.BalanceType.PROPORTIONAL_TO_GENERATION_P_MAX)
            .connectedComponentMode(LoadFlowParameters.ConnectedComponentMode.MAIN)
            .build();
        ShortCircuitParametersEntity defaultShortCircuitParametersEntity = ShortCircuitAnalysisService.toEntity(ShortCircuitAnalysisService.getDefaultShortCircuitParamters());
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, caseUuid, "", defaultLoadflowProvider, defaultLoadflowParametersEntity, defaultShortCircuitParametersEntity);
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

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName) throws Exception {
        return createNetworkModificationNode(studyUuid, parentNodeUuid,
            modificationGroupUuid, variantId, nodeName, BuildStatus.NOT_BUILT);
    }

    private NetworkModificationNode createNetworkModificationNode(UUID studyUuid, UUID parentNodeUuid,
            UUID modificationGroupUuid, String variantId, String nodeName, BuildStatus buildStatus) throws Exception {
        NetworkModificationNode modificationNode = NetworkModificationNode.builder().name(nodeName)
                .description("description").modificationGroupUuid(modificationGroupUuid).variantId(variantId)
                .loadFlowStatus(LoadFlowStatus.NOT_DONE).buildStatus(buildStatus)
                .children(Collections.emptyList()).build();

        // Only for tests
        String mnBodyJson = objectWriter.writeValueAsString(modificationNode);
        JSONObject jsonObject = new JSONObject(mnBodyJson);
        jsonObject.put("variantId", variantId);
        jsonObject.put("modificationGroupUuid", modificationGroupUuid);
        mnBodyJson = jsonObject.toString();

        mockMvc.perform(post("/v1/studies/{studyUuid}/tree/nodes/{id}", studyUuid, parentNodeUuid).content(mnBodyJson).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
        var mess = output.receive(TIMEOUT, studyUpdateDestination);
        assertNotNull(mess);
        modificationNode.setId(UUID.fromString(String.valueOf(mess.getHeaders().get(NotificationService.HEADER_NEW_NODE))));
        assertEquals(InsertMode.CHILD.name(), mess.getHeaders().get(NotificationService.HEADER_INSERT_MODE));
        return modificationNode;
    }

    private void cleanDB() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();
    }

    @After
    public void tearDown() {
        List<String> destinations = List.of(studyUpdateDestination);

        cleanDB();

        TestUtils.assertQueuesEmptyThenClear(destinations, output);

        try {
            TestUtils.assertServerRequestsEmptyThenShutdown(server);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
