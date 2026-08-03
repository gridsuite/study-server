/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.shortcircuit;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCircuitClientTest extends AbstractWireMockRestClientTest {
    private ShortCircuitClient shortCircuitClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("shortcircuit-server", initMockWebServer());
        shortCircuitClient = new ShortCircuitClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testDownloadDebugFile() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        String body = "{\"debug\":true}";
        String url = "/v1/results/" + resultUuid + "/download-debug-file";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(body)));

        var response = shortCircuitClient.downloadDebugFile(resultUuid);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testGetSpecificParametersAndParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String response = "{\"withLimitViolations\":true}";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/parameters/specific-parameters")).willReturn(WireMock.ok().withBody(response)));
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/parameters/" + parameterUuid)).willReturn(WireMock.ok().withBody(response)));

        assertThat(shortCircuitClient.getSpecificParameters()).isEqualTo(response);
        assertThat(shortCircuitClient.getParameters(parameterUuid)).isEqualTo(response);
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String body = "{\"withLimitViolations\":true}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
            .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(WireMock.equalTo(body))
            .willReturn(WireMock.ok()));

        shortCircuitClient.updateParameters(parameterUuid, body);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlEqualTo(url)));
    }
}
