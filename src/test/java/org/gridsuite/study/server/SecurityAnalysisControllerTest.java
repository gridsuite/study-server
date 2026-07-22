/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.SecurityAnalysisController;
import org.gridsuite.study.server.service.SecurityAnalysisService;
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
class SecurityAnalysisControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";

    @Mock
    private SecurityAnalysisService securityAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SecurityAnalysisController(securityAnalysisService)).build();
    }

    @Test
    void getProvidersReturnsJsonFromSecurityAnalysisService() throws Exception {
        when(securityAnalysisService.getProviders()).thenReturn(JSON);

        mockMvc.perform(get("/v1/security-analysis/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(securityAnalysisService).getProviders();
        verifyNoMoreInteractions(securityAnalysisService);
    }

    @Test
    void getSecurityAnalysisParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        when(securityAnalysisService.getSecurityAnalysisParameters(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/security-analysis/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(securityAnalysisService).getSecurityAnalysisParameters(ID);
        verifyNoMoreInteractions(securityAnalysisService);
    }

    @Test
    void getDefaultLimitReductionsReturnsJsonFromSecurityAnalysisService() throws Exception {
        when(securityAnalysisService.getDefaultLimitReductions()).thenReturn(JSON);

        mockMvc.perform(get("/v1/security-analysis/parameters/default-limit-reductions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(securityAnalysisService).getDefaultLimitReductions();
        verifyNoMoreInteractions(securityAnalysisService);
    }

    @Test
    void updateSecurityAnalysisParametersForwardsParameterUuidAndBody() throws Exception {
        mockMvc.perform(put("/v1/security-analysis/parameters/{parameterUuid}", ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist(HttpHeaders.CONTENT_TYPE))
            .andExpect(content().string(""));

        verify(securityAnalysisService).updateSecurityAnalysisParameters(ID, BODY);
        verifyNoMoreInteractions(securityAnalysisService);
    }
}
