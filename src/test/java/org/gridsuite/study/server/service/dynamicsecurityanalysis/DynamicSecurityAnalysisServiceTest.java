/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.dynamicsecurityanalysis;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.common.ComputationParametersService;
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
class DynamicSecurityAnalysisServiceTest {

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
    private DynamicSecurityAnalysisRestService dynamicSecurityAnalysisRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private UserAdminService userAdminService;

    private DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    @BeforeEach
    void setUp() {
        dynamicSecurityAnalysisService = new DynamicSecurityAnalysisService(studyRepository, computationParametersService, notificationService,
            rootNetworkNodeInfoService, dynamicSecurityAnalysisRestService, networkModificationTreeService, userAdminService, rootNetworkService);
    }

    @Test
    void testDownloadDebugFile() {
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource(PARAMETERS.getBytes()));
        when(dynamicSecurityAnalysisRestService.downloadDebugFile(RESULT_UUID)).thenReturn(response);

        assertThat(dynamicSecurityAnalysisService.downloadDebugFile(RESULT_UUID)).isEqualTo(response);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"Dynawo\"]";
        when(dynamicSecurityAnalysisRestService.getProviders()).thenReturn(providers);

        assertThat(dynamicSecurityAnalysisService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetParameters() {
        when(dynamicSecurityAnalysisRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(dynamicSecurityAnalysisService.getParameters(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }

    @Test
    void testUpdateParameters() {
        dynamicSecurityAnalysisService.updateParameters(PARAMETERS_UUID, PARAMETERS);

        verify(dynamicSecurityAnalysisRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }
}
