/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsimulation;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicSimulationServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"Dynawo\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DynamicSimulationRestService dynamicSimulationRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private DynamicSimulationEventService dynamicSimulationEventService;
    @Mock
    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    private DynamicSimulationService dynamicSimulationService;

    @BeforeEach
    void setUp() {
        dynamicSimulationService = new DynamicSimulationService(studyRepository, computationParametersService, notificationService,
            rootNetworkNodeInfoService, dynamicSimulationRestService, dynamicSecurityAnalysisService, dynamicSimulationEventService,
            networkModificationTreeService, userAdminService, rootNetworkService);
    }

    @Test
    void testGetParameters() {
        when(dynamicSimulationRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(dynamicSimulationService.getParameters(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"Dynawo\"]";
        when(dynamicSimulationRestService.getProviders()).thenReturn(providers);

        assertThat(dynamicSimulationService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testUpdateParameters() {
        dynamicSimulationService.updateParameters(PARAMETERS_UUID, PARAMETERS);

        verify(dynamicSimulationRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }

    @Test
    void testDownloadDebugFile() {
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource(PARAMETERS.getBytes()));
        when(dynamicSimulationRestService.downloadDebugFile(RESULT_UUID)).thenReturn(response);

        assertThat(dynamicSimulationService.downloadDebugFile(RESULT_UUID)).isEqualTo(response);
    }
}
