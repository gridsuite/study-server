/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.shortcircuit;

import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShortCircuitParametersControllerTest {

    private static final String BASE_URL = "/v1/shortcircuit";
    private static final String PARAMETERS = "{\"withLimitViolations\":true}";

    @Mock
    private ShortCircuitService shortCircuitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ShortCircuitParametersController(shortCircuitService)).build();
    }

    @Test
    void testDownloadDebugFile() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        when(shortCircuitService.downloadDebugFile(resultUuid))
            .thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new ByteArrayResource(PARAMETERS.getBytes())));

        mockMvc.perform(get(BASE_URL + "/results/{resultUuid}/download-debug-file", resultUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(PARAMETERS));

        verify(shortCircuitService).downloadDebugFile(resultUuid);
    }

    @Test
    void testGetSpecificParameters() throws Exception {
        when(shortCircuitService.getSpecificParameters()).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters/specific-parameters"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(PARAMETERS));

        verify(shortCircuitService).getSpecificParameters();
    }

    @Test
    void testGetParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();
        when(shortCircuitService.getParameters(parameterUuid)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters/{parameterUuid}", parameterUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(PARAMETERS));

        verify(shortCircuitService).getParameters(parameterUuid);
    }

    @Test
    void testUpdateParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();

        mockMvc.perform(put(BASE_URL + "/parameters/{parameterUuid}", parameterUuid)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
