/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.pccmin;

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

class PccMinClientTest extends AbstractWireMockRestClientTest {
    private PccMinClient pccMinClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("pcc-min-server", initMockWebServer());
        pccMinClient = new PccMinClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String response = "{\"provider\":\"pcc-min\"}";
        String url = "/v1/parameters/" + parameterUuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody(response).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(pccMinClient.getParameters(parameterUuid)).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }
}
