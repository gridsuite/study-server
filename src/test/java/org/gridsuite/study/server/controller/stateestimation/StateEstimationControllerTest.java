/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.controller.stateestimation;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.nodeactivity.NodeActivityRunnerService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.stateestimation.StateEstimationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Kamil MARUT {@literal <kamil.marut at rte-france.com>}
 */
@ExtendWith(MockitoExtension.class)
class StateEstimationControllerTest {

    private static final String URL = "/v1/studies/{studyUuid}/root-networks/{rootNetworkUuid}/nodes/{nodeUuid}/state-estimation/measurements";
    private static final String GLOBAL_FILTERS = "{\"countryCode\":[\"FR\"],\"label\":\"a+b %41 & c=d é\"}";

    private final UUID studyUuid = UUID.randomUUID();
    private final UUID rootNetworkUuid = UUID.randomUUID();
    private final UUID nodeUuid = UUID.randomUUID();

    @Mock
    private StudyService studyService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private StateEstimationService stateEstimationService;
    @Mock
    private NodeActivityRunnerService nodeActivityRunnerService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
            new StateEstimationController(studyService, rootNetworkNodeInfoService, stateEstimationService, nodeActivityRunnerService)).build();
    }

    @Test
    void returnsDownstreamArrayAfterOwnershipCheck() throws Exception {
        String measurements = "[{\"id\":\"L1\"},{\"id\":\"L1\"}]";
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, GLOBAL_FILTERS)).thenReturn(measurements);

        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string(measurements));

        InOrder inOrder = inOrder(studyService, stateEstimationService);
        inOrder.verify(studyService).assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
        inOrder.verify(stateEstimationService).getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, GLOBAL_FILTERS);
    }

    @Test
    void globalFiltersAreOptional() throws Exception {
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, null)).thenReturn("[]");

        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid))
            .andExpect(status().isOk())
            .andExpect(content().string("[]"));
    }

    @Test
    void noMatchIsEmptyArrayNotNoContent() throws Exception {
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, GLOBAL_FILTERS)).thenReturn("[]");

        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS))
            .andExpect(status().isOk())
            .andExpect(content().string("[]"));
    }

    @Test
    void noStoredResultIsNoContent() throws Exception {
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, GLOBAL_FILTERS)).thenReturn(null);

        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"filters", "sort"})
    void rejectsColumnFiltersAndSort(String parameter) throws Exception {
        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS).queryParam(parameter, "[]"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam(parameter, ""))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(studyService, stateEstimationService);
    }

    @Test
    void notOwnedNodeStopsBeforeResolution() {
        doThrow(new StudyException(NOT_FOUND, "Node not found")).when(studyService).assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);

        assertThatThrownBy(() -> mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS)))
            .hasCauseInstanceOf(StudyException.class);
        verifyNoInteractions(stateEstimationService);
    }

    @Test
    void downstreamNotFoundIsNotTurnedIntoNoContent() throws Exception {
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, GLOBAL_FILTERS))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid).queryParam("globalFilters", GLOBAL_FILTERS))
            .andExpect(status().isNotFound());
        verify(studyService).assertIsRootNetworkAndNodeInStudy(studyUuid, rootNetworkUuid, nodeUuid);
    }

    @Test
    void responseKeepsUnicodeAsUtf8() throws Exception {
        String measurements = "[{\"id\":\"Café é\"}]";
        when(stateEstimationService.getStateEstimationMeasurements(nodeUuid, rootNetworkUuid, null)).thenReturn(measurements);

        byte[] body = mockMvc.perform(get(URL, studyUuid, rootNetworkUuid, nodeUuid)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(measurements.getBytes(StandardCharsets.UTF_8));
    }
}
