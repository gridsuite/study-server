/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.NetworkModificationController;
import org.gridsuite.study.server.service.NetworkModificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NetworkModificationControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID SECOND_ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private NetworkModificationService networkModificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NetworkModificationController(networkModificationService)).build();
    }

    @Test
    void getLineTypesCatalogReturnsJsonFromNetworkModificationService() throws Exception {
        when(networkModificationService.getLineTypesCatalog()).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-modifications/catalog/line_types"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getLineTypesCatalog();
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void getLineTypeForwardsUuidAndReturnsJson() throws Exception {
        when(networkModificationService.getLineType(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-modifications/catalog/line_types/{uuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getLineType(ID);
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void getLineTypeWithLimitsForwardsPathAndQueryParametersAndReturnsJson() throws Exception {
        when(networkModificationService.getLineTypeWithLimits(ID, "FR", "20", "1.1")).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-modifications/catalog/line_types/{uuid}/with-limits", ID)
                .param("area", "FR")
                .param("temperature", "20")
                .param("shapeFactor", "1.1"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getLineTypeWithLimits(ID, "FR", "20", "1.1");
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void getNetworkModificationsFromCompositeForwardsUuidsAndOnlyMetadataFlagAndReturnsJson() throws Exception {
        List<UUID> uuids = List.of(ID, SECOND_ID);
        when(networkModificationService.getNetworkModificationsFromComposite(uuids, false)).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-composite-modifications/network-modifications")
                .param("uuids", ID.toString(), SECOND_ID.toString())
                .param("onlyMetadata", "false"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getNetworkModificationsFromComposite(uuids, false);
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void getNetworkModificationForwardsUuidAndReturnsJson() throws Exception {
        when(networkModificationService.getNetworkModification(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-modifications/{uuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getNetworkModification(ID);
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void getBusBarSectionsForNewCouplerForwardsQueryParametersAndReturnsJson() throws Exception {
        when(networkModificationService.getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"))).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-modifications/busbar-sections-for-new-coupler")
                .param("voltageLevelId", "VL1")
                .param("busBarCount", "2")
                .param("sectionCount", "4")
                .param("switchKindList", "BREAKER", "DISCONNECTOR"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkModificationService).getBusBarSectionsForNewCoupler("VL1", 2, 4, List.of("BREAKER", "DISCONNECTOR"));
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void updateNetworkModificationForwardsUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/network-modifications/{uuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(networkModificationService).updateNetworkModification(ID, BODY);
        verifyNoMoreInteractions(networkModificationService);
    }

    @Test
    void updateNetworkModificationsMetadataForwardsUuidsAndBody() throws Exception {
        List<UUID> uuids = List.of(ID, SECOND_ID);

        mockMvc.perform(put("/v1/network-modifications")
                .param("uuids", ID.toString(), SECOND_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(networkModificationService).updateNetworkModificationsMetadata(uuids, BODY);
        verifyNoMoreInteractions(networkModificationService);
    }
}
