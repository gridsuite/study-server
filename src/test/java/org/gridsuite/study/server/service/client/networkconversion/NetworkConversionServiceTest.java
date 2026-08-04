/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkconversion;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.ContextConfigurationWithTestChannel;
import org.gridsuite.study.server.StudyApplication;
import org.gridsuite.study.server.service.NetworkConversionService;
import org.gridsuite.study.server.service.client.AbstractWireMockRestClientTest;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;


@AutoConfigureMockMvc
@SpringBootTest
@DisableElasticsearch
@ContextConfigurationWithTestChannel
class NetworkConversionServiceTest extends AbstractWireMockRestClientTest {
    @MockitoSpyBean
    private NetworkConversionService networkConversionService;

    @BeforeEach
    void setup() {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());

        // start server
        wireMockServer.start();

        // mock base url of network conversion server as one of wire mock server
        Mockito.doAnswer(invocation -> wireMockServer.baseUrl()).when(networkConversionService).getNetworkConversionServerBaseUri();
    }

    @Test
    void testGetCaseImportParameters() {
        UUID caseUuid = UUID.randomUUID();
        String response = "{\"format\":\"iidm\"}";
        String url = "/v1/cases/" + caseUuid + "/import-parameters";
        wireMockServer.stubFor(WireMock.get(WireMock.urlEqualTo(url))
            .willReturn(WireMock.ok().withBody(response).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));

        assertThat(networkConversionService.getCaseImportParameters(caseUuid)).isEqualTo(response);
        wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo(url)));
    }
}
