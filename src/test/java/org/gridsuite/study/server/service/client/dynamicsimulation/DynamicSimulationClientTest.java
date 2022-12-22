/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.service.client.AbstractRestClientTest;
import org.gridsuite.study.server.service.client.dynamicsimulation.impl.DynamicSimulationClientImpl;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;

public class DynamicSimulationClientTest extends AbstractRestClientTest {

    private static final int DYNAMIC_SIMULATION_PORT = 5032;

    private DynamicSimulationClient dynamicSimulationClient;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @NotNull
    protected Dispatcher getDispatcher() {
        return new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) throws InterruptedException {
                MockResponse response = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());

                return response;
            }
        };
    }

    @Override
    public void setUp() {
        super.setUp();

        // config client
        dynamicSimulationClient = new DynamicSimulationClientImpl(initMockWebServer(DYNAMIC_SIMULATION_PORT), restTemplate);
    }

    @Test
    public void testRun() {

    }

    @Test
    public void testGetTimeSeriesResult() {

    }

    @Test
    public void testGetTimeLineResult() {

    }

    @Test
    public void testGetStatus() {

    }

    @Test
    public void testDeleteResult() {

    }
}
