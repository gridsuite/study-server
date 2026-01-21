/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.removeRequestForStub;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili@rte-france.com>
 */
public class ReportServerStubs {
    private final WireMockServer wireMock;

    public ReportServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubDeleteReports() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
                .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyDeleteReports(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.deleteRequestedFor(WireMock.urlPathEqualTo("/v1/reports"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

}
