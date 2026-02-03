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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * @author Maissa Souissi <maissa.souissi@rte-france.com>
 */
public class ComputationServerStubs {
    private final WireMockServer wireMock;

    public ComputationServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubComputationRun(String networkUuid, String variantId, String resultUuid) {
        MappingBuilder builder = WireMock.post(
            WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save.*")
        );
        if (variantId != null) {
            builder = builder.withQueryParam("variantId", WireMock.equalTo(variantId));
        }
        wireMock.stubFor(
            builder.willReturn(WireMock.okJson("\"" + resultUuid + "\""))
        );
    }

    public void verifyComputationRun(String networkUuid, Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/networks/" + networkUuid + "/run-and-save", queryParams);
    }

    public UUID stubGetResultStatus(String resultUuid, String statusJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/status"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(statusJson))).getId();
    }

    public UUID stubGetResult(String resultUuid, String statusJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid)).withQueryParam("mode", WireMock.equalTo("FULL"))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(statusJson))).getId();
    }

    public void verifyComputationStop(String resultUuid, Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/results/" + resultUuid + "/stop", true, queryParams, null);
    }

    public void verifyGetResultStatus(String resultUuid) {
        verifyGetResultStatus(resultUuid, 1);
    }

    public void verifyGetResultStatus(String resultUuid, int nbRequests) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/status", Map.of(), nbRequests);
    }

    /*
     * Computation parameter
     */

    public void stubParametersDuplicateFrom(String duplicateFromUuid, String responseBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody)));
    }

    public void stubParametersDuplicateFromNotFound(String duplicateFromUuid) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.notFound()));
    }

    public void verifyParametersDuplicateFrom(String duplicateFromUuid) {
        verifyParametersDuplicateFrom(duplicateFromUuid, 1);
    }

    public void verifyParametersDuplicateFrom(String duplicateFromUuid, int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of("duplicateFrom", WireMock.equalTo(duplicateFromUuid)), nbRequests);
    }

    public void stubParameterPut(String paramUuid, String responseJson) {
        wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
                .willReturn(WireMock.okJson(responseJson))
        );
    }

    public void verifyParameterPut(String paramUuid) {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/parameters/" + paramUuid, Map.of(), null);
    }

    public void stubParametersGet(String paramUuid, String responseBody) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
            .willReturn(WireMock.ok().withBody(responseBody))
        );
    }

    public void verifyParametersGet(String paramUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/parameters/" + paramUuid, Map.of());
    }

    public void stubCreateParameter(UUID paramUuid) {
        wireMock.stubFor(
            post(urlPathEqualTo("/v1/parameters"))
                .willReturn(okJson("\"" + paramUuid + "\""))
        );
    }

    public void verifyParameterPost(String bodyJson) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", false, Map.of(), bodyJson);
    }

    public void verifyParameters(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of(), nbRequests);
    }

    public void stubPostParametersDefault(String responseBody) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters/default"))
                .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public void stubGetParametersDefault(String statusJson) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/default"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(statusJson)));
    }

    public void verifyParametersDefault(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters/default", Map.of(), nbRequests);
    }

    public void stubDeleteParameters(String parametersUuid) {
        wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid))
                .willReturn(WireMock.ok()));
    }

    public void verifyDeleteParameters(String parametersUuid) {
        WireMockUtilsCriteria.verifyDeleteRequest(wireMock,
                "/v1/parameters/" + parametersUuid, Map.of());
    }

    public void verifyParametersDefault() {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters/default", Map.of());
    }

    /*    Results     */

    public UUID stubDeleteResult(String resultUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", WireMock.equalTo(resultUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteResult(String resultUuid) {
        WireMockUtilsCriteria.verifyDeleteRequest(wireMock, "/v1/results", false, Map.of("resultsUuids", WireMock.equalTo(resultUuid)));
    }

    public void stubDeleteResults(String path) {
        wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(path))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(WireMock.ok())
        );
    }

    public void stubResultsCount(int count) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                "/v1/supervision/results-count"))
            .willReturn(WireMock.okJson(String.valueOf(count)))
        );
    }

    public void verifyResultsCountGet() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/supervision/results-count", Map.of());
    }

    public void stubGetResultCsv(String resultUuid, byte[] csvContent) {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1/results/" + resultUuid + "/csv"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(csvContent)));
    }

    public void stubGetResultCsvNotFound(String resultUuid) {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1/results/" + resultUuid + "/csv"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                ));
    }

    public void verifyGetResultCsv(String resultUuid) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/results/" + resultUuid + "/csv", Map.of());
    }

    /*    Status     */

    public void stubInvalidateStatus() {
        wireMock.stubFor(WireMock.put(WireMock.urlMatching("/v1/results/invalidate-status\\?resultUuid=.*"))
                .willReturn(ok()));
    }

    public void verifyInvalidateStatus(Map<String, StringValuePattern> queryParams) {
        WireMockUtilsCriteria.verifyPutRequest(wireMock, "/v1/results/invalidate-status",
                true, queryParams, null);
    }

}
