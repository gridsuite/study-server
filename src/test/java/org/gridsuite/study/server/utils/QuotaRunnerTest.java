/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.service.StudyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotaRunnerTest {

    private static final String USER_ID = "userId";
    private static final ComputationType COMPUTATION_TYPE = ComputationType.LOAD_FLOW;

    @Mock
    private StudyService studyService;

    private UUID quotaId;

    @BeforeEach
    void setUp() {
        quotaId = UUID.randomUUID();
    }

    @Test
    void testComputationSucceeds() {
        when(studyService.consumeQuota(COMPUTATION_TYPE, USER_ID)).thenReturn(quotaId);

        QuotaRunner.runComputationWithQuota(studyService, COMPUTATION_TYPE, USER_ID, receivedQuotaId ->
            assertThat(receivedQuotaId).isEqualTo(quotaId));

        verify(studyService).consumeQuota(COMPUTATION_TYPE, USER_ID);
        verify(studyService, never()).releaseQuotaOnFailure(any(), any());
    }

    @Test
    void testComputationFailsReleasesQuota() {
        when(studyService.consumeQuota(COMPUTATION_TYPE, USER_ID)).thenReturn(quotaId);
        RuntimeException expected = new RuntimeException("computation failed");

        assertThatThrownBy(() -> QuotaRunner.runComputationWithQuota(studyService, COMPUTATION_TYPE, USER_ID, receivedQuotaId -> {
            throw expected;
        })).isSameAs(expected);

        verify(studyService).consumeQuota(COMPUTATION_TYPE, USER_ID);
        verify(studyService).releaseQuotaOnFailure(USER_ID, quotaId);
    }

    @Test
    void testQuotaConsumptionFailsPropagatesExceptionWithoutRunningComputation() {
        RuntimeException expected = new RuntimeException("quota exceeded");
        when(studyService.consumeQuota(COMPUTATION_TYPE, USER_ID)).thenThrow(expected);

        assertThatThrownBy(() -> QuotaRunner.runComputationWithQuota(studyService, COMPUTATION_TYPE, USER_ID, receivedQuotaId -> {
            throw new AssertionError("computation should not run when quota consumption fails");
        })).isSameAs(expected);

        verify(studyService).consumeQuota(COMPUTATION_TYPE, USER_ID);
        verify(studyService, never()).releaseQuotaOnFailure(any(), any());
    }

    @Test
    void testNullQuotaIdWhenQuotaCheckDisabled() {
        when(studyService.consumeQuota(COMPUTATION_TYPE, USER_ID)).thenReturn(null);

        QuotaRunner.runComputationWithQuota(studyService, COMPUTATION_TYPE, USER_ID, receivedQuotaId ->
            assertThat(receivedQuotaId).isNull());

        verify(studyService).consumeQuota(COMPUTATION_TYPE, USER_ID);
        verify(studyService, never()).releaseQuotaOnFailure(any(), any());
    }

    @Test
    void testUtilityClassCannotBeInstantiated() {
        assertThatThrownBy(() -> {
            var constructor = QuotaRunner.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            try {
                constructor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(UnsupportedOperationException.class);
    }
}
