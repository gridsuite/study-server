/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.gridsuite.study.server.utils.TestUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public class ShortcircuitServerStubs {
    private final WireMockServer wireMock;

    public ShortcircuitServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubGetShortcircuitParameters(String parametersUuid) throws IOException {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .willReturn(WireMock.ok().withBody(TestUtils.resourceToString("/short-circuit-parameters.json")).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }
}
