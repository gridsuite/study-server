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
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.SENSITIVITY_ANALYSIS_API_VERSION;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitivityAnalysisRestServiceTest {

    private static final String BASE_URI = "http://sensitivity-analysis-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private SensitivityAnalysisRestService sensitivityAnalysisRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("sensitivity-analysis-server")).thenReturn(BASE_URI);
        sensitivityAnalysisRestService = new SensitivityAnalysisRestService(remoteServicesProperties, restClient, new ObjectMapper());
    }

    @Test
    void testGetProviders() {
        String providers = "[\"OpenLoadFlow\"]";
        when(restClient.get().uri(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/providers").retrieve().body(String.class))
            .thenReturn(providers);

        assertThat(sensitivityAnalysisRestService.getProviders()).isEqualTo(providers);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        when(restClient.get().uri(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid)
            .retrieve().body(String.class)).thenReturn(parameters);

        assertThat(sensitivityAnalysisRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }

    @Test
    void testUpdateParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"OpenLoadFlow\"}";
        var requestSpec = restClient.put().uri(BASE_URI + "/" + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/" + parameterUuid);
        doReturn(requestSpec).when(requestSpec).contentType(MediaType.APPLICATION_JSON);

        sensitivityAnalysisRestService.updateParameters(parameterUuid, parameters);

        verify(requestSpec).contentType(MediaType.APPLICATION_JSON);
        verify(requestSpec).body(parameters);
        verify(requestSpec).retrieve();
    }
}
