/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.verifyDeleteRequest;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.verifyPutRequest;

/**
 * @author Maissa Souissi <maissa.souissi@rte-france.com>
 */
public class ReportServerStubs {
    private final WireMockServer wireMock;

    public ReportServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubSendReport() {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/reports/.*"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifySendReport(UUID stubUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/reports/.*", true, Map.of(), null);
    }

    public UUID stubDeleteReport() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteReport(UUID stubUuid) {
        verifyDeleteRequest(wireMock, stubUuid, "/v1/reports", false, Map.of());
    }
}
