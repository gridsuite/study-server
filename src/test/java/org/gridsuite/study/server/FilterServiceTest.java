/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.powsybl.commons.exceptions.UncheckedInterruptedException;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.FilterService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.wiremock.WireMockStubs;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.gridsuite.study.server.utils.TestUtils.createModificationNodeInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class FilterServiceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FilterServiceTest.class);

    private static final String NETWORK_UUID_STRING = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String CASE_UUID_STRING = "00000000-8cf0-11bd-b23e-10b96e4ef00d";
    private static final String FILTER_UUID_STRING = "c6c15d08-81e9-47a1-9cdb-7be22f017ad5";
    private static final List<String> FILTERS_UUID_STRING = List.of("fc3aa057-5fa4-4173-b1a8-16028f5eefd1", "f8773f32-f77c-4126-8c6f-a4af8bf6f788");
    private static final UUID CASE_UUID = UUID.fromString(CASE_UUID_STRING);
    private static final String NODE_1_NAME = "node1";

    @Autowired
    private MockMvc mockMvc;

    private WireMockServer wireMockServer;

    private WireMockStubs wireMockStubs;

    @MockitoSpyBean
    private FilterService filterService;

    @Autowired
    private NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestUtils studyTestUtils;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockStubs = new WireMockStubs(wireMockServer);

        // start server
        wireMockServer.start();

        // mock base url of filter server as one of wire mock server
        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(filterService).getBaseUri();
    }

    @AfterEach
    void tearDown() {
        studyRepository.findAll().forEach(s -> networkModificationTreeService.doDeleteTree(s.getId()));
        studyRepository.deleteAll();

        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (UncheckedInterruptedException e) {
            LOGGER.error("Error while attempting to get the request done : ", e);
        }
    }

    private StudyEntity insertDummyStudy(UUID networkUuid, UUID caseUuid) {
        StudyEntity studyEntity = TestUtils.createDummyStudy(networkUuid, "netId", caseUuid, "", "", null, null, null, null, null, null);
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

    @Test
    void testEvaluateFilter() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNodeUuid, createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        // whatever string is allowed but given here a json string for more expressive
        final String sendBody = """
                    {
                      "type": "EXPERT",
                      "equipmentType": "GENERATOR",
                      "rules": {
                        "combinator": "AND",
                        "dataType": "COMBINATOR",
                        "rules": [
                          {
                            "field": "ID",
                            "operator": "IN",
                            "values": ["GEN"],
                            "dataType": "STRING"
                          }
                        ]
                      }
                    }
                """;

        // whatever string is allowed but given here a json string for more expressive
        String responseBody = """
                [
                    {"id": "GEN", "type":"GENERATOR"}
                ]
            """;

        UUID stubUuid = wireMockStubs.stubFilterEvaluate(NETWORK_UUID_STRING, responseBody);

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .content(sendBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFilterEvaluate(stubUuid, NETWORK_UUID_STRING);

        // evaluate on first node
        mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate",
                        studyNameUserIdUuid, firstRootNetworkUuid, firstNode.getId())
                        .content(sendBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFilterEvaluate(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testEvaluateGlobalFilter() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        // whatever string is allowed but given here a json string for more expressive
        final String sendBody = """
                    {
                      "nominalV" : ["380", "225"],
                      "countryCode": ["FR", "BE"],
                      "genericFilter": ["c6c15d08-81e9-47a1-9cdb-7be22f017ad5"],
                      "substationProperty": {}
                    }
                """;

        String responseBody = "[\"GEN\"]";

        UUID stubUuid = wireMockStubs.stubGlobalFilterEvaluate(NETWORK_UUID_STRING, List.of(EquipmentType.GENERATOR), responseBody);

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/global-filter/evaluate?equipmentTypes=GENERATOR",
                studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                .content(sendBody).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyGlobalFilterEvaluate(stubUuid, NETWORK_UUID_STRING, List.of(EquipmentType.GENERATOR));
    }

    @Test
    void testEvaluateFilterNotFoundError() throws Exception {
        UUID stubUuid = wireMockStubs.stubFilterEvaluateNotFoundError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        // whatever string is allowed but given here a json string for more expressive
        final String sendBody = """
                    {
                      "type": "EXPERT",
                      "equipmentType": "GENERATOR",
                      "rules": {
                        "combinator": "AND",
                        "dataType": "COMBINATOR",
                        "rules": [
                          {
                            "field": "ID",
                            "operator": "IN",
                            "values": ["GEN"],
                            "dataType": "STRING"
                          }
                        ]
                      }
                    }
                """;

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .content(sendBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andReturn();

        wireMockStubs.verifyFilterEvaluate(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testEvaluateFilterError() throws Exception {
        UUID stubUuid = wireMockStubs.stubFilterEvaluateError(NETWORK_UUID_STRING);

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyNameUserIdUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyNameUserIdUuid);
        UUID rootNodeUuid = getRootNode(studyNameUserIdUuid).getId();

        // whatever string is allowed but given here a json string for more expressive
        final String sendBody = """
                    {
                      "type": "EXPERT",
                      "equipmentType": "GENERATOR",
                      "rules": {
                        "combinator": "AND",
                        "dataType": "COMBINATOR",
                        "rules": [
                          {
                            "field": "ID",
                            "operator": "IN",
                            "values": ["GEN"],
                            "dataType": "STRING"
                          }
                        ]
                      }
                    }
                """;

        mockMvc.perform(post("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/evaluate",
                        studyNameUserIdUuid, firstRootNetworkUuid, rootNodeUuid)
                        .content(sendBody).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andReturn();

        wireMockStubs.verifyFilterEvaluate(stubUuid, NETWORK_UUID_STRING);
    }

    @Test
    void testExportFilter() throws Exception {

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyUuid);
        String responseBody = """
                [
                    {"id":"MANDA7COND.41","type":"SHUNT_COMPENSATOR","distributionKey":null},
                    {"id":"MANDA7COND.31","type":"SHUNT_COMPENSATOR","distributionKey":null}
                ]
            """;
        UUID stubUuid = wireMockStubs.stubFilterExport(NETWORK_UUID_STRING, FILTER_UUID_STRING, responseBody);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/filters/{filterId}/elements",
                        studyUuid, firstRootNetworkUuid, FILTER_UUID_STRING))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFilterExport(stubUuid, FILTER_UUID_STRING, NETWORK_UUID_STRING);
    }

    @Test
    void exportFilterFromFirstRootNetwork() throws Exception {

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID studyFirstRootNetworkUuid = studyTestUtils.getOneRootNetwork(studyUuid).getNetworkUuid();
        String responseBody = """
                [
                    {"id":"MANDA7COND.41","type":"SHUNT_COMPENSATOR","distributionKey":null},
                    {"id":"MANDA7COND.31","type":"SHUNT_COMPENSATOR","distributionKey":null}
                ]
            """;
        UUID stubUuid = wireMockStubs.stubFilterExport(studyFirstRootNetworkUuid.toString(), FILTER_UUID_STRING, responseBody);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/filters/{filterId}/elements",
                        studyUuid, FILTER_UUID_STRING))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        Assertions.assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFilterExport(stubUuid, FILTER_UUID_STRING, studyFirstRootNetworkUuid.toString());
    }

    @Test
    void evaluateFiltersWithEquipmentsOnFirstRootNetworkTest() throws Exception {

        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID studyUuid = studyEntity.getId();
        UUID studyFirstRootNetworkUuid = studyTestUtils.getOneRootNetwork(studyUuid).getNetworkUuid();
        String filtersBody = """
            {
              "filterAttributes": [{
                "id": "c6c15d08-81e9-47a1-9cdb-7be22f017ad5"
              }],
              "equipmentTypesByElement": []
            }
            """;
        String responseBody = """
                [
                    {"id":"MANDA7COND.41","type":"SHUNT_COMPENSATOR","distributionKey":null},
                    {"id":"MANDA7COND.31","type":"SHUNT_COMPENSATOR","distributionKey":null}
                ]
            """;
        UUID stubUuid = wireMockStubs.stubFiltersEvaluate(studyFirstRootNetworkUuid.toString(), filtersBody, responseBody);

        MvcResult mvcResult = mockMvc.perform(post("/v1/studies/{studyUuid}/filters/elements", studyUuid)
                .content(filtersBody).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        String resultAsString = mvcResult.getResponse().getContentAsString();
        Assertions.assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFiltersEvaluate(stubUuid, filtersBody, studyFirstRootNetworkUuid.toString());
    }

    @Test
    void testExportFilters() throws Exception {
        StudyEntity studyEntity = insertDummyStudy(UUID.fromString(NETWORK_UUID_STRING), CASE_UUID);
        UUID firstRootNetworkUuid = studyTestUtils.getOneRootNetworkUuid(studyEntity.getId());
        UUID studyUuid = studyEntity.getId();
        UUID rootNodeUuid = getRootNode(studyUuid).getId();
        NetworkModificationNode firstNode = networkModificationTreeService.createNode(studyEntity, rootNodeUuid, createModificationNodeInfo(NODE_1_NAME), InsertMode.AFTER, null);

        String responseBody = """
            [
               {
                  "filterId":"fc3aa057-5fa4-4173-b1a8-16028f5eefd1",
                  "identifiableAttributes":[
                     {
                        "id":".TMP",
                        "type":"SUBSTATION",
                        "distributionKey":null
                     }
                  ],
                  "notFoundEquipments":null
               },
               {
                  "filterId":"f8773f32-f77c-4126-8c6f-a4af8bf6f788",
                  "identifiableAttributes":[
                     {
                        "id":".TMP2",
                        "type":"SUBSTATION",
                        "distributionKey":null
                     },
                     {
                        "id":".TMP3",
                        "type":"SUBSTATION",
                        "distributionKey":null
                     },
                  ],
                  "notFoundEquipments":null
               }
            ]
                        """;
        UUID stubUuid = wireMockStubs.stubFiltersExport(NETWORK_UUID_STRING, FILTERS_UUID_STRING, responseBody);

        MvcResult mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/elements?filtersUuid=" + FILTERS_UUID_STRING.stream().collect(Collectors.joining(",")),
                studyUuid, firstRootNetworkUuid, rootNodeUuid))
            .andExpect(status().isOk())
            .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFiltersExport(stubUuid, FILTERS_UUID_STRING, NETWORK_UUID_STRING);

        // export on first node
        mvcResult = mockMvc.perform(get("/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/filters/elements?filtersUuid=" + FILTERS_UUID_STRING.stream().collect(Collectors.joining(",")),
                studyUuid, firstRootNetworkUuid, firstNode.getId()))
            .andExpect(status().isOk())
            .andReturn();
        resultAsString = mvcResult.getResponse().getContentAsString();
        assertEquals(responseBody, resultAsString);

        wireMockStubs.verifyFiltersExport(stubUuid, FILTERS_UUID_STRING, NETWORK_UUID_STRING);
    }
}
