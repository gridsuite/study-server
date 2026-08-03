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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StateEstimationParametersControllerTest {

    @Mock
    private StateEstimationService stateEstimationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StateEstimationParametersController(stateEstimationService)).build();
    }

    @Test
    void testDownloadDebugFile() throws Exception {
        UUID resultUuid = UUID.randomUUID();
        String debugFile = "{\"debug\":true}";
        when(stateEstimationService.downloadDebugFile(resultUuid))
            .thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new ByteArrayResource(debugFile.getBytes())));

        mockMvc.perform(get("/v1/state-estimation/results/{resultUuid}/download-debug-file", resultUuid))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(debugFile));

        verify(stateEstimationService).downloadDebugFile(resultUuid);
    }
