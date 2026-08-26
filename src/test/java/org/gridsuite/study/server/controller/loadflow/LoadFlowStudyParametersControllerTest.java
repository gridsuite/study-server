/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.loadflow;

import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.loadflow.LoadFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class LoadFlowStudyParametersControllerTest {

    private static final String BASE_URL = "/v1/studies/{studyUuid}/loadflow";
    private static final String PARAMETERS = "{\"provider\":\"OpenLoadFlow\"}";
    private static final String USER_ID = "userId";

    @Mock
    private StudyService studyService;

    @Mock
    private NetworkModificationTreeService networkModificationTreeService;

    @Mock
    private LoadFlowService loadFlowService;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LoadFlowStudyParametersController(studyService, networkModificationTreeService, loadFlowService)).build();
    }

    @Test
    void testSetLoadflowParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNodeUuid = UUID.randomUUID();
        when(networkModificationTreeService.getStudyRootNodeUuid(studyUuid)).thenReturn(rootNodeUuid);
        when(loadFlowService.setLoadFlowParameters(studyUuid, PARAMETERS, USER_ID)).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID)
                .content(PARAMETERS)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().string(""));

        InOrder inOrder = inOrder(networkModificationTreeService, studyService, loadFlowService);
        inOrder.verify(networkModificationTreeService).getStudyRootNodeUuid(studyUuid);
        inOrder.verify(studyService).assertNoBlockedNodeInStudy(studyUuid, rootNodeUuid);
        inOrder.verify(loadFlowService).setLoadFlowParameters(studyUuid, PARAMETERS, USER_ID);
    }

    @Test
    void testSetLoadflowParametersReturnsNoContent() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID rootNodeUuid = UUID.randomUUID();
        when(networkModificationTreeService.getStudyRootNodeUuid(studyUuid)).thenReturn(rootNodeUuid);
        when(loadFlowService.setLoadFlowParameters(studyUuid, null, USER_ID)).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/parameters", studyUuid)
                .header(HEADER_USER_ID, USER_ID))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

        verify(studyService).assertNoBlockedNodeInStudy(studyUuid, rootNodeUuid);
        verify(loadFlowService).setLoadFlowParameters(studyUuid, null, USER_ID);
    }

    @Test
    void testGetLoadflowParameters() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(loadFlowService.getLoadFlowParametersInfos(studyUuid)).thenReturn(LoadFlowParametersInfos.builder().provider("OpenLoadFlow").build());

        mockMvc.perform(get(BASE_URL + "/parameters", studyUuid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("OpenLoadFlow"));

        verify(loadFlowService).getLoadFlowParametersInfos(studyUuid);
    }

    @Test
    void testGetLoadflowParametersId() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        UUID parametersUuid = UUID.randomUUID();
        when(loadFlowService.getLoadFlowParametersId(studyUuid)).thenReturn(parametersUuid);

        mockMvc.perform(get(BASE_URL + "/parameters/id", studyUuid))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(parametersUuid.toString()));

        verify(loadFlowService).getLoadFlowParametersId(studyUuid);
    }

    @Test
    void testGetLoadFlowProvider() throws Exception {
        UUID studyUuid = UUID.randomUUID();
        when(loadFlowService.getLoadFlowProvider(studyUuid)).thenReturn("OpenLoadFlow");

        mockMvc.perform(get(BASE_URL + "/provider", studyUuid))
            .andExpect(status().isOk())
            .andExpect(content().string("OpenLoadFlow"));

        verify(loadFlowService).getLoadFlowProvider(studyUuid);
    }
}
