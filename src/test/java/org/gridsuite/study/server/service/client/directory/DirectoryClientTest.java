/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.directory;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryClientTest extends AbstractWireMockRestClientTest {
    private DirectoryClient directoryClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("directory-server", initMockWebServer());
        directoryClient = new DirectoryClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetElements() {
        UUID elementUuid = UUID.randomUUID();
        String response = "[{\"id\":\"" + elementUuid + "\"}]";
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/elements"))
            .withQueryParam("ids", WireMock.equalTo(elementUuid.toString()))
            .withQueryParam("elementTypes", WireMock.equalTo("STUDY"))
            .withQueryParam("strictMode", WireMock.equalTo("false"))
            .withHeader("userId", WireMock.equalTo("userId"))
            .willReturn(WireMock.ok().withBody(response).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(directoryClient.getElements(List.of(elementUuid), List.of("STUDY"), false, "userId")).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/elements")));
    }

    @Test
    void testElementExists() {
        UUID directoryUuid = UUID.randomUUID();
        String url = "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        wireMockServer.stubFor(WireMock.head(WireMock.urlEqualTo(url)).willReturn(WireMock.ok()));

        assertThat(directoryClient.elementExists(directoryUuid, "elementName", "STUDY")).isTrue();
        wireMockServer.verify(WireMock.headRequestedFor(WireMock.urlEqualTo(url)));
    }
}
