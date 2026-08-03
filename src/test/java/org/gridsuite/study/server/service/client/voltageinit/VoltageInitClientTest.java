/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.voltageinit;

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

class VoltageInitClientTest extends AbstractWireMockRestClientTest {
    private VoltageInitClient voltageInitClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("voltage-init-server", initMockWebServer());
        voltageInitClient = new VoltageInitClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testDownloadDebugFile() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        String body = "{\"debug\":true}";
        String url = "/v1/results/" + resultUuid + "/download-debug-file";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(body)));

        var response = voltageInitClient.downloadDebugFile(resultUuid);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(body);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody("{\"reactiveSlacksThreshold\":1.5,\"updateBusVoltage\":true}").withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        var parameters = voltageInitClient.getParameters(parameterUuid);
        assertThat(parameters.getReactiveSlacksThreshold()).isEqualTo(1.5);
        assertThat(parameters.isUpdateBusVoltage()).isTrue();
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }
}
