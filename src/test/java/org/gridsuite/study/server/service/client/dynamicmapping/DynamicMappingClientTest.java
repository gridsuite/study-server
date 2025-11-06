/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicmapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelInfos;
import org.gridsuite.study.server.dto.dynamicmapping.ModelVariableDefinitionInfos;
import org.gridsuite.study.server.dto.dynamicmapping.VariablesSetInfos;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.service.client.dynamicmapping.impl.DynamicMappingClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

import static org.gridsuite.study.server.service.client.dynamicmapping.DynamicMappingClient.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicMappingClientTest extends AbstractWireMockRestClientTest {
    public static final String MAPPINGS_BASE_URL = buildEndPointUrl("", API_VERSION, DYNAMIC_MAPPING_END_POINT_MAPPING);

    private static final String[] MAPPING_NAMES = {"mapping01", "mapping02"};
    private static final List<MappingInfos> MAPPINGS = Arrays.asList(new MappingInfos(MAPPING_NAMES[0]),
                                                        new MappingInfos(MAPPING_NAMES[1]));

    private static final List<ModelInfos> MODELS = List.of(
            // take from resources/data/loadAlphaBeta.json
            new ModelInfos("LoadAlphaBeta", "LOAD", List.of(
                    new ModelVariableDefinitionInfos("load_PPu", "MW"),
                    new ModelVariableDefinitionInfos("load_QPu", "MW")
            ), null),
            // take from resources/data/generatorSynchronousThreeWindingsProportionalRegulations.json
            new ModelInfos("GeneratorSynchronousThreeWindingsProportionalRegulations", "GENERATOR", null, List.of(
                    new VariablesSetInfos("Generator", List.of(
                            new ModelVariableDefinitionInfos("generator_omegaPu", "pu"),
                            new ModelVariableDefinitionInfos("generator_PGen", "MW")
                    )),
                    new VariablesSetInfos("VoltageRegulator", List.of(
                            new ModelVariableDefinitionInfos("voltageRegulator_EfdPu", "pu")
                    ))
            ))
    );

    private DynamicMappingClient dynamicMappingClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    public void setup() {
        // config client
        remoteServicesProperties.setServiceUri("dynamic-mapping-server", initMockWebServer());
        dynamicMappingClient = new DynamicMappingClientImpl(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetAllMappings() throws Exception {

        // configure mock server response for test case getAllMappings - mappings/
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL + DELIMITER))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(MAPPINGS))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));

        // call method to be tested
        List<MappingInfos> allMappings = dynamicMappingClient.getAllMappings();

        // --- check result --- //
        // must contain two elements
        getLogger().info("Size of result mappings = {}", allMappings.size());
        assertEquals(2, allMappings.size());
        getLogger().info("Result mappings = {}", objectMapper.writeValueAsString(allMappings));
        // first element's name must be mappingNames[0]
        assertEquals(MAPPING_NAMES[0], allMappings.get(0).getName());
        // first element's name must be mappingNames[1]
        assertEquals(MAPPING_NAMES[1], allMappings.get(1).getName());
    }

    @Test
    void testGetAllMappingsGivenNotFound() {
        // configure mock server response for test case not found getAllMappings - mappings
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL))
                .willReturn(WireMock.notFound()
                ));
        // call method to be tested
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMappingClient.getAllMappings()
        );
    }

    @Test
    void testGetAllMappingsGivenBadRequest() {
        // configure mock server response for test case bad request getAllMappings - mappings/
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL + DELIMITER))
                .willReturn(WireMock.badRequest()
                ));
        // call method to be tested
        assertThrows(HttpStatusCodeException.class, () -> dynamicMappingClient.getAllMappings());
    }

    @Test
    void testGetModels() throws Exception {
        // configure mock server response for test case getModels - mappings/{mappingName}/models
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL + DELIMITER + MAPPING_NAMES[0] + DELIMITER + "models"))
                .willReturn(WireMock.ok()
                        .withBody(objectMapper.writeValueAsString(MODELS))
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));

        // call method to be tested
        List<ModelInfos> resultModelInfosList = dynamicMappingClient.getModels(MAPPING_NAMES[0]);

        // --- check result --- //
        // must contain two models
        assertEquals(2, resultModelInfosList.size());
        // two json must equivalent
        String expectedModelsJson = objectMapper.writeValueAsString(MODELS);
        String resultModelsJson = objectMapper.writeValueAsString(resultModelInfosList);
        getLogger().info("Expect models in json = {}", expectedModelsJson);
        getLogger().info("Result models in json = {}", resultModelsJson);
        assertEquals(objectMapper.readTree(expectedModelsJson), objectMapper.readTree(resultModelsJson));

    }

    @Test
    void testGetModelsGiveBlankMapping() {
        // call method to be tested
        List<ModelInfos> modelInfosList = dynamicMappingClient.getModels("");

        // --- check result --- //
        // must be null
        assertNull(modelInfosList);
    }

    @Test
    void testGetModelsGivenNotFound() {
        // configure mock server response for test case getModels - mappings/{mappingName}/models
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL + DELIMITER + MAPPING_NAMES[0] + DELIMITER + "models" + ".*"))
                .willReturn(WireMock.notFound()
                ));
        // call method to be tested
        assertThrows(
            HttpClientErrorException.NotFound.class,
            () -> dynamicMappingClient.getModels(MAPPING_NAMES[0])
        );
    }

    @Test
    void testGetModelsGivenBadRequest() {
        // configure mock server response for test case getModels - mappings/{mappingName}/models
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(MAPPINGS_BASE_URL + DELIMITER + MAPPING_NAMES[0] + DELIMITER + "models"))
                .willReturn(WireMock.badRequest()
                ));
        // call method to be tested
        assertThrows(HttpStatusCodeException.class, () -> dynamicMappingClient.getModels(MAPPING_NAMES[0]));
    }
}
