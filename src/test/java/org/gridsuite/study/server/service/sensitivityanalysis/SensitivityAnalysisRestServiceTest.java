/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.sensitivityanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.SENSITIVITY_ANALYSIS_API_VERSION;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitivityAnalysisRestServiceTest {

    private static final String BASE_URI = "http://sensitivity-analysis-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    @Mock
    private RestTemplate restTemplate;

    private SensitivityAnalysisRestService sensitivityAnalysisRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("sensitivity-analysis-server")).thenReturn(BASE_URI);
        sensitivityAnalysisRestService = new SensitivityAnalysisRestService(remoteServicesProperties, restTemplate, new ObjectMapper());
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(restTemplate.getForObject(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/providers", String.class)).thenReturn(providers);

        assertThat(sensitivityAnalysisRestService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        when(restTemplate.getForObject(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid, String.class)).thenReturn(parameters);

        assertThat(sensitivityAnalysisRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        sensitivityAnalysisRestService.updateParameters(parameterUuid, parameters);

        verify(restTemplate).put(eq(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid), captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo(parameters);
        assertThat(captor.getValue().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
