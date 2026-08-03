/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkmap;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkMapClientTest extends AbstractWireMockRestClientTest {
    private NetworkMapClient networkMapClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("network-map-server", initMockWebServer());
        networkMapClient = new NetworkMapClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetElementSchema() {
        String response = "{\"type\":\"object\"}";
        String url = "/v1/schemas/LINE/FORM";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody(response).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(networkMapClient.getElementSchema("LINE", "FORM")).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }
}
