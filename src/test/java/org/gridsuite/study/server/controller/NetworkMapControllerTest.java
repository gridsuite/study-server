/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller;

import org.gridsuite.study.server.service.NetworkMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NetworkMapControllerTest {

    @Mock
    private NetworkMapService networkMapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NetworkMapController(networkMapService)).build();
    }

    @Test
    void testGetElementSchema() throws Exception {
        String schema = "{\"type\":\"object\"}";
        when(networkMapService.getElementSchema("LINE", "FORM")).thenReturn(schema);

        mockMvc.perform(get("/v1/network-map/schemas/{elementType}/{infoType}", "LINE", "FORM"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(NetworkMapController.APPLICATION_JSON_SCHEMA_VALUE))
            .andExpect(content().json(schema));

        verify(networkMapService).getElementSchema("LINE", "FORM");
    }
}
