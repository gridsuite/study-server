/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.sensitivityanalysis;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SensitivityAnalysisClientTest extends AbstractWireMockRestClientTest {
    private SensitivityAnalysisClient sensitivityAnalysisClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("sensitivity-analysis-server", initMockWebServer());
        sensitivityAnalysisClient = new SensitivityAnalysisClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetProviders() {
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/providers")).willReturn(WireMock.ok().withBody("[\"provider\"]")));

        assertThat(sensitivityAnalysisClient.getProviders()).isEqualTo("[\"provider\"]");
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/providers")));
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String response = "{\"provider\":\"sensitivity\"}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(response)));

        assertThat(sensitivityAnalysisClient.getParameters(parameterUuid)).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String body = "{\"provider\":\"sensitivity\"}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
            .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(WireMock.equalTo(body))
            .willReturn(WireMock.ok()));

        sensitivityAnalysisClient.updateParameters(parameterUuid, body);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlEqualTo(url)));
    }
}
