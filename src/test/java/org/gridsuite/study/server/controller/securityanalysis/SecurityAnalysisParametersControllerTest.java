/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.securityanalysis;

import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisService;
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
class SecurityAnalysisParametersControllerTest {

    private static final String BASE_URL = "/v1/security-analysis";
    private static final String PARAMETERS = "{\"provider\":\"OpenLoadFlow\"}";

    @Mock
    private SecurityAnalysisService securityAnalysisService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SecurityAnalysisParametersController(securityAnalysisService)).build();
    }

    @Test
    void testGetProviders() throws Exception {
        String providers = "[\"OpenLoadFlow\"]";
        when(securityAnalysisService.getProviders()).thenReturn(providers);

        mockMvc.perform(get(BASE_URL + "/providers"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(providers));

        verify(securityAnalysisService).getProviders();
    }

    @Test
    void testGetSecurityAnalysisParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();
        when(securityAnalysisService.getSecurityAnalysisParameters(parameterUuid)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters/{parameterUuid}", parameterUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(PARAMETERS));

        verify(securityAnalysisService).getSecurityAnalysisParameters(parameterUuid);
    }

    @Test
    void testGetDefaultLimitReductions() throws Exception {
        String reductions = "[{\"nominalV\":400}]";
        when(securityAnalysisService.getDefaultLimitReductions()).thenReturn(reductions);

        mockMvc.perform(get(BASE_URL + "/parameters/default-limit-reductions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(reductions));

        verify(securityAnalysisService).getDefaultLimitReductions();
    }

    @Test
    void testUpdateSecurityAnalysisParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();

        mockMvc.perform(put(BASE_URL + "/parameters/{parameterUuid}", parameterUuid)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(securityAnalysisService).updateSecurityAnalysisParameters(parameterUuid, PARAMETERS);
