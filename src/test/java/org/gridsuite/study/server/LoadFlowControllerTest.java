/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.loadflow.LoadFlowParameters;
import org.gridsuite.study.server.controller.LoadFlowController;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.service.LoadFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
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
class LoadFlowControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private LoadFlowService loadFlowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LoadFlowController(loadFlowService)).build();
    }

    @Test
    void getProvidersReturnsJsonFromLoadFlowService() throws Exception {
        when(loadFlowService.getProviders()).thenReturn(JSON);

        mockMvc.perform(get("/v1/loadflow/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(loadFlowService).getProviders();
        verifyNoMoreInteractions(loadFlowService);
    }

    @Test
    void getSpecificParametersReturnsJsonFromLoadFlowService() throws Exception {
        when(loadFlowService.getSpecificParameters()).thenReturn(JSON);

        mockMvc.perform(get("/v1/loadflow/specific-parameters"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(loadFlowService).getSpecificParameters();
        verifyNoMoreInteractions(loadFlowService);
    }

    @Test
    void getDefaultLimitReductionsReturnsJsonFromLoadFlowService() throws Exception {
        when(loadFlowService.getDefaultLimitReductions()).thenReturn(JSON);

        mockMvc.perform(get("/v1/loadflow/parameters/default-limit-reductions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(loadFlowService).getDefaultLimitReductions();
        verifyNoMoreInteractions(loadFlowService);
    }

    @Test
    void getLoadFlowParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        LoadFlowParametersInfos parametersInfos = LoadFlowParametersInfos.builder()
            .commonParameters(LoadFlowParameters.load())
            .specificParametersPerProvider(Map.of())
            .build();
        when(loadFlowService.getLoadFlowParameters(ID)).thenReturn(parametersInfos);

        mockMvc.perform(get("/v1/loadflow/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{\"specificParametersPerProvider\":{}}"));

        verify(loadFlowService).getLoadFlowParameters(ID);
        verifyNoMoreInteractions(loadFlowService);
    }

    @Test
    void updateLoadFlowParametersForwardsParameterUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/loadflow/parameters/{parameterUuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(loadFlowService).updateLoadFlowParameters(ID, BODY);
        verifyNoMoreInteractions(loadFlowService);
    }
}
