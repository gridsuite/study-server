/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.sensitivityanalysis;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
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
class SensitivityAnalysisServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"OpenLoadFlow\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private SensitivityAnalysisRestService sensitivityAnalysisRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private DirectoryService directoryService;

    private SensitivityAnalysisService sensitivityAnalysisService;

    @BeforeEach
    void setUp() {
        sensitivityAnalysisService = new SensitivityAnalysisService(studyRepository, computationParametersService, notificationService,
            rootNetworkNodeInfoService, sensitivityAnalysisRestService, networkModificationTreeService, rootNetworkService,
            userAdminService, directoryService);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(sensitivityAnalysisRestService.getProviders()).thenReturn(providers);

        assertThat(sensitivityAnalysisService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetSensitivityAnalysisParametersByUuid() {
        when(sensitivityAnalysisRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(sensitivityAnalysisService.getSensitivityAnalysisParametersByUuid(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }

    @Test
    void testUpdateSensitivityAnalysisParameters() {
        sensitivityAnalysisService.updateSensitivityAnalysisParameters(PARAMETERS_UUID, PARAMETERS);

        verify(sensitivityAnalysisRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }
}
