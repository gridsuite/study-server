/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.pccmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.dto.UserProfileInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.service.*;
import org.gridsuite.study.server.service.common.ComputationParametersService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.dto.ComputationType.PCC_MIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PccMinServiceTest {

    private static final UUID PARAMETERS_UUID = UUID.randomUUID();
    private static final String PARAMETERS = "{\"provider\":\"pcc-min\"}";
    private static final UUID STUDY_UUID = UUID.randomUUID();
    private static final UUID PROFILE_PARAMETERS_UUID = UUID.randomUUID();
    private static final UUID DUPLICATED_PARAMETERS_UUID = UUID.randomUUID();
    private static final String USER_ID = "userId";

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

    @Test
    void testResetPccMinParametersFromUserProfile() {
        StudyEntity studyEntity = StudyEntity.builder().pccMinParametersUuid(PARAMETERS_UUID).build();
        when(userAdminService.getUserProfile(USER_ID)).thenReturn(UserProfileInfos.builder().pccMinParameterId(PROFILE_PARAMETERS_UUID).build());
        when(pccMinRestService.duplicateParameters(PROFILE_PARAMETERS_UUID)).thenReturn(DUPLICATED_PARAMETERS_UUID);

        assertThat(pccMinService.resetParameters(studyEntity, USER_ID)).isFalse();

        assertThat(studyEntity.getPccMinParametersUuid()).isEqualTo(DUPLICATED_PARAMETERS_UUID);
        verify(pccMinRestService).doDeleteComputationParameters(eq(PARAMETERS_UUID), eq(PCC_MIN.getLabel()), any());
        verify(pccMinRestService, never()).resetPccMinParameters(any());
    }

    @Test
    void testResetPccMinParametersToDefault() {
        StudyEntity studyEntity = StudyEntity.builder().pccMinParametersUuid(PARAMETERS_UUID).build();
        when(userAdminService.getUserProfile(USER_ID)).thenReturn(UserProfileInfos.builder().build());

        assertThat(pccMinService.resetParameters(studyEntity, USER_ID)).isFalse();

        assertThat(studyEntity.getPccMinParametersUuid()).isEqualTo(PARAMETERS_UUID);
        verify(pccMinRestService).resetPccMinParameters(PARAMETERS_UUID);
        verify(pccMinRestService, never()).duplicateParameters(any());
        verify(pccMinRestService, never()).updatePccMinParameters(any(), any());
    }

    @Test
    void testResetPccMinParametersDoesNothingWhenStudyHasNone() {
        StudyEntity studyEntity = StudyEntity.builder().build();
        when(userAdminService.getUserProfile(USER_ID)).thenReturn(UserProfileInfos.builder().build());

        assertThat(pccMinService.resetParameters(studyEntity, USER_ID)).isFalse();

        assertThat(studyEntity.getPccMinParametersUuid()).isNull();
        verify(pccMinRestService, never()).resetPccMinParameters(any());
        verify(pccMinRestService, never()).createPccMinParameters(any());
    }

    @Test
    void testResetPccMinParametersFallsBackToDefaultWhenProfileDuplicationFails() {
        StudyEntity studyEntity = StudyEntity.builder().pccMinParametersUuid(PARAMETERS_UUID).build();
        when(userAdminService.getUserProfile(USER_ID)).thenReturn(UserProfileInfos.builder().name("profile").pccMinParameterId(PROFILE_PARAMETERS_UUID).build());
        when(pccMinRestService.duplicateParameters(PROFILE_PARAMETERS_UUID)).thenThrow(new RuntimeException("dangling profile parameters"));

        assertThat(pccMinService.resetParameters(studyEntity, USER_ID)).isTrue();

        assertThat(studyEntity.getPccMinParametersUuid()).isEqualTo(PARAMETERS_UUID);
        verify(pccMinRestService).resetPccMinParameters(PARAMETERS_UUID);
        verify(pccMinRestService, never()).doDeleteComputationParameters(any(), anyString(), any());
    }

    @Test
    void testResetPccMinParametersInvalidatesAndNotifies() {
        StudyEntity studyEntity = StudyEntity.builder().id(STUDY_UUID).pccMinParametersUuid(PARAMETERS_UUID).build();
        when(studyRepository.findById(STUDY_UUID)).thenReturn(Optional.of(studyEntity));
        when(userAdminService.getUserProfile(USER_ID)).thenReturn(UserProfileInfos.builder().build());

        assertThat(pccMinService.resetPccMinParameters(STUDY_UUID, USER_ID)).isFalse();

        verify(rootNetworkNodeInfoService).invalidatePccMinStatusOnAllNodes(STUDY_UUID);
        verify(notificationService).emitStudyChanged(STUDY_UUID, null, null, NotificationService.UPDATE_TYPE_PCC_MIN_STATUS);
        verify(notificationService).emitElementUpdated(STUDY_UUID, USER_ID);
        verify(notificationService).emitComputationParamsChanged(STUDY_UUID, PCC_MIN);
    }

    @Test
    void testResetPccMinParametersOfStudyEntity() {
        StudyEntity studyEntity = StudyEntity.builder().pccMinParametersUuid(PARAMETERS_UUID).build();

        pccMinService.resetPccMinParameters(studyEntity);

        verify(pccMinRestService).resetPccMinParameters(PARAMETERS_UUID);
    }

    @Test
    void testResetPccMinParametersOfStudyEntityWithoutParameters() {
        pccMinService.resetPccMinParameters(StudyEntity.builder().build());

        verify(pccMinRestService, never()).resetPccMinParameters(any());
    }
}
