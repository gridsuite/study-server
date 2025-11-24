/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
public final class WireMockUtils {
    private WireMockUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams, int nbRequests) {
        verifyPostRequest(wireMockServer, stubId, urlPath, false, queryParams, null, nbRequests);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(wireMockServer, stubId, urlPath, queryParams, 1);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        verifyPostRequest(wireMockServer, stubId, urlPath, regexMatching, queryParams, body, 1);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.postRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.postRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(wireMockServer, stubId, requestBuilder, queryParams, body, nbRequests);
    }

    public static void verifyPutRequestWithUrlMatching(WireMockServer wireMockServer, UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams, String body) {
        verifyPutRequest(wireMockServer, stubId, urlPath, true, queryParams, body);
    }

    public static void verifyPutRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.putRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.putRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(wireMockServer, stubId, requestBuilder, queryParams, body, 1);
    }

    public static void verifyDeleteRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        verifyDeleteRequest(wireMockServer, stubId, urlPath, regexMatching, queryParams, 1);
    }

    public static void verifyDeleteRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, int nbRequests) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(wireMockServer, stubId, requestBuilder, queryParams, null, nbRequests);
    }

    public static void verifyGetRequest(WireMockServer wireMockServer, UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo(urlPath));
        queryParams.forEach(requestBuilder::withQueryParam);
        wireMockServer.verify(1, requestBuilder);
        removeRequestForStub(wireMockServer, stubId, 1);
    }

    private static void verifyRequest(WireMockServer wireMockServer, UUID stubId, RequestPatternBuilder requestBuilder, Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        queryParams.forEach(requestBuilder::withQueryParam);
        if (body != null) {
            requestBuilder.withRequestBody(WireMock.equalToJson(body));
        }
        wireMockServer.verify(nbRequests, requestBuilder);
        removeRequestForStub(wireMockServer, stubId, nbRequests);
    }

    public static void removeRequestForStub(WireMockServer wireMockServer, UUID stubId, int nbRequests) {
        List<ServeEvent> serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(stubId)).getServeEvents();
        assertEquals(nbRequests, serveEvents.size());
        for (ServeEvent serveEvent : serveEvents) {
            wireMockServer.removeServeEvent(serveEvent.getId());
        }
    }
}
