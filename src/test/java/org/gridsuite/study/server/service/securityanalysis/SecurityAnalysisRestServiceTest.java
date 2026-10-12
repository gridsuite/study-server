/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.securityanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.SECURITY_ANALYSIS_API_VERSION;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class SecurityAnalysisRestServiceTest {

    private static final String BASE_URI = "http://security-analysis-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    private MockRestServiceServer server;

    private SecurityAnalysisRestService securityAnalysisRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("security-analysis-server")).thenReturn(BASE_URI);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        securityAnalysisRestService = new SecurityAnalysisRestService(remoteServicesProperties, new ObjectMapper(), builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        server.expect(requestTo(BASE_URI + "/" + SECURITY_ANALYSIS_API_VERSION + "/providers"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(providers, MediaType.APPLICATION_JSON));

        assertThat(securityAnalysisRestService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetDefaultLimitReductions() {
        String defaultLimitReductions = "[]";
        server.expect(requestTo(BASE_URI + "/" + SECURITY_ANALYSIS_API_VERSION + "/parameters/default-limit-reductions"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(defaultLimitReductions, MediaType.APPLICATION_JSON));

        assertThat(securityAnalysisRestService.getDefaultLimitReductions()).isEqualTo(defaultLimitReductions);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        server.expect(requestTo(BASE_URI + "/" + SECURITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(parameters, MediaType.APPLICATION_JSON));

        assertThat(securityAnalysisRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        server.expect(requestTo(BASE_URI + "/" + SECURITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(parameters))
            .andRespond(withNoContent());

        securityAnalysisRestService.updateParameters(parameterUuid, parameters);
    }

    @Test
    void testUpdateParametersWithNullBody() {
        UUID parameterUuid = UUID.randomUUID();
        server.expect(requestTo(BASE_URI + "/" + SECURITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string(""))
            .andRespond(withNoContent());

        securityAnalysisRestService.updateParameters(parameterUuid, null);
    }
}
