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
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili@rte-france.com>
 */
public class LoadflowServerStubs {
    private final WireMockServer wireMock;

    public LoadflowServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubGetLoadflowProvider(String parametersUuid, String providerName) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .willReturn(WireMock.ok().withBody(providerName)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLoadflowProvider(String parametersUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/parameters/" + parametersUuid + "/provider", false, Map.of(), 1);
    }

    public void stubRunLoadflow(UUID networkUuid, String responseBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlMatching("/v1/networks/" + networkUuid + "/run-and-save\\?withRatioTapChangers=.*&receiver=.*&reportUuid=.*&reporterId=.*&variantId=.*"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void stubRunLoadflowFailed(UUID networkUuid, UUID nodeUuid, String responseBody) {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlMatching("/v1/networks/" + networkUuid + "/run-and-save\\?withRatioTapChangers=.*&receiver=.*&reportUuid=.*&reporterId=.*&variantId=.*"));

        mappingBuilder = mappingBuilder.withPostServeAction(POST_ACTION_SEND_INPUT,
                Parameters.from(
                        Map.of(
                                "payload", "",
                                "destination", "loadflow.run.dlx",
                                "receiver", "%7B%22nodeUuid%22%3A%22" + nodeUuid + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + networkUuid + "%22%2C%20%22userId%22%3A%22userId%22%7D"

                        )
                )
        );
        wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withBody(responseBody)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyRunLoadflow(UUID networkUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/networks/" + networkUuid + "/run-and-save", Map.of("withRatioTapChangers", new RegexPattern(".*"), "receiver", new RegexPattern(".*"), "reportUuid", new RegexPattern(".*"), "reporterId", new RegexPattern(".*"), "variantId", new RegexPattern(".*")));
    }

    public void stubGetLoadflowResult(UUID resultUuid, String responseBody) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLoadflowResult(UUID resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid, Map.of());
    }

    public void stubGetLoadflowStatus(UUID resultUuid, String responseBody, boolean isNotFound) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/status"))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLoadflowStatus(UUID resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/status", Map.of());
    }

