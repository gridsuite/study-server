/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.NetworkConversionController;
import org.gridsuite.study.server.service.NetworkConversionService;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NetworkConversionControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";

    @Mock
    private NetworkConversionService networkConversionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NetworkConversionController(networkConversionService)).build();
    }

    @Test
    void getCaseImportParametersForwardsCaseUuid() throws Exception {
        when(networkConversionService.getCaseImportParameters(ID)).thenReturn(JSON);

        mockMvc.perform(get("/v1/network-conversion/cases/{caseUuid}/import-parameters", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(networkConversionService).getCaseImportParameters(ID);
        verifyNoMoreInteractions(networkConversionService);
    }
}
