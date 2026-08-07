/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.service.DynamicMappingService.API_VERSION;
import static org.gridsuite.study.server.service.DynamicMappingService.DYNAMIC_MAPPING_END_POINT_NETWORK;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
class DynamicMappingServiceTest extends AbstractWireMockRestClientTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamicMappingServiceTest.class);

    private static final String DELIMITER = "/";
    private static final UUID NETWORK_UUID = UUID.randomUUID();

    private static final String NETWORK_VALUES_JSON = "{\"propertyValues\":[]}";
    private static final String RULE_TO_MATCH_JSON = "{\"filter\":null,\"ruleIndex\":0}";
    private static final String NETWORK_MATCHES_JSON = "[\"GEN1\",\"GEN2\"]";

    private DynamicMappingService dynamicMappingService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setUp() {
        // config client
        remoteServicesProperties.setServiceUri("dynamic-mapping-server", initMockWebServer());
        dynamicMappingService = new DynamicMappingService(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetNetworkValues() {
        String networkBaseUrl = buildEndPointUrl("", API_VERSION, DYNAMIC_MAPPING_END_POINT_NETWORK);
        // --- setup mock server --- //
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(networkBaseUrl + DELIMITER + NETWORK_UUID + "/values"))
                .willReturn(WireMock.ok()
                        .withBody(NETWORK_VALUES_JSON)));

        // --- call service method to be tested --- //
        LOGGER.info("Calling getNetworkValues for networkUuid={}", NETWORK_UUID);
        String result = dynamicMappingService.getNetworkValues(NETWORK_UUID);

        // --- check result --- //
        LOGGER.info("Network values result = {}", result);
        assertThat(result).isEqualTo(NETWORK_VALUES_JSON);

    }

    @Test
    void testGetNetworkMatches() {
        String networkBaseUrl = buildEndPointUrl("", API_VERSION, DYNAMIC_MAPPING_END_POINT_NETWORK);
        // --- setup mock server --- //
        wireMockServer.stubFor(WireMock.post(WireMock.urlEqualTo(networkBaseUrl + DELIMITER + NETWORK_UUID + "/matches/rule"))
                        .withRequestBody(WireMock.equalToJson(RULE_TO_MATCH_JSON))
                        .willReturn(WireMock.ok()
                        .withBody(NETWORK_MATCHES_JSON)));

        // --- call service method to be tested --- //
        LOGGER.info("Calling getNetworkMatches for networkUuid={}, rule={}", NETWORK_UUID, RULE_TO_MATCH_JSON);
        String result = dynamicMappingService.getNetworkMatches(NETWORK_UUID, RULE_TO_MATCH_JSON);

        // --- check result --- //
        LOGGER.info("Network matches result = {}", result);
        assertThat(result).isEqualTo(NETWORK_MATCHES_JSON);

    }
}
