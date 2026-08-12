/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkMapServiceTest {

    private static final String NETWORK_MAP_SERVER_URI = "http://network-map-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock
    private RestTemplate restTemplate;

    private NetworkMapService networkMapService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("network-map-server")).thenReturn(NETWORK_MAP_SERVER_URI);
        networkMapService = new NetworkMapService(remoteServicesProperties, restTemplate);
    }

    @Test
    void testGetElementSchema() {
        String response = "{\"type\":\"object\"}";
        String expectedUrl = NETWORK_MAP_SERVER_URI + "/v1/schemas/LINE/TAB";
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(response);

        assertThat(networkMapService.getElementSchema("LINE", "TAB")).isEqualTo(response);
    }
}
