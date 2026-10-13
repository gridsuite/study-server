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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

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
//TODO
class UserAdminServiceTest {
    private static final String USER_ID = "userId";
    private static final String BASE_URI = "http://user-admin-server";

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private QuotaConsumptionRepository quotaConsumptionRepository;

    @TestBean(name = "studyRestClient", methodName = "createRestClient")
    private RestClient restClient;

    static RestClient createRestClient() {
        return Mockito.mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    @BeforeEach
    void configureRestClient() {
        Mockito.reset(restClient);
        userAdminService.setUserAdminServerBaseUri(BASE_URI);
    }

    @AfterEach
    void cleanDB() {
        quotaConsumptionRepository.deleteAll();
    }

    @Test
    void testGetUserQuotaState() throws Exception {
        Map<QuotaType, QuotaState> expectedState = Map.of(
                QuotaType.LOAD_FLOW, new QuotaState(1, 5),
                QuotaType.BUILD, new QuotaState(0, 10));
        String expectedUrl = BASE_URI + "/v1/users/" + USER_ID + "/quota/state";
        var requestSpec = restClient.get().uri(expectedUrl);
        var responseType = new ParameterizedTypeReference<Map<QuotaType, QuotaState>>() { };
        when(requestSpec.retrieve().body(responseType)).thenReturn(expectedState);

        Map<QuotaType, QuotaState> result = userAdminService.getUserQuotaState(USER_ID);

        assertEquals(expectedState, result);
    }

    @Test
    void testConsumeQuotaReturnsQuotaId() {
        UUID quotaId = UUID.randomUUID();
        String expectedUrl = BASE_URI + "/v1/users/" + USER_ID + "/quota/SHORT_CIRCUIT/consume";
        when(restClient.post().uri(expectedUrl).retrieve().body(UUID.class)).thenReturn(quotaId);

        UUID result = userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT);

        assertEquals(quotaId, result);
    }

    @Test
    void testConsumeQuotaThrowsStudyExceptionWhenQuotaExhausted() {
        String expectedUrl = BASE_URI + "/v1/users/" + USER_ID + "/quota/SHORT_CIRCUIT/consume";
        HttpClientErrorException quotaExhausted = HttpClientErrorException.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too Many Requests",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8);
        when(restClient.post().uri(expectedUrl).retrieve().body(UUID.class)).thenThrow(quotaExhausted);

        assertThrows(StudyException.class, () -> userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT));
    }

    @Test
    void testReleaseQuotaIdCallsReleaseEndpoint() {
        UUID quotaId = UUID.randomUUID();
        var requestSpec = expectReleaseQuota(quotaId);

        userAdminService.releaseQuotaId(USER_ID, quotaId);

        verify(requestSpec).retrieve();
    }

    @Test
    void testRegisterThenReleaseQuotaUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();
        var requestSpec = expectReleaseQuota(quotaId);

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findById(resultUuid).isPresent());

        userAdminService.releaseQuota(USER_ID, resultUuid);

        assertTrue(quotaConsumptionRepository.findById(resultUuid).isEmpty());
        verify(requestSpec).retrieve();
    }

    @Test
    void testReleaseQuotaIsNoOpWhenNoMappingRegistered() {
        userAdminService.releaseQuota(USER_ID, UUID.randomUUID());

        verifyNoInteractions(restClient);
    }

    @Test
    void testRegisterThenReleaseFailedQuotaIdUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();
        var requestSpec = expectReleaseQuota(quotaId);

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isPresent());

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);

        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isEmpty());
        verify(requestSpec).retrieve();
    }

    @Test
    void testReleaseFailedQuotaIdCallsReleaseEndpointWhenNoMappingRegistered() {
        UUID quotaId = UUID.randomUUID();
        var requestSpec = expectReleaseQuota(quotaId);

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);

        verify(requestSpec).retrieve();
    }

    private RestClient.RequestBodySpec expectReleaseQuota(UUID quotaId) {
        return restClient.post().uri(BASE_URI + "/v1/users/" + USER_ID + "/quota/" + quotaId + "/release");
    }
}
