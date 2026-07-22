/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.DynamicSecurityAnalysisController;
import org.gridsuite.study.server.service.client.dynamicsecurityanalysis.DynamicSecurityAnalysisClient;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
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
class DynamicSecurityAnalysisControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private DynamicSecurityAnalysisClient dynamicSecurityAnalysisClient;
    @Mock
    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DynamicSecurityAnalysisController(dynamicSecurityAnalysisClient, dynamicSecurityAnalysisService)).build();
    }

    @Test
    void getProvidersReturnsJsonFromDynamicSecurityAnalysisService() throws Exception {
        when(dynamicSecurityAnalysisService.getProviders()).thenReturn(JSON);

        mockMvc.perform(get("/v1/dynamic-security-analysis/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(dynamicSecurityAnalysisService).getProviders();
        verifyNoMoreInteractions(dynamicSecurityAnalysisService, dynamicSecurityAnalysisClient);
    }

    @Test
    void getParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        when(dynamicSecurityAnalysisService.getParameters(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/dynamic-security-analysis/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(dynamicSecurityAnalysisService).getParameters(ID);
        verifyNoMoreInteractions(dynamicSecurityAnalysisService, dynamicSecurityAnalysisClient);
    }

    @Test
    void updateParametersForwardsParameterUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/dynamic-security-analysis/parameters/{parameterUuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(dynamicSecurityAnalysisService).updateParameters(ID, BODY);
        verifyNoMoreInteractions(dynamicSecurityAnalysisService, dynamicSecurityAnalysisClient);
    }

    @Test
    void downloadDebugFileForwardsResultUuidAndReturnsFileContent() throws Exception {
        when(dynamicSecurityAnalysisClient.downloadDebugFile(ID)).thenReturn(ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json")
            .body(new ByteArrayResource("debug".getBytes())));

        mockMvc.perform(get("/v1/dynamic-security-analysis/results/{resultUuid}/download-debug-file", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json"))
            .andExpect(content().bytes("debug".getBytes()));

        verify(dynamicSecurityAnalysisClient).downloadDebugFile(ID);
        verifyNoMoreInteractions(dynamicSecurityAnalysisService, dynamicSecurityAnalysisClient);
    }
}
