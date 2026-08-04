/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com). This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import jakarta.servlet.http.HttpServletRequest;
import org.gridsuite.study.server.controller.DynamicSimulationController;
import org.gridsuite.study.server.service.proxy.EntryPointAuthorization;
import org.gridsuite.study.server.service.proxy.TransparentProxyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DynamicSimulationControllerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String JSON = "{\"name\":\"value\"}";
    private static final String BODY = "{\"enabled\":true}";
    private static final String DOWNSTREAM_URI = "http://dynamic-simulation-server/";

    @Mock
    private EntryPointAuthorization authorization;

    private MockRestServiceServer server;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();

        RemoteServicesProperties properties = new RemoteServicesProperties();
        properties.setServices(List.of(new RemoteServicesProperties.Service(
            "dynamic-simulation-server", DOWNSTREAM_URI, false)));

        TransparentProxyService proxyService = new TransparentProxyService(properties, restTemplate);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new DynamicSimulationController(proxyService, List.of(authorization))).build();
    }

    @Test
    void forwardsRequestAndResponseWithoutBindingEndpointDetails() throws Exception {
        server.expect(requestTo(DOWNSTREAM_URI + "v1/dynamic-simulation/parameters/" + ID + "?mode=async"))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("userId", "alice"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.header("X-Request-Id", "request-id"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().bytes(BODY.getBytes()))
            .andRespond(withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Downstream", "yes")
                .body(JSON));

        mockMvc.perform(put("/v1/dynamic-simulation/parameters/{parameterUuid}", ID)
                .queryParam("mode", "async")
                .header("userId", "alice")
                .header("X-Request-Id", "request-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
            .andExpect(status().isAccepted())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(header().string("X-Downstream", "yes"))
            .andExpect(content().json(JSON));

        verify(authorization).authorize(any(HttpServletRequest.class));
        server.verify();
    }

    @Test
    void forwardsDownstreamErrorStatusAndBody() throws Exception {
        server.expect(requestTo(DOWNSTREAM_URI + "v1/dynamic-simulation/providers"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(JSON));

        mockMvc.perform(get("/v1/dynamic-simulation/providers"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().json(JSON));

        verify(authorization).authorize(any(HttpServletRequest.class));
        server.verify();
    }
}
