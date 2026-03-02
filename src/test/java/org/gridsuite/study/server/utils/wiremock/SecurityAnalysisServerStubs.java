/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import java.util.Map;

/**
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
public class SecurityAnalysisServerStubs {
    private final WireMockServer wireMock;

    public SecurityAnalysisServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void verifyGetResultLimitTypes(String resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/limit-types", Map.of());
    }

    public void verifyGetNResult(String resultUuid, Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/n-result", queryParams);
    }

    public void verifyGetNmkContingenciesResult(String resultUuid, Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/nmk-contingencies-result/paged", queryParams);
    }

    public void verifyGetNmkConstraintsResult(String resultUuid, Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/nmk-constraints-result/paged", queryParams);
    }

    public void stubContingencyListCount(String responseJson, Map<String, StringValuePattern> queryParams) {
        MappingBuilder builder = WireMock.get(WireMock.urlPathEqualTo("/v1/contingency-lists/count"));
        queryParams.forEach(builder::withQueryParam);
        wireMock.stubFor(builder.willReturn(WireMock.okJson(responseJson)));
    }

    public void verifyContingencyListCount(Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/contingency-lists/count", queryParams);
    }
}
