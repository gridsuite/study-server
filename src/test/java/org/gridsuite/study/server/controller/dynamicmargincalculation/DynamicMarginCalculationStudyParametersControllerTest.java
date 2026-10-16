/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicmargincalculation;

import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DynamicMarginCalculationStudyParametersControllerTest {

    private static final String BASE_URL = "/v1/studies/{studyUuid}/dynamic-margin-calculation";
    private static final String PARAMETERS = "{\"provider\":\"Dynawo\"}";
    private static final String USER_ID = "userId";

    @Mock
    private DynamicMarginCalculationService dynamicMarginCalculationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DynamicMarginCalculationStudyParametersController(dynamicMarginCalculationService)).build();
    }

    @Test
    void testGetDynamicMarginCalculationProvider() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicMarginCalculationService.getDynamicMarginCalculationProvider(studyUuid)).thenReturn("Dynawo");

        mockMvc.perform(get(BASE_URL + "/provider", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string("Dynawo"));

        verify(dynamicMarginCalculationService).getDynamicMarginCalculationProvider(studyUuid);
    }

    @Test
    void testSetDynamicMarginCalculationParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicMarginCalculationService.setDynamicMarginCalculationParameters(studyUuid, PARAMETERS, USER_ID)).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(dynamicMarginCalculationService).setDynamicMarginCalculationParameters(studyUuid, PARAMETERS, USER_ID);
    }

    @Test
    void testSetDynamicMarginCalculationParametersReturnsNoContent() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicMarginCalculationService.setDynamicMarginCalculationParameters(studyUuid, null, USER_ID)).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(dynamicMarginCalculationService).setDynamicMarginCalculationParameters(studyUuid, null, USER_ID);
    }

    @Test
    void testGetDynamicMarginCalculationParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicMarginCalculationService.getDynamicMarginCalculationParameters(studyUuid, USER_ID)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isOk())
            .andExpect(content().json(PARAMETERS));

        verify(dynamicMarginCalculationService).getDynamicMarginCalculationParameters(studyUuid, USER_ID);
    }
}
