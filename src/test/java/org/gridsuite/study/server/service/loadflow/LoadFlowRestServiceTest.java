/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.loadflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.LoadFlowParametersJsonModule;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.LOADFLOW_API_VERSION;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoadFlowRestServiceTest {

    private static final String BASE_URI = "http://loadflow-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new LoadFlowParametersJsonModule());

    private LoadFlowRestService loadFlowRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("loadflow-server")).thenReturn(BASE_URI);
        loadFlowRestService = new LoadFlowRestService(remoteServicesProperties, objectMapper, restTemplate);
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(restTemplate.getForObject(BASE_URI + "/" + LOADFLOW_API_VERSION + "/providers", String.class)).thenReturn(providers);

        assertThat(loadFlowRestService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetSpecificParameters() {
        String specificParameters = "{\"provider\":\"OpenLoadFlow\"}";
        when(restTemplate.getForObject(BASE_URI + "/" + LOADFLOW_API_VERSION + "/specific-parameters", String.class)).thenReturn(specificParameters);

        assertThat(loadFlowRestService.getSpecificParameters()).isEqualTo(specificParameters);
    }

    @Test
    void testGetDefaultLimitReductions() {
        String defaultLimitReductions = "[]";
        when(restTemplate.getForObject(BASE_URI + "/" + LOADFLOW_API_VERSION + "/parameters/default-limit-reductions", String.class)).thenReturn(defaultLimitReductions);

        assertThat(loadFlowRestService.getDefaultLimitReductions()).isEqualTo(defaultLimitReductions);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        when(restTemplate.getForObject(BASE_URI + "/" + LOADFLOW_API_VERSION + "/parameters/" + parameterUuid, String.class)).thenReturn(parameters);

        assertThat(loadFlowRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }

    @Test
    void testGetCommonParameters() throws Exception {
        UUID parameterUuid = UUID.randomUUID();
        LoadFlowParameters commonParameters = LoadFlowParameters.load();
        String parameters = objectMapper.writeValueAsString(Map.of(
                "provider", "OpenLoadFlow",
                "commonParameters", commonParameters));
        when(restTemplate.getForObject(BASE_URI + "/" + LOADFLOW_API_VERSION + "/parameters/" + parameterUuid, String.class)).thenReturn(parameters);

        assertThat(objectMapper.writeValueAsString(loadFlowRestService.getCommonParameters(parameterUuid)))
                .isEqualTo(objectMapper.writeValueAsString(commonParameters));
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);

        loadFlowRestService.updateParameters(parameterUuid, parameters);

        verify(restTemplate).put(org.mockito.ArgumentMatchers.eq(BASE_URI + "/" + LOADFLOW_API_VERSION + "/parameters/" + parameterUuid), captor.capture());
        assertThat(captor.getValue().getBody()).isEqualTo(parameters);
        assertThat(captor.getValue().getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }
}
