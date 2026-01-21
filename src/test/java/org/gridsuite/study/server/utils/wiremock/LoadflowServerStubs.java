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
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili@rte-france.com>
 */
public class LoadflowServerStubs {
    private final WireMockServer wireMock;

    public LoadflowServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubGetLoadflowProvider(String parametersUuid, String providerName) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .willReturn(WireMock.ok().withBody(providerName)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLoadflowProvider(UUID stubUuid, String parametersUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid + "/provider"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubRunLoadflow(UUID networkUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlMatching("/v1/networks/" + networkUuid + "/run-and-save\\?withRatioTapChangers=.*&receiver=.*&reportUuid=.*&reporterId=.*&variantId=.*"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public UUID stubRunLoadflowFailed(UUID networkUuid, UUID nodeUuid, String responseBody) {
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
        return wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withBody(responseBody)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyRunLoadflow(UUID stubUuid, UUID networkUuid) {
        RequestPatternBuilder requestBuilder = WireMock.postRequestedFor(WireMock.urlMatching("/v1/networks/" + networkUuid + "/run-and-save\\?withRatioTapChangers=.*&receiver=.*&reportUuid=.*&reporterId=.*&variantId=.*"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetLoadflowResult(UUID resultUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLoadflowResult(UUID stubUuid, UUID resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/results/" + resultUuid));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetLoadflowStatus(UUID resultUuid, String responseBody, boolean isNotFound) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/status"))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLoadflowStatus(UUID stubUuid, UUID resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/status"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubStopLoadflow(UUID resultUuid, UUID nodeUuid, UUID networkUuid, String responseBody) {
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
        return wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyStopLoadflow(UUID stubUuid, UUID resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.putRequestedFor(WireMock.urlMatching("/v1/results/" + resultUuid + "/stop\\?receiver=.*"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetLoadflowModifications(UUID resultUuid, String responseBody, boolean isNotFound) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/modifications"))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLoadflowModifications(UUID stubUuid, UUID resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/modifications"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubCreateLoadflowDefaultParameters(String createdParametersUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters/default"))
                .willReturn(WireMock.ok().withBody(createdParametersUuid)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCreateLoadflowDefaultParameters(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/parameters/default"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetLoadflowParameters(String parametersUuid, String parameters, boolean isNotFound) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(parameters)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLoadflowParameters(UUID stubUuid, String parametersUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubPutLoadflowParameters(String parametersUuid, String parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid))
                    .willReturn(WireMock.ok())
            ).getId();
        }
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid)).withRequestBody(new EqualToPattern(parameters))
                .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyPutLoadflowParameters(UUID stubUuid, String parametersUuid, String parameters) {
        RequestPatternBuilder requestBuilder = WireMock.putRequestedFor(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid));
        if (parameters != null && !parameters.isEmpty()) {
            requestBuilder.withRequestBody(new EqualToPattern(parameters));
        }
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubCreateLoadflowParameters(String parametersUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters"))
                .willReturn(WireMock.ok().withBody(parametersUuid)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCreateLoadflowParameters(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.postRequestedFor(WireMock.urlPathEqualTo("/v1/parameters"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetDefaultProvider(String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/default-provider"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetDefaultProvider(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/default-provider"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetResultsCount(String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/supervision/results-count"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetResultsCount(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/supervision/results-count"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDeleteLoadflowResults(String resultUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlMatching("/v1/results\\?resultsUuids=" + resultUuid))
                .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteLoadflowResults(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.deleteRequestedFor(WireMock.urlMatching("/v1/results\\?resultsUuids=.*"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDuplicateLoadflowParameters(String loadflowParametersToDuplicateUuid, String createdLoadflowParametersUuidJson, boolean isNotFound) {
        return wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters?duplicateFrom=" + loadflowParametersToDuplicateUuid))
                .willReturn(isNotFound ? WireMock.notFound() : WireMock.ok().withBody(createdLoadflowParametersUuidJson).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))).getId();
    }

    public void verifyDuplicateLoadflowParameters(UUID stubUuid, String loadflowParametersToDuplicateUuid) {
        RequestPatternBuilder requestBuilder = WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/parameters?duplicateFrom=" + loadflowParametersToDuplicateUuid));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubPutLoadflowProvider(String parametersUuid, String providerName) {
        return wireMock.stubFor(WireMock.put(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .withRequestBody(new EqualToPattern(providerName))
                .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyPutLoadflowProvider(UUID stubUuid, String parametersUuid) {
        RequestPatternBuilder requestBuilder = WireMock.putRequestedFor(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid + "/provider"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubDeleteLoadFlowParameters(String parametersUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid))
                .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteLoadFlowParameters(UUID stubUuid, String parametersUuid) {
        RequestPatternBuilder requestBuilder = WireMock.deleteRequestedFor(WireMock.urlEqualTo("/v1/parameters/" + parametersUuid));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetComputationStatus(String resultUuid, String computingStatusResponse) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation-status"))
                .willReturn(WireMock.ok()
                        .withBody(computingStatusResponse)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetComputationStatus(UUID stubUuid, String resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation-status"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetComputation(String resultUuid) {
        return wireMock.stubFor(WireMock.get(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation"))
                .willReturn(WireMock.notFound())
        ).getId();
    }

    public void verifyGetComputation(UUID stubUuid, String resultUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlEqualTo("/v1/results/" + resultUuid + "/computation"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubGetLimitViolation(String resultUuid, String limitViolationResponse, boolean withRequestParam) {
        return wireMock.stubFor(WireMock.get(WireMock.urlMatching("/v1/results/" + resultUuid + "/limit-violations" + (withRequestParam ? "\\?filters=.*globalFilters=.*networkUuid=.*variantId.*sort=.*" : "")))
                .willReturn(WireMock.ok()
                        .withBody(limitViolationResponse)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetLimitViolation(UUID stubUuid, String resultUuid, boolean withRequestParam) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlMatching("/v1/results/" + resultUuid + "/limit-violations" + (withRequestParam ? "\\?filters=.*globalFilters=.*networkUuid=.*variantId.*sort=.*" : "")));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public UUID stubPutInvalidateStatus() {
        return wireMock.stubFor(WireMock.put(WireMock.urlMatching("/v1/results/invalidate-status\\?resultUuid=.*"))
                .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyPutInvalidateStatus(UUID stubUuid) {
        RequestPatternBuilder requestBuilder = WireMock.putRequestedFor(WireMock.urlMatching("/v1/results/invalidate-status\\?resultUuid=.*"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

}
