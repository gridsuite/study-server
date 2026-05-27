/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * @author Caroline JEANDAT <caroline.jeandat at rte-france.com>
 */
public class SensitivityAnalysisServerStubs {
    private final WireMockServer wireMock;

    public SensitivityAnalysisServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubGetElementIds(String responseBody) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/parameters/.*/contingency-lists-and-filters"))
            .willReturn(WireMock.aResponse()
                .withStatus(HttpStatus.OK.value())
                .withHeader("Content-Type", "application/json")
                .withBody(responseBody)));
    }

    public void verifyGetElementIds() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/parameters/.*/contingency-lists-and-filters", true,
            Map.of(), 1);
    }
}
