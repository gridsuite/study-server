/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.*;

/**
 * @author Maissa Souissi <maissa.souissi@rte-france.com>
 */
public class ComputationServerStubs {
    private final WireMockServer wireMock;

    public ComputationServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubComputationRun(String networkUuid, String variantId, String resultUuid) {
        MappingBuilder builder = WireMock.post(
            WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save.*")
        );
        if (variantId != null) {
            builder = builder.withQueryParam("variantId", WireMock.equalTo(variantId));
        }
        return wireMock.stubFor(
            builder.willReturn(WireMock.okJson("\"" + resultUuid + "\""))
        ).getId();
    }

    public void verifyComputationRun(UUID stubUuid, String networkUuid, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(
            wireMock,
            stubUuid,
            "/v1/networks/" + networkUuid + "/run-and-save",
            queryParams);
    }

    public UUID stubGetResultStatus(String resultUuid, String statusJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/status"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(statusJson))).getId();
    }

    public void verifyComputationStop(UUID stubUuid, String resultUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/results/" + resultUuid + "/stop", false, Map.of(), null);
    }

    public void verifyGetResultStatus(UUID stubId, String resultUuid) {
        verifyGetResultStatus(stubId, resultUuid, 1);
    }

    public void verifyGetResultStatus(UUID stubId, String resultUuid, int nbRequests) {
        verifyGetRequest(wireMock, stubId, "/v1/results/" + resultUuid + "/status", Map.of(), nbRequests);
    }

    /*
     * Computation parameter
     */

    public void verifyDuplicateParameters(String paramUUID) {
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/parameters"))
                .withQueryParam("duplicateFrom", equalTo(paramUUID))
        );
    }

    public void stubParameterPut(WireMockServer wireMockServer, String paramUuid, String responseJson) {
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
            .willReturn(WireMock.okJson(responseJson))
        );
    }

    public void verifyParameterPut(WireMockServer wireMockServer, String paramUuid) {
        wireMockServer.verify(
            putRequestedFor(urlEqualTo("/v1/parameters/" + paramUuid))
        );
    }

    public UUID stubParametersGet(String paramUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyParametersGet(UUID stubUuid, String paramUuid) {
        verifyGetRequest(wireMock, stubUuid, "/v1/parameters/" + paramUuid, Map.of());
    }

    public UUID stubCreateComputationParameter(UUID paramUuid) {
        return
            wireMock.stubFor(
                post(urlPathEqualTo("/v1/parameters"))
                    .willReturn(okJson("\"" + paramUuid + "\""))
            ).getId();
    }

    public void verifyComputationParameterPost(UUID stubUuid, String bodyJson) {
        verifyPostRequest(wireMock, stubUuid, "/v1/parameters", false, Map.of(), bodyJson);
    }

    /*    Results     */

    public UUID stubDeleteResult(String resultUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", WireMock.equalTo(resultUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteResult(UUID stubId, String resultUuid) {
        verifyDeleteRequest(wireMock, stubId, "/v1/results", false, Map.of("resultsUuids", WireMock.equalTo(resultUuid)));
    }

    public UUID stubDeleteResults(String path) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(path))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(WireMock.ok())
        ).getId();
    }
}
