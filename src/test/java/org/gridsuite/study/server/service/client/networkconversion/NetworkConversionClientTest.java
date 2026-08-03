/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkconversion;

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

class NetworkConversionClientTest extends AbstractWireMockRestClientTest {
    private NetworkConversionClient networkConversionClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("network-conversion-server", initMockWebServer());
        networkConversionClient = new NetworkConversionClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetCaseImportParameters() {
        UUID caseUuid = UUID.randomUUID();
        String response = "{\"format\":\"iidm\"}";
        String url = "/v1/cases/" + caseUuid + "/import-parameters";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody(response).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(networkConversionClient.getCaseImportParameters(caseUuid)).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }
}
