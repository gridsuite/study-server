/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.pccmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.PCC_MIN_API_VERSION;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PccMinRestServiceTest {

    private static final String BASE_URI = "http://pcc-min-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    @Mock
    private RestTemplate restTemplate;

    private PccMinRestService pccMinRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("pcc-min-server")).thenReturn(BASE_URI);
        pccMinRestService = new PccMinRestService(remoteServicesProperties, new ObjectMapper(), restTemplate);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        String parameters = "{\"provider\":\"pcc-min\"}";
        when(restTemplate.getForObject(BASE_URI + "/" + PCC_MIN_API_VERSION + "/parameters/" + parameterUuid, String.class)).thenReturn(parameters);

        assertThat(pccMinRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }
}
