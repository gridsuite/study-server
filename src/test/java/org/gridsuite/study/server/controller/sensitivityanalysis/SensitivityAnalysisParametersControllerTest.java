/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.sensitivityanalysis;

import org.gridsuite.study.server.service.sensitivityanalysis.SensitivityAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
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
class SensitivityAnalysisParametersControllerTest {

    private static final String BASE_URL = "/v1/sensitivity-analysis";
    private static final String PARAMETERS = "{\"provider\":\"sensitivity\"}";

    @Mock
    private SensitivityAnalysisService sensitivityAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SensitivityAnalysisParametersController(sensitivityAnalysisService)).build();
    }

    @Test
    void testGetProviders() throws Exception {
        String providers = "[\"sensitivity\"]";
        when(sensitivityAnalysisService.getProviders()).thenReturn(providers);

        mockMvc.perform(get(BASE_URL + "/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(providers));

        verify(sensitivityAnalysisService).getProviders();
    }

    @Test
    void testGetSensitivityAnalysisParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();
        when(sensitivityAnalysisService.getSensitivityAnalysisParametersByUuid(parameterUuid)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters/{parameterUuid}", parameterUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(PARAMETERS));

        verify(sensitivityAnalysisService).getSensitivityAnalysisParametersByUuid(parameterUuid);
    }

    @Test
    void testUpdateSensitivityAnalysisParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();

        mockMvc.perform(put(BASE_URL + "/parameters/{parameterUuid}", parameterUuid)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

