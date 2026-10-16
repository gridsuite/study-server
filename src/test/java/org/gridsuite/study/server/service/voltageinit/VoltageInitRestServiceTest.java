/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.voltageinit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.VOLTAGE_INIT_API_VERSION;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoltageInitRestServiceTest {

    private static final String BASE_URI = "http://voltage-init-server";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;
    @Mock
    private RestTemplate restTemplate;

    private VoltageInitRestService voltageInitRestService;

    @BeforeEach
    void setUp() {
        when(remoteServicesProperties.getServiceUri("voltage-init-server")).thenReturn(BASE_URI);
        voltageInitRestService = new VoltageInitRestService(remoteServicesProperties, restTemplate, new ObjectMapper());
    }

    @Test
    void testDownloadDebugFile() {
        UUID resultUuid = UUID.randomUUID();
        ResponseEntity<Resource> response = ResponseEntity.ok(new ByteArrayResource("debug".getBytes()));
        String url = BASE_URI + "/" + VOLTAGE_INIT_API_VERSION + "/results/" + resultUuid + "/download-debug-file";
        when(restTemplate.exchange(url, HttpMethod.GET, null, Resource.class)).thenReturn(response);

        assertThat(voltageInitRestService.downloadDebugFile(resultUuid)).isEqualTo(response);
    }

    @Test
    void testGetParameters() {
        UUID parameterUuid = UUID.randomUUID();
        VoltageInitParametersInfos parameters = new VoltageInitParametersInfos();
        when(restTemplate.getForObject(BASE_URI + "/" + VOLTAGE_INIT_API_VERSION + "/parameters/" + parameterUuid, VoltageInitParametersInfos.class)).thenReturn(parameters);

        assertThat(voltageInitRestService.getParameters(parameterUuid)).isEqualTo(parameters);
    }
}
