/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils.wiremock;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Caroline JEANDAT <caroline.jeandat at rte-france.com>
 */
public class SensitivityAnalysisServerStubs {
    private final WireMockServer wireMock;
    private final ObjectMapper objectMapper;

    public SensitivityAnalysisServerStubs(WireMockServer wireMock, ObjectMapper objectMapper) {
        this.wireMock = wireMock;
        this.objectMapper = objectMapper;
    }

    public void stubGetElementIds(List<UUID> elementUuids) throws JsonProcessingException {
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/parameters/.*/contingency-lists-and-filters"))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(elementUuids))));
    }

    public void verifyGetElementIds() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/parameters/.*/contingency-lists-and-filters", true,
            Map.of(), 1);
    }
}
