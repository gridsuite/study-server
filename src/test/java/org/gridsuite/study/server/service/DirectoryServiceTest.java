/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class DirectoryServiceTest {

    private static final String DIRECTORY_SERVER_URI = "http://directory-server";
    private static final String USER_ID = "userId";

    @Mock
    private RemoteServicesProperties remoteServicesProperties;

    private MockRestServiceServer server;

    private DirectoryService directoryService;

    @BeforeEach
    void setup() {
        when(remoteServicesProperties.getServiceUri("directory-server")).thenReturn(DIRECTORY_SERVER_URI);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        directoryService = new DirectoryService(remoteServicesProperties, builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void testGetElements() {
        UUID elementUuid = UUID.randomUUID();
        String response = "[{\"name\":\"element\"}]";
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/elements?ids=" + elementUuid + "&elementTypes=STUDY&strictMode=false";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(HEADER_USER_ID, USER_ID))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThat(directoryService.getElements(List.of(elementUuid), List.of("STUDY"), false, USER_ID)).isEqualTo(response);
    }

    @Test
    void testElementExists() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withSuccess());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isTrue();
    }

    @Test
    void testElementDoesNotExist() {
        UUID directoryUuid = UUID.randomUUID();
        String expectedUrl = DIRECTORY_SERVER_URI + "/v1/directories/" + directoryUuid + "/elements/elementName/types/STUDY";
        server.expect(requestTo(expectedUrl))
            .andExpect(method(HttpMethod.HEAD))
            .andRespond(withNoContent());

        assertThat(directoryService.elementExists(directoryUuid, "elementName", "STUDY")).isFalse();
    }
}
