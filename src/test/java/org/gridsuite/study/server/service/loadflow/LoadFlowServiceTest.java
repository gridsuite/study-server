/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.loadflow;

import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadFlowServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"OpenLoadFlow\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private LoadFlowRestService loadFlowRestService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private ComputationParametersService computationParametersService;

    private LoadFlowService loadFlowService;

    @BeforeEach
    void setUp() {
        loadFlowService = new LoadFlowService(studyRepository, loadFlowRestService, notificationService, rootNetworkNodeInfoService, computationParametersService);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(loadFlowRestService.getProviders()).thenReturn(providers);

        assertThat(loadFlowService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetSpecificParameters() {
        when(loadFlowRestService.getSpecificParameters()).thenReturn(PARAMETERS);

        assertThat(loadFlowService.getSpecificParameters()).isEqualTo(PARAMETERS);
    }

    @Test
    void testGetDefaultLimitReductions() {
        String defaultLimitReductions = "[]";
        when(loadFlowRestService.getDefaultLimitReductions()).thenReturn(defaultLimitReductions);

        assertThat(loadFlowService.getDefaultLimitReductions()).isEqualTo(defaultLimitReductions);
    }

    @Test
    void testGetLoadFlowParameters() {
        LoadFlowParametersInfos parameters = new LoadFlowParametersInfos();
        when(loadFlowRestService.getParameters(PARAMETERS_UUID)).thenReturn(parameters);

        assertThat(loadFlowService.getLoadFlowParameters(PARAMETERS_UUID)).isEqualTo(parameters);
    }

    @Test
    void testUpdateLoadFlowParameters() {
        loadFlowService.updateLoadFlowParameters(PARAMETERS_UUID, PARAMETERS);

        verify(loadFlowRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }
}
