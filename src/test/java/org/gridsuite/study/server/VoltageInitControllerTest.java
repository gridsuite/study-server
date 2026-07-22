/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.gridsuite.study.server.controller.VoltageInitController;
import org.gridsuite.study.server.service.VoltageInitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoltageInitControllerTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private VoltageInitService voltageInitService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VoltageInitController(voltageInitService)).build();
    }

    @Test
    void downloadDebugFileForwardsResultUuidAndReturnsFileContent() throws Exception {
        when(voltageInitService.downloadDebugFile(ID)).thenReturn(ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json")
            .body(new ByteArrayResource("debug".getBytes())));

        mockMvc.perform(get("/v1/voltage-init/results/{resultUuid}/download-debug-file", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=debug.json"))
            .andExpect(content().bytes("debug".getBytes()));

        verify(voltageInitService).downloadDebugFile(ID);
        verifyNoMoreInteractions(voltageInitService);
    }

    @Test
    void getParametersForwardsParameterUuidAndReturnsJson() throws Exception {
        VoltageInitParametersInfos parametersInfos = VoltageInitParametersInfos.builder().build();
        when(voltageInitService.getVoltageInitParameters(ID)).thenReturn(parametersInfos);

        mockMvc.perform(get("/v1/voltage-init/parameters/{parameterUuid}", ID))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json("{}"));

        verify(voltageInitService).getVoltageInitParameters(ID);
        verifyNoMoreInteractions(voltageInitService);
    }
}
