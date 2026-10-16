/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.securityanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class SecurityAnalysisServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"OpenLoadFlow\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SecurityAnalysisRestService securityAnalysisRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private UserAdminService userAdminService;

    private SecurityAnalysisService securityAnalysisService;

    @BeforeEach
    void setUp() {
        securityAnalysisService = new SecurityAnalysisService(studyRepository, computationParametersService, notificationService,
            securityAnalysisRestService, networkModificationTreeService, new ObjectMapper(), rootNetworkService,
            rootNetworkNodeInfoService, userAdminService);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(securityAnalysisRestService.getProviders()).thenReturn(providers);

        assertThat(securityAnalysisService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetSecurityAnalysisParameters() {
        when(securityAnalysisRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(securityAnalysisService.getSecurityAnalysisParameters(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }

    @Test
    void testGetDefaultLimitReductions() {
        String defaultLimitReductions = "[]";
        when(securityAnalysisRestService.getDefaultLimitReductions()).thenReturn(defaultLimitReductions);

        assertThat(securityAnalysisService.getDefaultLimitReductions()).isEqualTo(defaultLimitReductions);
    }

    @Test
    void testUpdateSecurityAnalysisParameters() {
        securityAnalysisService.updateSecurityAnalysisParameters(PARAMETERS_UUID, PARAMETERS);

        verify(securityAnalysisRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }
}
