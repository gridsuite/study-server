/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.voltageinit;

import org.gridsuite.study.server.dto.voltageinit.parameters.StudyVoltageInitParameters;
import org.gridsuite.study.server.service.voltageinit.VoltageInitService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoltageInitStudyParametersControllerTest {

    private static final String BASE_URL = "/v1/studies/{studyUuid}/voltage-init";
    private static final String PARAMETERS = "{\"applyModifications\":true}";
    private static final String USER_ID = "userId";

    @Mock
    private VoltageInitService voltageInitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VoltageInitStudyParametersController(voltageInitService)).build();
    }

    @Test
    void testSetVoltageInitParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        StudyVoltageInitParameters parameters = StudyVoltageInitParameters.builder().applyModifications(true).build();
        when(voltageInitService.setVoltageInitParameters(studyUuid, parameters, USER_ID)).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(voltageInitService).setVoltageInitParameters(studyUuid, parameters, USER_ID);
    }

    @Test
    void testSetVoltageInitParametersReturnsNoContent() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(voltageInitService.setVoltageInitParameters(studyUuid, null, USER_ID)).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(voltageInitService).setVoltageInitParameters(studyUuid, null, USER_ID);
    }

    @Test
    void testGetVoltageInitParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(voltageInitService.getVoltageInitParameters(studyUuid)).thenReturn(StudyVoltageInitParameters.builder().applyModifications(true).build());

        mockMvc.perform(get(BASE_URL + "/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applyModifications").value(true));

        verify(voltageInitService).getVoltageInitParameters(studyUuid);
    }
}
