/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.SensitivityAnalysisController;
import org.gridsuite.study.server.service.SensitivityAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class SensitivityAnalysisControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private SensitivityAnalysisService sensitivityAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SensitivityAnalysisController(sensitivityAnalysisService)).build();
    }

    @Test
    void getProvidersReturnsJsonFromSensitivityAnalysisService() throws Exception {
        when(sensitivityAnalysisService.getProviders()).thenReturn(JSON);

        mockMvc.perform(get("/v1/sensitivity-analysis/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(sensitivityAnalysisService).getProviders();
        verifyNoMoreInteractions(sensitivityAnalysisService);
    }

    @Test
    void getSensitivityAnalysisParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        when(sensitivityAnalysisService.getSensitivityAnalysisParameters(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/sensitivity-analysis/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(sensitivityAnalysisService).getSensitivityAnalysisParameters(ID);
        verifyNoMoreInteractions(sensitivityAnalysisService);
    }

    @Test
    void updateSensitivityAnalysisParametersForwardsParameterUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/sensitivity-analysis/parameters/{parameterUuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(sensitivityAnalysisService).updateSensitivityAnalysisParameters(ID, BODY);
        verifyNoMoreInteractions(sensitivityAnalysisService);
    }
}
