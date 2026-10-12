/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkConversionServiceTest {

    private static final String NETWORK_CONVERSION_SERVER_URI = "http://network-conversion-server";

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    @Test
    void testGetCaseImportParameters() {
        UUID caseUuid = UUID.randomUUID();
        String response = "{\"format\":\"UCTE\"}";
        NetworkConversionService networkConversionService = new NetworkConversionService(NETWORK_CONVERSION_SERVER_URI, new ObjectMapper(), restClient);
        String expectedUrl = NETWORK_CONVERSION_SERVER_URI + "/v1/cases/" + caseUuid + "/import-parameters";
        when(restClient
            .get()
            .uri(expectedUrl)
            .retrieve()
            .body(String.class)).thenReturn(response);

        assertThat(networkConversionService.getCaseImportParameters(caseUuid)).isEqualTo(response);
    }
}