    public void stubStopLoadflow(UUID resultUuid, UUID nodeUuid, UUID networkUuid, String responseBody) {
        MappingBuilder mappingBuilder = WireMock.put(WireMock.urlMatching("/v1/results/" + resultUuid + "/stop\\?receiver=.*"));
        mappingBuilder = mappingBuilder.withPostServeAction(POST_ACTION_SEND_INPUT,
                Parameters.from(
                        Map.of(
                                "payload", "",
                                "receiver", "%7B%22nodeUuid%22%3A%22" + nodeUuid + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + networkUuid + "%22%2C%20%22userId%22%3A%22userId%22%7D",
                                "destination", "loadflow.stopped"

                        )
                )
        );
        wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyStopLoadflow(UUID resultUuid) {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/results/" + resultUuid + "/stop", Map.of("receiver", new RegexPattern(".*")), null);
    }

    public void stubGetLoadflowModifications(UUID resultUuid, String responseBody, boolean isNotFound) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/modifications"))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLoadflowModifications(UUID resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/modifications", Map.of());
    }

    public void stubCreateLoadflowDefaultParameters(String createdParametersUuid) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters/default"))
                .willReturn(WireMock.ok().withBody(createdParametersUuid)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyCreateLoadflowDefaultParameters() {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters/default", Map.of());
    }

    public void stubGetLoadflowParameters(String parametersUuid, String parameters, boolean isNotFound) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(parameters)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLoadflowParameters(String parametersUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/parameters/" + parametersUuid, Map.of());
    }

    public void stubPutLoadflowParameters(String parametersUuid, String parameters) {
        if (StringUtils.isEmpty(parameters)) {
            wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid))
                    .willReturn(WireMock.ok())
            );
        } else {
            wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid)).withRequestBody(new EqualToPattern(parameters))
                    .willReturn(WireMock.ok())
            );
        }
    }

    public void verifyPutLoadflowParameters(String parametersUuid, String parameters) {
        if (parameters != null && !parameters.isEmpty()) {
            WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/parameters/" + parametersUuid, Map.of(), parameters);
        } else {
            WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/parameters/" + parametersUuid, Map.of(), null);
        }
    }

    public void stubCreateLoadflowParameters(String parametersUuid) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters"))
                .willReturn(WireMock.ok().withBody(parametersUuid)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyCreateLoadflowParameters() {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of());
    }

    public void stubGetDefaultProvider(String responseBody) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/default-provider"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetDefaultProvider() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/default-provider", Map.of());
    }

    public void stubGetResultsCount(String responseBody) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/supervision/results-count"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetResultsCount() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/supervision/results-count", Map.of());
    }

    public void stubDeleteLoadflowResults(String resultUuid) {
        wireMock.stubFor(WireMock.delete(WireMock.urlMatching("/v1/results\\?resultsUuids=" + resultUuid))
                .willReturn(WireMock.ok()));
    }

    public void verifyDeleteLoadflowResults() {
        WireMockUtilsCriteria.verifyDeleteRequest(wireMock, "/v1/results", Map.of("resultsUuids", new RegexPattern(".*")));
    }

    public void stubDuplicateLoadflowParameters(String loadflowParametersToDuplicateUuid, String createdLoadflowParametersUuidJson, boolean isNotFound) {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters?duplicateFrom=" + loadflowParametersToDuplicateUuid))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(createdLoadflowParametersUuidJson).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)));
    }

    public void verifyDuplicateLoadflowParameters(String loadflowParametersToDuplicateUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of("duplicateFrom", WireMock.equalTo(loadflowParametersToDuplicateUuid)));
    }

    public void stubPutLoadflowProvider(String parametersUuid, String providerName) {
        wireMock.stubFor(WireMock.put(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .withRequestBody(new EqualToPattern(providerName))
                .willReturn(WireMock.ok())
        );
    }

    public void verifyPutLoadflowProvider(String parametersUuid) {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/parameters/" + parametersUuid + "/provider", Map.of(), null);
    }

    public void stubDeleteLoadFlowParameters(String parametersUuid) {
        wireMock.stubFor(WireMock.delete(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid))
                .willReturn(WireMock.ok()));
    }

    public void verifyDeleteLoadFlowParameters(String parametersUuid) {
        WireMockUtilsCriteria.verifyDeleteRequest(wireMock, "/v1/parameters/" + parametersUuid, Map.of());
    }

    public void stubGetComputationStatus(String resultUuid, String computingStatusResponse) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation-status"))
                .willReturn(WireMock.ok()
                        .withBody(computingStatusResponse)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetComputationStatus(String resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/computation-status", Map.of());
    }

    public void stubGetComputation(String resultUuid) {
        wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation"))
                .willReturn(WireMock.notFound())
        );
    }

    public void verifyGetComputation(String resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/computation", Map.of());
    }

    public void stubGetLimitViolation(String resultUuid, String limitViolationResponse, boolean withRequestParam) {
        wireMock.stubFor(WireMock.get(WireMock.urlMatching("/v1/results/" + resultUuid + "/limit-violations" + (withRequestParam ? "\\?filters=.*globalFilters=.*networkUuid=.*variantId.*sort=.*" : "")))
                .willReturn(WireMock.ok()
                        .withBody(limitViolationResponse)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        );
    }

    public void verifyGetLimitViolation(String resultUuid, boolean withRequestParam) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/limit-violations",
                withRequestParam ?
                        Map.of("filters", new RegexPattern(".*"), "globalFilters", new RegexPattern(".*"), "networkUuid", new RegexPattern(".*"), "variantId", new RegexPattern(".*"), "sort", new RegexPattern(".*"))
                        : Map.of());
    }

    public void stubPutInvalidateStatus() {
        wireMock.stubFor(WireMock.put(WireMock.urlMatching("/v1/results/invalidate-status\\?resultUuid=.*"))
                .willReturn(WireMock.ok())
        );
    }

    public void verifyPutInvalidateStatus() {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/results/invalidate-status", Map.of("resultUuid", new RegexPattern(".*")), null);
    }

}
