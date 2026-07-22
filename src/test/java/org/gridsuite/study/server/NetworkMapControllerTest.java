/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.NetworkMapController;
import org.gridsuite.study.server.service.NetworkMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NetworkMapControllerTest {

    private static final String JSON = "{\"name\":\"value\"}";

    @Mock
    private NetworkMapService networkMapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NetworkMapController(networkMapService)).build();
    }

    @Test
    void getElementSchemaForwardsPathVariablesAndSchemaContentType() throws Exception {
        when(networkMapService.getElementSchema("GENERATOR", "TAB")).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-map/schemas/{elementType}/{infoType}", "GENERATOR", "TAB"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", NetworkMapController.APPLICATION_JSON_SCHEMA_VALUE))
            .andExpect(content().json(JSON));

        verify(networkMapService).getElementSchema("GENERATOR", "TAB");
        verifyNoMoreInteractions(networkMapService);
    }
}
