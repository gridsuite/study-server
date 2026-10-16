/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.dynamicsimulation;

import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
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
class DynamicSimulationStudyParametersControllerTest {

    private static final String BASE_URL = "/v1/studies/{studyUuid}/dynamic-simulation";
    private static final String PARAMETERS = "{\"provider\":\"Dynawo\"}";
    private static final String USER_ID = "userId";

    @Mock
    private DynamicSimulationService dynamicSimulationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DynamicSimulationStudyParametersController(dynamicSimulationService)).build();
    }

    @Test
    void testSetDynamicSimulationParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(dynamicSimulationService).setDynamicSimulationParameters(studyUuid, PARAMETERS, USER_ID);
    }

    @Test
    void testGetDynamicSimulationParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicSimulationService.getDynamicSimulationParameters(studyUuid)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().json(PARAMETERS));

        verify(dynamicSimulationService).getDynamicSimulationParameters(studyUuid);
    }

    @Test
    void testGetDynamicSimulationProvider() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(dynamicSimulationService.getDynamicSimulationProvider(studyUuid)).thenReturn("Dynawo");

        mockMvc.perform(get(BASE_URL + "/provider", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string("Dynawo"));

        verify(dynamicSimulationService).getDynamicSimulationProvider(studyUuid);
    }
}
