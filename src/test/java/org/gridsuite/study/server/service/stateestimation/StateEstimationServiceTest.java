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
import static org.gridsuite.study.server.dto.ComputationType.STATE_ESTIMATION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateEstimationServiceTest {

    private static final UUID RESULT_UUID = UUID.randomUUID();
    private static final UUID NODE_UUID = UUID.randomUUID();
    private static final UUID ROOT_NETWORK_UUID = UUID.randomUUID();
    private static final UUID NETWORK_UUID = UUID.randomUUID();
    private static final String VARIANT_ID = "node_variant";
    private static final String GLOBAL_FILTERS = "{\"countryCode\":[\"FR\"]}";

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

    @Test
    void measurementsUseStoredResultNetworkAndNodeVariant() {
        when(rootNetworkNodeInfoService.getComputationResultUuid(NODE_UUID, ROOT_NETWORK_UUID, STATE_ESTIMATION)).thenReturn(RESULT_UUID);
        when(rootNetworkService.getNetworkUuid(ROOT_NETWORK_UUID)).thenReturn(NETWORK_UUID);
        when(networkModificationTreeService.getVariantId(NODE_UUID, ROOT_NETWORK_UUID)).thenReturn(VARIANT_ID);
        when(stateEstimationRestService.getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, VARIANT_ID, GLOBAL_FILTERS)).thenReturn("[]");

        assertThat(stateEstimationService.getStateEstimationMeasurements(NODE_UUID, ROOT_NETWORK_UUID, GLOBAL_FILTERS)).isEqualTo("[]");

        verify(stateEstimationRestService).getStateEstimationMeasurements(RESULT_UUID, NETWORK_UUID, VARIANT_ID, GLOBAL_FILTERS);
        verifyNoInteractions(notificationService, studyRepository, computationParametersService);
    }

    @Test
    void measurementsWithoutStoredResultDoNothingElse() {
        when(rootNetworkNodeInfoService.getComputationResultUuid(NODE_UUID, ROOT_NETWORK_UUID, STATE_ESTIMATION)).thenReturn(null);

        assertThat(stateEstimationService.getStateEstimationMeasurements(NODE_UUID, ROOT_NETWORK_UUID, GLOBAL_FILTERS)).isNull();

        verifyNoInteractions(rootNetworkService, networkModificationTreeService, stateEstimationRestService, notificationService, studyRepository);
    }
}
