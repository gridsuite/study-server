/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.securityanalysis;

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

class SecurityAnalysisClientTest extends AbstractWireMockRestClientTest {
    private SecurityAnalysisClient securityAnalysisClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("security-analysis-server", initMockWebServer());
        securityAnalysisClient = new SecurityAnalysisClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetProvidersAndDefaultLimitReductions() {
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/providers")).willReturn(WireMock.ok().withBody("[\"provider\"]")));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/parameters/default-limit-reductions")).willReturn(WireMock.ok().withBody("[]")));

        assertThat(securityAnalysisClient.getProviders()).isEqualTo("[\"provider\"]");
        assertThat(securityAnalysisClient.getDefaultLimitReductions()).isEqualTo("[]");
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/providers")));
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/parameters/default-limit-reductions")));
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String response = "{\"provider\":\"security\"}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(response)));

        assertThat(securityAnalysisClient.getParameters(parameterUuid)).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String body = "{\"provider\":\"security\"}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
            .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(WireMock.equalTo(body))
            .willReturn(WireMock.ok()));

        securityAnalysisClient.updateParameters(parameterUuid, body);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlEqualTo(url)));
    }
}
