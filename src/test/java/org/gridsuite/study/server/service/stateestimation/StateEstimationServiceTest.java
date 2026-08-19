/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.stateestimation;

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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateEstimationServiceTest {

    private static final UUID RESULT_UUID = UUID.randomUUID();

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private RootNetworkService rootNetworkService;
    @Mock
    private StateEstimationRestService stateEstimationRestService;
    @Mock
    private UserAdminService userAdminService;

    private StateEstimationService stateEstimationService;

    @BeforeEach
    void setUp() {
        stateEstimationService = new StateEstimationService(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService,
            networkModificationTreeService, rootNetworkService, stateEstimationRestService, userAdminService, new ObjectMapper());
    }

    @Test
    void testDownloadDebugFile() {
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource("debug".getBytes()));
        when(stateEstimationRestService.downloadDebugFile(RESULT_UUID)).thenReturn(response);

        assertThat(stateEstimationService.downloadDebugFile(RESULT_UUID)).isEqualTo(response);
    }
}
