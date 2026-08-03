/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkmodification;

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

class NetworkModificationClientTest extends AbstractWireMockRestClientTest {
    private static final String RESPONSE = "{\"name\":\"modification\"}";
    private NetworkModificationClient networkModificationClient;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @BeforeEach
    void setup() {
        remoteServicesProperties.setServiceUri("network-modification-server", initMockWebServer());
        networkModificationClient = new NetworkModificationClient(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetLineTypesCatalog() {
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/network-modifications/catalog/line_types"))
            .willReturn(WireMock.ok().withBody(RESPONSE).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(networkModificationClient.getLineTypesCatalog()).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/network-modifications/catalog/line_types")));
    }

    @Test
    void testGetLineType() {
        UUID uuid = UUID.randomUUID();
        String url = "/v1/network-modifications/catalog/line_types/" + uuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(RESPONSE)));

        assertThat(networkModificationClient.getLineType(uuid)).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testGetLineTypeWithLimits() {
        UUID uuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/network-modifications/catalog/line_types/" + uuid + "/with-limits"))
            .withQueryParam("area", WireMock.equalTo("FR"))
            .withQueryParam("temperature", WireMock.equalTo("25"))
            .withQueryParam("shapeFactor", WireMock.equalTo("1.0"))
            .willReturn(WireMock.ok().withBody(RESPONSE)));

        assertThat(networkModificationClient.getLineTypeWithLimits(uuid, "FR", "25", "1.0")).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/network-modifications/catalog/line_types/" + uuid + "/with-limits")));
    }

    @Test
    void testGetNetworkModificationsFromComposite() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/v1/network-composite-modifications/network-modifications\\?.*" + firstUuid + ".*" + secondUuid + ".*onlyMetadata=false.*"))
            .willReturn(WireMock.ok().withBody(RESPONSE)));

        assertThat(networkModificationClient.getNetworkModificationsFromComposite(List.of(firstUuid, secondUuid), false)).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlMatching("/v1/network-composite-modifications/network-modifications\\?.*" + firstUuid + ".*" + secondUuid + ".*onlyMetadata=false.*")));
    }

    @Test
    void testGetNetworkModification() {
        UUID uuid = UUID.randomUUID();
        String url = "/v1/network-modifications/" + uuid;
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url)).willReturn(WireMock.ok().withBody(RESPONSE)));

        assertThat(networkModificationClient.getNetworkModification(uuid)).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testGetBusBarSectionsForNewCoupler() {
        wireMockServer.stubFor(WireMock.get(WireMock.urlMatching("/v1/network-modifications/busbar-sections-for-new-coupler\\?.*voltageLevelId=VL1.*busBarCount=2.*sectionCount=4.*BREAKER.*DISCONNECTOR.*"))
            .willReturn(WireMock.ok().withBody(RESPONSE)));

        assertThat(networkModificationClient.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).isEqualTo(RESPONSE);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlMatching("/v1/network-modifications/busbar-sections-for-new-coupler\\?.*voltageLevelId=VL1.*busBarCount=2.*sectionCount=4.*BREAKER.*DISCONNECTOR.*")));
    }

    @Test
    void testUpdateNetworkModification() {
        UUID uuid = UUID.randomUUID();
        String url = "/v1/network-modifications/" + uuid;
        wireMockServer.stubFor(WireMock.put(WireMock.urlEqualTo(url))
            .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.containing(MediaType.APPLICATION_JSON_VALUE))
            .withRequestBody(WireMock.equalTo(RESPONSE))
            .willReturn(WireMock.ok()));

        networkModificationClient.updateNetworkModification(uuid, RESPONSE);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlEqualTo(url)));
    }

    @Test
    void testUpdateNetworkModificationsMetadata() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String metadata = "{\"metadata\":true}";
        wireMockServer.stubFor(WireMock.put(WireMock.urlMatching("/v1/network-modifications\\?.*" + firstUuid + ".*" + secondUuid + ".*"))
            .withRequestBody(WireMock.equalTo(metadata))
            .willReturn(WireMock.ok()));

        networkModificationClient.updateNetworkModificationsMetadata(List.of(firstUuid, secondUuid), metadata);
        wireMockServer.verify(WireMock.putRequestedFor(WireMock.urlMatching("/v1/network-modifications\\?.*" + firstUuid + ".*" + secondUuid + ".*")));
    }
}
