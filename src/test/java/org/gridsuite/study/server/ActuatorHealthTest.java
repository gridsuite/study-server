/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.service.ActuatorHealthService;
import org.gridsuite.study.server.service.RemoteServicesProperties;
import org.gridsuite.study.server.utils.TestUtils;
import org.gridsuite.study.server.utils.WireMockUtils;
import org.gridsuite.study.server.utils.elasticsearch.DisableElasticsearch;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */

@RunWith(SpringRunner.class)
@AutoConfigureMockMvc
@DisableElasticsearch
@SpringBootTest
public class ActuatorHealthTest {

    private WireMockServer wireMockServer;
    private WireMockUtils wireMockUtils;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ActuatorHealthService actuatorHealthService;
    @Autowired
    private RemoteServicesProperties remoteServicesProperties;

    @Before
    public void setup() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);
        wireMockServer.start();

        remoteServicesProperties.getServices().stream().forEach(s -> s.setBaseUri(wireMockServer.baseUrl()));
    }

    @Test
    public void testActuatorHealthUp() throws Exception {
        UUID stubUuid = wireMockUtils.stubActuatorHealthGet("{\"status\":\"UP\"}");

        MvcResult mvcResult = mockMvc.perform(get("/v1/up-optional-services"))
                .andExpect(status().isOk())
                .andReturn();
        String response = mvcResult.getResponse().getContentAsString();

        // We receive all server names in the response, but the order is random (N concurrent calls on isServerUp).
        // So compare the content no matter the order.
        List<String> names = objectMapper.readValue(response, new TypeReference<>() { });
        List<String> expectedNames = remoteServicesProperties.getServices().stream()
            .filter(RemoteServicesProperties.Service::getOptional)
            .map(RemoteServicesProperties.Service::getName)
            .toList();
        assertTrue(CollectionUtils.isEqualCollection(expectedNames, names));

        wireMockUtils.verifyActuatorHealth(stubUuid, expectedNames.size());
    }

    @Test
    public void testActuatorHealthDown() throws Exception {
        getActuatorHealthAllDown("{\"status\":\"DOWN\"}");
    }

    @Test
    public void testActuatorHealthMalformedJson() throws Exception {
        getActuatorHealthAllDown("{\"malformed json\":");
    }

    @Test
    public void testActuatorHealthUnexpectedJson() throws Exception {
        getActuatorHealthAllDown("{\"unexpected_property\":\"UP\"}");
    }

    private void getActuatorHealthAllDown(String jsonContent) throws Exception {
        UUID stubUuid = wireMockUtils.stubActuatorHealthGet(jsonContent);

        MvcResult mvcResult = mockMvc.perform(get("/v1/up-optional-services"))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();

        // no up server expected
        assertEquals("[]", resultAsString);

        wireMockUtils.verifyActuatorHealth(stubUuid, remoteServicesProperties.getServices().stream().filter(RemoteServicesProperties.Service::getOptional).toList().size());
    }

    @After
    public void tearDown() {
        try {
            TestUtils.assertWiremockServerRequestsEmptyThenShutdown(wireMockServer);
        } catch (IOException e) {
            // Ignoring
        }
    }
}
