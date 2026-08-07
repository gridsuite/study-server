/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.stateestimation;

import org.gridsuite.study.server.service.stateestimation.StateEstimationService;
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
class StateEstimationStudyParametersControllerTest {

    private static final String BASE_URL = "/v1/studies/{studyUuid}/state-estimation";
    private static final String PARAMETERS = "{\"provider\":\"state-estimation\"}";
    private static final String USER_ID = "userId";

    @Mock
    private StateEstimationService stateEstimationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StateEstimationStudyParametersController(stateEstimationService)).build();
    }

    @Test
    void testGetStateEstimationParametersValues() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(stateEstimationService.getStateEstimationParameters(studyUuid)).thenReturn(PARAMETERS);

        mockMvc.perform(get(BASE_URL + "/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().json(PARAMETERS));

        verify(stateEstimationService).getStateEstimationParameters(studyUuid);
    }

    @Test
    void testSetStateEstimationParametersValues() throws Exception {
        UUID studyUuid = UUID.randomUUID();

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        verify(stateEstimationService).setStateEstimationParametersValues(studyUuid, PARAMETERS, USER_ID);
    }
}
