/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.pccmin;

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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PccMinServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"pcc-min\"}";

    @Mock
    private StudyRepository studyRepository;
    @Mock
    private ComputationParametersService computationParametersService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private RootNetworkNodeInfoService rootNetworkNodeInfoService;
    @Mock
    private PccMinRestService pccMinRestService;
    @Mock
    private NetworkModificationTreeService networkModificationTreeService;
    @Mock
    private UserAdminService userAdminService;
    @Mock
    private RootNetworkService rootNetworkService;

    private PccMinService pccMinService;

    @BeforeEach
    void setUp() {
        pccMinService = new PccMinService(studyRepository, computationParametersService, notificationService, rootNetworkNodeInfoService,
            pccMinRestService, networkModificationTreeService, userAdminService, new ObjectMapper(), rootNetworkService);
    }

    @Test
    void testGetPccMinParametersByUuid() {
        when(pccMinRestService.getParameters(PARAMETERS_UUID)).thenReturn(PARAMETERS);

        assertThat(pccMinService.getPccMinParametersByUuid(PARAMETERS_UUID)).isEqualTo(PARAMETERS);
    }
}
