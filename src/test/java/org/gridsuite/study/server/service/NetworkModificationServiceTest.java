/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReferenceData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkModificationServiceTest {

    private static final String NETWORK_MODIFICATION_SERVER_URI = "http://network-modification-server";
    private static final String RESPONSE = "{\"id\":\"modification\"}";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RootNetworkService rootNetworkService;

    private NetworkModificationService networkModificationService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("network-modification-server")).thenReturn(NETWORK_MODIFICATION_SERVER_URI);
        networkModificationService = new NetworkModificationService(remoteServicesProperties, restTemplate, new ObjectMapper(), rootNetworkService);
    }

    @Test
    void testGetLineTypesCatalog() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types";
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineTypesCatalog()).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineType() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid;
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineType(lineTypeUuid)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetLineTypeWithLimits() {
        UUID lineTypeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/catalog/line_types/" + lineTypeUuid + "/with-limits?area=FR&temperature=25&shapeFactor=1.0";
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getLineTypeWithLimits(lineTypeUuid, "FR", "25", "1.0")).isEqualTo(RESPONSE);
    }

    @Test
    void testGetNetworkModificationsFromComposite() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-composite-modifications/network-modifications?uuids=" + firstUuid + "&uuids=" + secondUuid + "&onlyMetadata=false";
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getNetworkModificationsFromComposite(List.of(firstUuid, secondUuid), false)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getNetworkModification(modificationUuid)).isEqualTo(RESPONSE);
    }

    @Test
    void testGetBusBarSectionsForNewCoupler() {
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI
            + "/v1/network-modifications/busbar-sections-for-new-coupler?voltageLevelId=VL1&busBarCount=2&sectionCount=4&switchKindList=BREAKER&switchKindList=DISCONNECTOR";
        when(restTemplate.getForObject(expectedUrl, String.class)).thenReturn(RESPONSE);

        assertThat(networkModificationService.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).isEqualTo(RESPONSE);
    }

    @Test
    void testUpdateNetworkModification() {
        UUID modificationUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications/" + modificationUuid;

        networkModificationService.updateNetworkModification(modificationUuid, RESPONSE);

        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.PUT), org.mockito.ArgumentMatchers.<HttpEntity<String>>any(), eq(Void.class));
    }

    @Test
    void testUpdateNetworkModificationsMetadata() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-modifications?uuids=" + firstUuid + "&uuids=" + secondUuid;

        networkModificationService.updateNetworkModificationsMetadata(List.of(firstUuid, secondUuid), RESPONSE);

        verify(restTemplate).exchange(eq(expectedUrl), eq(HttpMethod.PUT), org.mockito.ArgumentMatchers.<HttpEntity<String>>any(), eq(Void.class));
    }

    @Test
    void testGetReferences() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/references?uuids=" + firstUuid + "&uuids=" + secondUuid;
        List<ReferenceData> expected = List.of(new ReferenceData(firstUuid, UUID.randomUUID(), null));
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                Mockito.<ParameterizedTypeReference<List<ReferenceData>>>any()))
                .thenReturn(ResponseEntity.ok(expected));

        assertThat(networkModificationService.getReferences(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testFindParentComposites() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID compositeUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-composite-modifications/parent-composites?uuids=" + firstUuid + "&uuids=" + secondUuid;
        Map<UUID, UUID> expected = Map.of(firstUuid, compositeUuid);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                Mockito.<ParameterizedTypeReference<Map<UUID, UUID>>>any()))
                .thenReturn(ResponseEntity.ok(expected));

        assertThat(networkModificationService.findParentComposites(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testFindRootGroupByModification() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/network-composite-modifications/root-groups?uuids=" + firstUuid + "&uuids=" + secondUuid;
        Map<UUID, UUID> expected = Map.of(firstUuid, groupUuid, secondUuid, groupUuid);
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                Mockito.<ParameterizedTypeReference<Map<UUID, UUID>>>any()))
                .thenReturn(ResponseEntity.ok(expected));

        assertThat(networkModificationService.findRootGroupByModification(List.of(firstUuid, secondUuid))).isEqualTo(expected);
    }

    @Test
    void testGetReferencesFromGroup() {
        UUID groupUuid = UUID.randomUUID();
        String expectedUrl = NETWORK_MODIFICATION_SERVER_URI + "/v1/groups/" + groupUuid + "/references";
        List<ReferenceData> expected = List.of(new ReferenceData(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        when(restTemplate.exchange(
                eq(expectedUrl),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                Mockito.<ParameterizedTypeReference<List<ReferenceData>>>any()))
                .thenReturn(ResponseEntity.ok(expected));

        assertThat(networkModificationService.getReferencesFromGroup(groupUuid)).isEqualTo(expected);
    }
}
