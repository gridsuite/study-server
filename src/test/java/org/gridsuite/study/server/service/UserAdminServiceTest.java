/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.QuotaState;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.QuotaConsumptionRepository;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 *
 * Unit tests for {@link UserAdminService}, focusing on the atomic quota check-and-consume REST call and the
 * local mapping between a computation's result UUID and the quotaId consumed for it, introduced alongside
 * {@link QuotaType}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class UserAdminServiceTest {
    private static final String USER_ID = "userId";

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private QuotaConsumptionRepository quotaConsumptionRepository;

    @MockitoBean(answers = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @AfterEach
    void cleanDB() {
        quotaConsumptionRepository.deleteAll();
    }

    @Test
    void testGetUserQuotaState() {
        Map<QuotaType, QuotaState> expectedState = Map.of(
                QuotaType.LOAD_FLOW, new QuotaState(1, 5),
                QuotaType.BUILD, new QuotaState(0, 10));
        when(restClient.get().uri(anyString()).retrieve().body(
                Mockito.<ParameterizedTypeReference<Map<QuotaType, QuotaState>>>any()))
                .thenReturn(expectedState);
        Mockito.clearInvocations(restClient.get());

        Map<QuotaType, QuotaState> result = userAdminService.getUserQuotaState(USER_ID);

        assertEquals(expectedState, result);
        verify(restClient.get(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/state$"));
    }

    @Test
    void testConsumeQuotaReturnsQuotaId() {
        UUID quotaId = UUID.randomUUID();
        when(restClient.post().uri(anyString()).retrieve().body(eq(UUID.class))).thenReturn(quotaId);
        Mockito.clearInvocations(restClient.post());

        UUID result = userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT);

        assertEquals(quotaId, result);
        verify(restClient.post(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/SHORT_CIRCUIT/consume$"));
    }

    @Test
    void testConsumeQuotaThrowsStudyExceptionWhenQuotaExhausted() {
        when(restClient.post().uri(anyString()).retrieve().body(eq(UUID.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Bad Request", null, null, null));

        assertThrows(StudyException.class, () -> userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT));
    }

    @Test
    void testReleaseQuotaIdCallsReleaseEndpoint() {
        UUID quotaId = UUID.randomUUID();

        userAdminService.releaseQuotaId(USER_ID, quotaId);

        verify(restClient.post(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/" + quotaId + "/release$"));
    }

    @Test
    void testRegisterThenReleaseQuotaUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findById(resultUuid).isPresent());

        userAdminService.releaseQuota(USER_ID, resultUuid);

        verify(restClient.post(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/" + quotaId + "/release$"));
        assertTrue(quotaConsumptionRepository.findById(resultUuid).isEmpty());
    }

    @Test
    void testReleaseQuotaIsNoOpWhenNoMappingRegistered() {
        userAdminService.releaseQuota(USER_ID, UUID.randomUUID());

        verify(restClient, times(0)).post();
    }

    @Test
    void testRegisterThenReleaseFailedQuotaIdUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isPresent());

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);

        verify(restClient.post(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/" + quotaId + "/release$"));
        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isEmpty());
    }

    @Test
    void testReleaseFailedQuotaIdCallsReleaseEndpointWhenNoMappingRegistered() {
        UUID quotaId = UUID.randomUUID();

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);

        verify(restClient.post(), times(1)).uri(matches(".*/users/" + USER_ID + "/quota/" + quotaId + "/release$"));
    }
}
