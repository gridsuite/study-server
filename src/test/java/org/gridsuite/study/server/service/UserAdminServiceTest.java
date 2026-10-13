/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.RestClientConfig;
import org.gridsuite.study.server.dto.QuotaState;
import org.gridsuite.study.server.dto.QuotaType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.QuotaConsumptionRepository;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class UserAdminServiceTest {
    private static final String USER_ID = "userId";
    private static final String BASE_URI = "http://user-admin-server";

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private QuotaConsumptionRepository quotaConsumptionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @TestBean(name = "studyRestClient", methodName = "createRestClient")
    private RestClient restClient;

    private static MockRestServiceServer server;

    static RestClient createRestClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new RestClientConfig().studyRestClient(builder);
    }

    @BeforeEach
    void configureRestClient() {
        server.reset();
        userAdminService.setUserAdminServerBaseUri(BASE_URI);
    }

    @AfterEach
    void cleanDB() {
        quotaConsumptionRepository.deleteAll();
        server.verify();
    }

    @Test
    void testGetUserQuotaState() throws Exception {
        Map<QuotaType, QuotaState> expectedState = Map.of(
                QuotaType.LOAD_FLOW, new QuotaState(1, 5),
                QuotaType.BUILD, new QuotaState(0, 10));
        server.expect(requestTo(BASE_URI + "/v1/users/" + USER_ID + "/quota/state"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(objectMapper.writeValueAsString(expectedState), MediaType.APPLICATION_JSON));

        Map<QuotaType, QuotaState> result = userAdminService.getUserQuotaState(USER_ID);

        assertEquals(expectedState, result);
    }

    @Test
    void testConsumeQuotaReturnsQuotaId() {
        UUID quotaId = UUID.randomUUID();
        server.expect(requestTo(BASE_URI + "/v1/users/" + USER_ID + "/quota/SHORT_CIRCUIT/consume"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("\"" + quotaId + "\"", MediaType.APPLICATION_JSON));

        UUID result = userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT);

        assertEquals(quotaId, result);
    }

    @Test
    void testConsumeQuotaThrowsStudyExceptionWhenQuotaExhausted() {
        server.expect(requestTo(BASE_URI + "/v1/users/" + USER_ID + "/quota/SHORT_CIRCUIT/consume"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(StudyException.class, () -> userAdminService.consumeQuota(USER_ID, QuotaType.SHORT_CIRCUIT));
    }

    @Test
    void testReleaseQuotaIdCallsReleaseEndpoint() {
        UUID quotaId = UUID.randomUUID();
        expectReleaseQuota(quotaId);

        userAdminService.releaseQuotaId(USER_ID, quotaId);
    }

    @Test
    void testRegisterThenReleaseQuotaUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();
        expectReleaseQuota(quotaId);

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findById(resultUuid).isPresent());

        userAdminService.releaseQuota(USER_ID, resultUuid);

        assertTrue(quotaConsumptionRepository.findById(resultUuid).isEmpty());
    }

    @Test
    void testReleaseQuotaIsNoOpWhenNoMappingRegistered() {
        userAdminService.releaseQuota(USER_ID, UUID.randomUUID());
    }

    @Test
    void testRegisterThenReleaseFailedQuotaIdUsesLocalMapping() {
        UUID resultUuid = UUID.randomUUID();
        UUID quotaId = UUID.randomUUID();
        expectReleaseQuota(quotaId);

        userAdminService.registerQuotaConsumption(resultUuid, quotaId);
        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isPresent());

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);

        assertTrue(quotaConsumptionRepository.findByQuotaId(quotaId).isEmpty());
    }

    @Test
    void testReleaseFailedQuotaIdCallsReleaseEndpointWhenNoMappingRegistered() {
        UUID quotaId = UUID.randomUUID();
        expectReleaseQuota(quotaId);

        userAdminService.releaseFailedQuotaId(USER_ID, quotaId);
    }

    private void expectReleaseQuota(UUID quotaId) {
        server.expect(requestTo(BASE_URI + "/v1/users/" + USER_ID + "/quota/" + quotaId + "/release"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent());
    }
}
