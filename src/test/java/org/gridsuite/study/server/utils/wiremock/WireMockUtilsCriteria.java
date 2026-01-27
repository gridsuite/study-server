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
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unlike WireMockUtils, this class remove the requests after verification by matching the request pattern, and not by stub ID.
 * We are sure to remove only the requests verified and matching the pattern.
 * Also, no need to store the stub ID anymore.
 * This class should be the first choice over WireMockUtils.
 *
 * @author Florent MILLOT <florent.millot at rte-france.com>
 */
public final class WireMockUtilsCriteria {
    private WireMockUtilsCriteria() {
        throw new IllegalStateException("Utility class");
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(wireMockServer, urlPath, queryParams, 1);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, String urlPath, Map<String, StringValuePattern> queryParams, int nbRequests) {
        verifyPostRequest(wireMockServer, urlPath, false, queryParams, null, nbRequests);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        verifyPostRequest(wireMockServer, urlPath, regexMatching, queryParams, body, 1);
    }

    public static void verifyPostRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching,
                                         Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        RequestPatternBuilder requestBuilder = postRequestBuilder(urlPath, regexMatching, queryParams);
        verifyRequest(wireMockServer, requestBuilder, queryParams, body, nbRequests);
    }

    public static void verifyPutRequest(WireMockServer wireMockServer, String urlPath, Map<String, StringValuePattern> queryParams, String body) {
        verifyPutRequest(wireMockServer, urlPath, false, queryParams, body);
    }

    public static void verifyPutRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching,
                                        Map<String, StringValuePattern> queryParams, String body) {
        RequestPatternBuilder requestBuilder = putRequestBuilder(urlPath, regexMatching, queryParams);
        verifyRequest(wireMockServer, requestBuilder, queryParams, body, 1);
    }

    public static void verifyDeleteRequest(WireMockServer wireMockServer, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyDeleteRequest(wireMockServer, urlPath, false, queryParams, 1);
    }

    public static void verifyDeleteRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        verifyDeleteRequest(wireMockServer, urlPath, regexMatching, queryParams, 1);
    }

    public static void verifyDeleteRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching,
                                           Map<String, StringValuePattern> queryParams, int nbRequests) {
        RequestPatternBuilder requestBuilder = deleteRequestBuilder(urlPath, regexMatching, queryParams);
        verifyRequest(wireMockServer, requestBuilder, queryParams, null, nbRequests);
    }

    public static void verifyGetRequest(WireMockServer wireMockServer, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyGetRequest(wireMockServer, urlPath, queryParams, 1);
    }

    public static void verifyGetRequest(WireMockServer wireMockServer, String urlPath,
                                        Map<String, StringValuePattern> queryParams, int nbRequests) {
        verifyGetRequest(wireMockServer, urlPath, false, queryParams, nbRequests);
    }

    public static void verifyGetRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching,
                                        Map<String, StringValuePattern> queryParams, int nbRequests) {
        RequestPatternBuilder requestBuilder = getRequestBuilder(urlPath, regexMatching, queryParams);
        verifyRequest(wireMockServer, requestBuilder, queryParams, null, nbRequests);
    }

    public static void verifyHeadRequest(WireMockServer wireMockServer, String urlPath,
                                        Map<String, StringValuePattern> queryParams, int nbRequests) {
        verifyHeadRequest(wireMockServer, urlPath, false, queryParams, nbRequests);
    }

    public static void verifyHeadRequest(WireMockServer wireMockServer, String urlPath, boolean regexMatching,
                                         Map<String, StringValuePattern> queryParams, int nbRequests) {
        RequestPatternBuilder requestBuilder = headRequestBuilder(urlPath, regexMatching, queryParams);
        verifyRequest(wireMockServer, requestBuilder, queryParams, null, nbRequests);
    }

    private static void verifyRequest(WireMockServer wireMockServer, RequestPatternBuilder requestBuilder, Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        queryParams.forEach(requestBuilder::withQueryParam);
        if (body != null) {
            requestBuilder.withRequestBody(WireMock.equalToJson(body));
        }
        wireMockServer.verify(nbRequests, requestBuilder);
        removeRequestMatching(wireMockServer, requestBuilder, nbRequests);
    }

    public static void removeRequestMatching(WireMockServer wireMockServer, RequestPatternBuilder requestBuilder, int nbRequests) {

        List<LoggedRequest> requests = wireMockServer.findAll(requestBuilder);
        // this assert should not fail
        assertEquals(nbRequests, requests.size(), "Wrong number of requests");
        wireMockServer.removeServeEventsMatching(requestBuilder.build());
    }

    /**
     * These utility functions below aim to exclude query params from URL matching if none is provided.
     * If there are no query params, we match the complete URL, including the query string, which is empty.
     * Then we are sure to not match other requests than the one we verified.
     */

    private static RequestPatternBuilder postRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.postRequestedFor(WireMock.urlMatching(noQueryUrlRegex(urlPath)))
                : WireMock.postRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.postRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.postRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    private static RequestPatternBuilder putRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.putRequestedFor(WireMock.urlMatching(noQueryUrlRegex(urlPath)))
                : WireMock.putRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.putRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.putRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    private static RequestPatternBuilder deleteRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.deleteRequestedFor(WireMock.urlMatching(noQueryUrlRegex(urlPath)))
                : WireMock.deleteRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    private static RequestPatternBuilder headRequestBuilder(String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.headRequestedFor(WireMock.urlMatching(noQueryUrlRegex(urlPath)))
                : WireMock.headRequestedFor(WireMock.urlEqualTo(urlPath));
        }
        return regexMatching
            ? WireMock.headRequestedFor(WireMock.urlPathMatching(urlPath))
            : WireMock.headRequestedFor(WireMock.urlPathEqualTo(urlPath));
    }

    private static RequestPatternBuilder getRequestBuilder(String urlPathOrPattern, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        if (queryParams.isEmpty()) {
            return regexMatching
                ? WireMock.getRequestedFor(WireMock.urlMatching(noQueryUrlRegex(urlPathOrPattern)))
                : WireMock.getRequestedFor(WireMock.urlEqualTo(urlPathOrPattern));
        }
        return regexMatching
            ? WireMock.getRequestedFor(WireMock.urlPathMatching(urlPathOrPattern))
            : WireMock.getRequestedFor(WireMock.urlPathEqualTo(urlPathOrPattern));
    }

    /**
     * Builds a regex that matches the complete URL but forbids any query string.
     * - (?!.*\\?) : refuses the presence of '?'
     * - ^(?:pattern)$ : forces complete match
     */
    private static String noQueryUrlRegex(String urlPathPattern) {
        return "^(?!.*\\?)(?:" + urlPathPattern + ")$";
    }
}
