/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.shortcircuit;

import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.gridsuite.study.server.service.UserAdminService;
import org.gridsuite.study.server.service.asymmetricalload.AsymmetricalLoadService;
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
class ShortCircuitServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"short-circuit\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private ShortCircuitRestService shortCircuitRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private AsymmetricalLoadService asymmetricalLoadService;

    private ShortCircuitService shortCircuitService;

    @BeforeEach
    void setUp() {
        shortCircuitService = new ShortCircuitService(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService,
            shortCircuitRestService, networkModificationTreeService, userAdminService, rootNetworkService, asymmetricalLoadService);
    }

    @Test
    void testGetParameters() {
        when(shortCircuitRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(shortCircuitService.getParameters(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }

    @Test
    void testUpdateParameters() {
        shortCircuitService.updateParameters(PARAMETERS_UUID, PARAMETERS);

        verify(shortCircuitRestService).updateParameters(PARAMETERS_UUID, PARAMETERS);
    }

    @Test
    void testDownloadDebugFile() {
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource(PARAMETERS.getBytes()));
        when(shortCircuitRestService.downloadDebugFile(RESULT_UUID)).thenReturn(response);

        assertThat(shortCircuitService.downloadDebugFile(RESULT_UUID)).isEqualTo(response);
    }

    @Test
    void testGetSpecificParameters() {
        when(shortCircuitRestService.getSpecificParameters()).thenReturn(PARAMETERS);

        assertThat(shortCircuitService.getSpecificParameters()).isEqualTo(PARAMETERS);
    }
}
