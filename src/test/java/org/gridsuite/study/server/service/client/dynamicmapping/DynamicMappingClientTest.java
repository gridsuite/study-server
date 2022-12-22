/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import org.gridsuite.study.server.dto.dynamicmapping.MappingInfos;
import org.gridsuite.study.server.service.client.AbstractRestClientTest;
import org.gridsuite.study.server.service.client.UrlUtil;
import org.gridsuite.study.server.service.client.dynamicmapping.impl.DynamicMappingClientImpl;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
public class DynamicMappingClientTest extends AbstractRestClientTest {
    private static String[] mappingNames = {"mapping01", "mapping02"};
    private List<MappingInfos> mappings = Arrays.asList(new MappingInfos(mappingNames[0]),
                                                        new MappingInfos(mappingNames[1]));

    private static final int DYNAMIC_MAPPING_PORT = 5036;

    private DynamicMappingClient dynamicMappingClient;

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
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
                String path = Objects.requireNonNull(recordedRequest.getPath());
                String endPointUrl = UrlUtil.buildEndPointUrl(DynamicMappingClient.API_VERSION, DynamicMappingClient.DYNAMIC_MAPPING_END_POINT_MAPPING);
                String method = recordedRequest.getMethod();

                MockResponse response = new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());

                // mappings/
                if ("GET".equals(method)
                        && path.matches(endPointUrl + ".*")) {
                    // makes a list of 2 elements
                    try {
                        response = new MockResponse()
                                .setResponseCode(HttpStatus.OK.value())
                                .addHeader("Content-Type", "application/json; charset=utf-8")
                                .setBody(objectMapper.writeValueAsString(mappings));
                    } catch (JsonProcessingException e) {
                        getLogger().info("Cannot convert to json : ", e);
                        return new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value());
                    }
                }
                return response;
            }
        };
    }

    @Override
    public void setUp() {
        super.setUp();

        // config client
        dynamicMappingClient = new DynamicMappingClientImpl(initMockWebServer(DYNAMIC_MAPPING_PORT), restTemplate);
    }

    @Test
    public void testGetAllMappings() throws JsonProcessingException {
        List<MappingInfos> allMappings = dynamicMappingClient.getAllMappings();

        // --- check result --- //
        // must contain two elements
        getLogger().info("Size of result mappings = " + allMappings.size());
        assertEquals(2, allMappings.size());
        getLogger().info("Result mappings = " + objectMapper.writeValueAsString(allMappings));
        // first element's name must be mappingNames[0]
        assertEquals(mappingNames[0], allMappings.get(0).getName());
        // first element's name must be mappingNames[1]
        assertEquals(mappingNames[1], allMappings.get(1).getName());
    }
}
