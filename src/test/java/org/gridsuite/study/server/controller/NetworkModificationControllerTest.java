/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.service.NetworkModificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NetworkModificationControllerTest {

    private static final String BASE_URL = "/v1";
    private static final String RESPONSE = "{\"name\":\"modification\"}";

    @Mock
    private NetworkModificationService networkModificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NetworkModificationController(networkModificationService)).build();
    }

    @Test
    void testGetLineTypesCatalog() throws Exception {
        String response = "[{\"name\":\"lineType\"}]";
        when(networkModificationService.getLineTypesCatalog()).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/network-modifications/catalog/line_types"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(response));

        verify(networkModificationService).getLineTypesCatalog();
    }

    @Test
    void testGetLineType() throws Exception {
        UUID lineTypeUuid = UUID.randomUUID();
        when(networkModificationService.getLineType(lineTypeUuid)).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_URL + "/network-modifications/catalog/line_types/{uuid}", lineTypeUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE));

        verify(networkModificationService).getLineType(lineTypeUuid);
    }

    @Test
    void testGetLineTypeWithLimits() throws Exception {
        UUID lineTypeUuid = UUID.randomUUID();
        when(networkModificationService.getLineTypeWithLimits(lineTypeUuid, "FR", "25", "1.0")).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_URL + "/network-modifications/catalog/line_types/{uuid}/with-limits", lineTypeUuid)
                .param("area", "FR")
                .param("temperature", "25")
                .param("shapeFactor", "1.0"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE));

        verify(networkModificationService).getLineTypeWithLimits(lineTypeUuid, "FR", "25", "1.0");
    }

    @Test
    void testGetNetworkModificationsFromComposite() throws Exception {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        when(networkModificationService.getNetworkModificationsFromComposite(List.of(firstUuid, secondUuid), false)).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_URL + "/network-composite-modifications/network-modifications")
                .param("uuids", firstUuid.toString(), secondUuid.toString())
                .param("onlyMetadata", "false"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE));

        verify(networkModificationService).getNetworkModificationsFromComposite(List.of(firstUuid, secondUuid), false);
    }

    @Test
    void testGetNetworkModification() throws Exception {
        UUID modificationUuid = UUID.randomUUID();
        when(networkModificationService.getNetworkModification(modificationUuid)).thenReturn(RESPONSE);

        mockMvc.perform(get(BASE_URL + "/network-modifications/{uuid}", modificationUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(RESPONSE));

        verify(networkModificationService).getNetworkModification(modificationUuid);
    }

    @Test
    void testGetBusBarSectionsForNewCoupler() throws Exception {
        String response = "[{\"id\":\"bbs1\"}]";
        when(networkModificationService.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/network-modifications/busbar-sections-for-new-coupler")
                .param("voltageLevelId", "VL1")
                .param("busBarCount", "2")
                .param("sectionCount", "4")
                .param("switchKindList", "BREAKER", "DISCONNECTOR"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(response));

        verify(networkModificationService).getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"));
    }

    @Test
    void testUpdateNetworkModification() throws Exception {
        UUID modificationUuid = UUID.randomUUID();

        mockMvc.perform(put(BASE_URL + "/network-modifications/{uuid}", modificationUuid)
                .content(RESPONSE)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(networkModificationService).updateNetworkModification(modificationUuid, RESPONSE);
    }

    @Test
    void testUpdateNetworkModificationsMetadata() throws Exception {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        String metadata = "{\"message\":\"metadata\"}";

        mockMvc.perform(put(BASE_URL + "/network-modifications")
                .param("uuids", firstUuid.toString(), secondUuid.toString())
                .content(metadata)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(networkModificationService).updateNetworkModificationsMetadata(List.of(firstUuid, secondUuid), metadata);
    }
}
