/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.loadflow;

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

class LoadFlowClientTest extends AbstractWireMockRestClientTest {
    private LoadFlowClient loadFlowClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("loadflow-server", initMockWebServer());
        loadFlowClient = new LoadFlowClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetProvidersSpecificParametersAndDefaultLimitReductions() {
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/providers")).willReturn(WireMock.ok().withBody("[\"OpenLoadFlow\"]")));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/specific-parameters")).willReturn(WireMock.ok().withBody("{\"p\":true}")));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/parameters/default-limit-reductions")).willReturn(WireMock.ok().withBody("[]")));

        assertThat(loadFlowClient.getProviders()).isEqualTo("[\"OpenLoadFlow\"]");
        assertThat(loadFlowClient.getSpecificParameters()).isEqualTo("{\"p\":true}");
        assertThat(loadFlowClient.getDefaultLimitReductions()).isEqualTo("[]");
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody("{\"provider\":\"OpenLoadFlow\"}").withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(loadFlowClient.getParameters(parameterUuid).getProvider()).isEqualTo("OpenLoadFlow");
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String url = "/v1/parameters/" + parameterUuid;
        String body = "{\"provider\":\"OpenLoadFlow\"}";
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
            .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(WireMock.equalTo(body))
            .willReturn(WireMock.ok()));

        loadFlowClient.updateParameters(parameterUuid, body);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlEqualTo(url)));
    }
}
