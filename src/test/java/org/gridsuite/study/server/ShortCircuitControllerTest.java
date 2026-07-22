/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.ShortCircuitController;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
class ShortCircuitControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private ShortCircuitService shortCircuitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ShortCircuitController(shortCircuitService)).build();
    }

    @Test
    void downloadDebugFileForwardsResultUuidAndReturnsFileContent() throws Exception {
        when(shortCircuitService.downloadDebugFile(ID)).thenReturn(ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json")
            .body(new ByteArrayResource("debug".getBytes())));

        mockMvc.perform(get("/v1/shortcircuit/results/{resultUuid}/download-debug-file", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json"))
            .andExpect(content().bytes("debug".getBytes()));

        verify(shortCircuitService).downloadDebugFile(ID);
        verifyNoMoreInteractions(shortCircuitService);
    }

    @Test
    void getSpecificParametersReturnsJsonFromShortCircuitService() throws Exception {
        when(shortCircuitService.getSpecificParameters()).thenReturn(JSON);

        mockMvc.perform(get("/v1/shortcircuit/parameters/specific-parameters"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(shortCircuitService).getSpecificParameters();
        verifyNoMoreInteractions(shortCircuitService);
    }

    @Test
    void getParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        when(shortCircuitService.getParameters(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/shortcircuit/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(shortCircuitService).getParameters(ID);
        verifyNoMoreInteractions(shortCircuitService);
    }

    @Test
    void updateParametersForwardsParameterUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/shortcircuit/parameters/{parameterUuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(shortCircuitService).updateParameters(ID, BODY);
        verifyNoMoreInteractions(shortCircuitService);
    }
}
