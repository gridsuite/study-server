/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}

 * Unit tests for {@link UserAdminService}, focusing on the operation quota REST calls
 * (get max/current quota, start/end operation with quota) introduced alongside {@link QuotaType}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class UserAdminServiceTest {
    private static final String USER_ID = "userId";

    @Autowired
    private UserAdminService userAdminService;

    @MockitoBean
    private RestTemplate restTemplate;

    @Test
    void testGetUserMaxQuota() {
        Map<QuotaType, Integer> expectedQuotas = Map.of(QuotaType.LOAD_FLOW, 5, QuotaType.BUILD, 10);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                Mockito.<ParameterizedTypeReference<Map<QuotaType, Integer>>>any()))
                .thenReturn(ResponseEntity.ok(expectedQuotas));

        Map<QuotaType, Integer> result = userAdminService.getUserMaxQuota(USER_ID);

        assertEquals(expectedQuotas, result);
        verify(restTemplate, times(1)).exchange(
                matches(".*/users/" + USER_ID + "/quota/max$"),
                eq(HttpMethod.GET),
                isNull(),
                Mockito.<ParameterizedTypeReference<Map<QuotaType, Integer>>>any());
    }

    @Test
    void testGetUserCurrentQuota() {
        Map<QuotaType, Integer> expectedQuotas = Map.of(QuotaType.SHORT_CIRCUIT, 1);
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                isNull(),
                Mockito.<ParameterizedTypeReference<Map<QuotaType, Integer>>>any()))
                .thenReturn(ResponseEntity.ok(expectedQuotas));

        Map<QuotaType, Integer> result = userAdminService.getUserCurrentQuota(USER_ID);

        assertEquals(expectedQuotas, result);
        verify(restTemplate, times(1)).exchange(
                matches(".*/users/" + USER_ID + "/quota/current$"),
                eq(HttpMethod.GET),
                isNull(),
                Mockito.<ParameterizedTypeReference<Map<QuotaType, Integer>>>any());
    }

    @Test
    void testStartOperationWithQuota() {
        UUID operationId = UUID.randomUUID();

        userAdminService.startOperationWithQuota(USER_ID, QuotaType.SHORT_CIRCUIT, operationId);

        verify(restTemplate, times(1)).postForEntity(
                matches(".*/users/" + USER_ID + "/quota/SHORT_CIRCUIT/" + operationId + "/start$"),
                isNull(),
                eq(Void.class));
    }

    @Test
    void testEndOperationWithQuota() {
        UUID operationId = UUID.randomUUID();

        userAdminService.endOperationWithQuota(USER_ID, QuotaType.SHORT_CIRCUIT, operationId);

        verify(restTemplate, times(1)).postForEntity(
                matches(".*/users/" + USER_ID + "/quota/SHORT_CIRCUIT/" + operationId + "/end$"),
                isNull(),
                eq(Void.class));
    }
}
