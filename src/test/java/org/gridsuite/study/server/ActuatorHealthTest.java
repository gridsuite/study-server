/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.github.tomakehurst.wiremock.WireMockServer;
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
    private RemoteServicesProperties remoteServicesProperties;

    @Before
    public void setup() throws IOException {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockUtils = new WireMockUtils(wireMockServer);
        wireMockServer.start();

        remoteServicesProperties.getServices().forEach(s -> s.setBaseUri(wireMockServer.baseUrl()));
    }

    @Test
    public void testActuatorHealthUp() throws Exception {
        // any optional service will be mocked as UP
        UUID stubUuid = wireMockUtils.stubActuatorHealthGet("{\"status\":\"UP\"}");

        // select 3 services to be optional
        List<String> optionalServices = List.of("loadflow-server", "security-analysis-server", "voltage-init-server");
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        MvcResult mvcResult = mockMvc.perform(get("/v1/optional-services"))
                .andExpect(status().isOk())
                .andReturn();
        String response = mvcResult.getResponse().getContentAsString();
        // all services are supposed to be Up
        assertEquals("[{\"name\":\"loadflow-server\",\"status\":\"UP\"},{\"name\":\"security-analysis-server\",\"status\":\"UP\"},{\"name\":\"voltage-init-server\",\"status\":\"UP\"}]", response);
        wireMockUtils.verifyActuatorHealth(stubUuid, optionalServices.size());
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

        // select 2 services to be optional
        List<String> optionalServices = List.of("sensitivity-analysis-server", "shortcircuit-server");
        remoteServicesProperties.getServices().forEach(s -> s.setOptional(optionalServices.contains(s.getName())));

        MvcResult mvcResult = mockMvc.perform(get("/v1/optional-services"))
                .andExpect(status().isOk())
                .andReturn();
        String resultAsString = mvcResult.getResponse().getContentAsString();
        // all services are supposed to be Down
        assertEquals("[{\"name\":\"sensitivity-analysis-server\",\"status\":\"DOWN\"},{\"name\":\"shortcircuit-server\",\"status\":\"DOWN\"}]", resultAsString);

        wireMockUtils.verifyActuatorHealth(stubUuid, optionalServices.size());
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
