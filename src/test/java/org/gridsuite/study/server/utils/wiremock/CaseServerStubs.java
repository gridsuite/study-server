/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
public class CaseServerStubs {
    private final WireMockServer wireMock;
    private static final String CASE_URI = "/v1/cases";

    public CaseServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubCaseExists(String caseUuid, boolean returnedValue) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid + "/exists"))
            .willReturn(WireMock.ok().withBody(returnedValue ? "true" : "false")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCaseExists(UUID stubUuid, String caseUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid + "/exists"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDisableCaseExpiration(String caseUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid + "/disableExpiration"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDisableCaseExpiration(UUID stubUuid, String caseUuid) {
        verifyPutRequest(wireMock, stubUuid, CASE_URI + "/" + caseUuid + "/disableExpiration", false, Map.of(), null);
    }

    public UUID stubDuplicateCase(String caseUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(CASE_URI))
                .withQueryParam("withExpiration", WireMock.matching(".*"))
                .withQueryParam("duplicateFrom", equalTo(caseUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDuplicateCase(UUID stubUuid, String caseUuid) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("withExpiration", WireMock.matching(".*"));
        params.put("duplicateFrom", equalTo(caseUuid));
        verifyPostRequest(wireMock, stubUuid, CASE_URI, params);
    }

    public UUID stubDuplicateCaseWithBody(String caseUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(CASE_URI))
            .withQueryParam("withExpiration", WireMock.matching(".*"))
            .withQueryParam("duplicateFrom", equalTo(caseUuid))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(responseBody))).getId();
    }

    public void verifyDuplicateCase(UUID stubUuid, String caseUuid, String withExpiration) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("withExpiration", equalTo(withExpiration));
        params.put("duplicateFrom", equalTo(caseUuid));
        verifyPostRequest(wireMock, stubUuid, CASE_URI, params);
    }

    public UUID stubDeleteCase(String caseUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo(CASE_URI + "/" + caseUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteCase(UUID stubUuid, String caseUuid) {
        verifyDeleteRequest(wireMock, stubUuid, CASE_URI + "/" + caseUuid, false, Map.of());
    }

    public void stubCreateCase(String caseUuid, String caseKey, String contentType) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(CASE_URI + "/create"))
            .withQueryParam("caseKey", equalTo(caseKey))
            .withQueryParam("contentType", equalTo(contentType))
            .willReturn(WireMock.ok().withBody(caseUuid)));
    }

    public void verifyCreateCase(String caseKey, String contentType) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("caseKey", equalTo(caseKey));
        params.put("contentType", equalTo(contentType));
        WireMockUtilsCriteria.verifyPostRequest(wireMock, CASE_URI + "/create", params, 1);
    }
}
