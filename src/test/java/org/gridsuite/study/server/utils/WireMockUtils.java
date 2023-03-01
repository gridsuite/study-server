/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class WireMockUtils {
    private final WireMockServer wireMock;

    public WireMockUtils(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubNetworkModificationGet(String groupUuid, String result) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/groups/" + groupUuid + "/modifications"))
                .withQueryParam("errorOnGroupNotFound", WireMock.equalTo("false"))
            .willReturn(WireMock.ok().withBody(result))
        ).getId();
    }

    public UUID stubNetworkModificationPost(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
            .willReturn(WireMock.ok()
                .withBody(responseBody)
                .withHeader("Content-Type", "application/json"))
        ).getId();
    }

    public UUID stubNetworkModificationPostWithBody(String requestBody, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.ok()
                .withBody(responseBody)
                .withHeader("Content-Type", "application/json"))
        ).getId();
    }

    public UUID stubNetworkModificationPostWithError(String requestBody) {
        return stubNetworkModificationPostWithError(requestBody, "Internal Server Error");
    }

    public UUID stubNetworkModificationPostWithError(String requestBody, String errorMessage) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.serverError().withBody(errorMessage))
        ).getId();
    }

    public UUID stubNetworkModificationPostWithBodyAndError(String requestBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-modifications"))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.badRequest())
        ).getId();
    }

    public UUID stubNetworkModificationPut(String modificationUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + modificationUuid))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationPutWithBody(String modificationUuid, String requestBody) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + modificationUuid))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationPutWithBodyAndError(String modificationUuid, String requestBody) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/network-modifications/" + modificationUuid))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.badRequest())
        ).getId();
    }

    public void verifyNetworkModificationsGet(UUID stubId, String groupUuid) {
        verifyGetRequest(stubId, "/v1/groups/" + groupUuid + "/modifications", Map.of("errorOnGroupNotFound", WireMock.equalTo("false")));
    }

    public void verifyNetworkModificationPost(UUID stubId, String requestBody, String networkUuid) {
        verifyPostRequest(stubId, "/v1/network-modifications", false,
            Map.of("networkUuid", WireMock.equalTo(networkUuid), "groupUuid", WireMock.matching(".*")),
            requestBody);
    }

    public void verifyNetworkModificationPostWithVariant(UUID stubId, String requestBody, String networkUuid, String variantId) {
        verifyPostRequest(stubId, "/v1/network-modifications", false,
            Map.of("networkUuid", WireMock.equalTo(networkUuid), "groupUuid", WireMock.matching(".*"), "variantId", WireMock.equalTo(variantId)),
            requestBody);
    }

    public void verifyNetworkModificationPut(UUID stubId, String modificationUuid, String requestBody) {
        verifyPutRequest(stubId, "/v1/network-modifications/" + modificationUuid, false, Map.of(), requestBody);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(stubId, urlPath, false, queryParams, null);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.postRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.postRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, body);
    }

    public void verifyPutRequestWithUrlMatching(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams, String body) {
        verifyPutRequest(stubId, urlPath, true, queryParams, body);
    }

    public void verifyPutRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.putRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.putRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, body);
    }

    public void verifyDeleteRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, null);
    }

    public void verifyGetRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo(urlPath));
        queryParams.forEach(requestBuilder::withQueryParam);
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubId);
    }

    private void verifyRequest(UUID stubId, RequestPatternBuilder requestBuilder, Map<String, StringValuePattern> queryParams, String body) {
        queryParams.forEach(requestBuilder::withQueryParam);
        if (body != null) {
            requestBuilder.withRequestBody(WireMock.equalToJson(body));
        }
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubId);
    }

    private void removeRequestForStub(UUID stubId) {
        List<ServeEvent> serveEvents = wireMock.getServeEvents(ServeEventQuery.forStubMapping(stubId)).getServeEvents();
        assertEquals(1, serveEvents.size());
        wireMock.removeServeEvent(serveEvents.get(0).getId());
    }
}
