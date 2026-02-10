/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Parameters;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
public class ShortcircuitServerStubs {
    private final WireMockServer wireMock;

    public ShortcircuitServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubShortCircuitRunWithPostAction(String networkUuid, String variantId, String resultUuid,
                                                  String destination, UUID modificationUuid, UUID rootNetworkUuid) {
        wireMock.stubFor(
                WireMock.post(WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save"))
                        .withQueryParam("receiver", WireMock.matching(".*"))
                        .withQueryParam("reportUuid", WireMock.matching(".*"))
                        .withQueryParam("reporterId", WireMock.matching(".*"))
                        .withQueryParam("variantId", WireMock.equalTo(variantId))
                        .withPostServeAction(POST_ACTION_SEND_INPUT,
                                Parameters.from(
                                        Map.of(
                                                "payload", "",
                                                "destination", destination,
                                                "receiver", "%7B%22nodeUuid%22%3A%22" + modificationUuid + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + rootNetworkUuid + "%22%2C%20%22userId%22%3A%22userId%22%7D"
                                        ))
                        ).willReturn(WireMock.okJson("\"" + resultUuid + "\"")));
    }

    public void stubShortCircuitStopWithPostAction(String resultUuid, String destination, UUID modificationNodeUuid, UUID rootNetworkUuid) {
        wireMock.stubFor(
                WireMock.put(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/stop")).withPostServeAction(POST_ACTION_SEND_INPUT,
                        Parameters.from(
                                Map.of(
                                        "payload", "",
                                        "destination", destination,
                                        "receiver", "%7B%22nodeUuid%22%3A%22" + modificationNodeUuid + "%22%2C%20%22rootNetworkUuid%22%3A%20%22" + rootNetworkUuid + "%22%2C%20%22userId%22%3A%22userId%22%7D"
                                ))).willReturn(ok())
        );
    }

    public void stubGetFaultTypes(String resultUuid) {
        wireMock.stubFor(WireMock.get("/v1/results/" + resultUuid + "/fault-types")
                .willReturn(ok()));
    }

    public void verifyGetFaultTypes(String resultUuid) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/fault-types", Map.of());
    }

    public void stubGetPagedFaultResults(String resultUuid, String result, String networkUuid, String variantId, String mode, String page, String size, String sort) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/fault_results/paged"))
                .withQueryParam("networkUuid", WireMock.equalTo(networkUuid))
                .withQueryParam("variantId", WireMock.equalTo(variantId))
                .withQueryParam("mode", WireMock.equalTo(mode))
                .withQueryParam("page", WireMock.equalTo(page))
                .withQueryParam("size", WireMock.equalTo(size))
                .withQueryParam("sort", WireMock.equalTo(sort))
                .willReturn(WireMock.okJson(result)));
    }

    public void verifyGetPagedFaultResults(String resultUuid, String networkUuid, String variantId, String mode, String page, String size, String sort) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/fault_results/paged", Map.of(
                "networkUuid", WireMock.equalTo(networkUuid),
                "variantId", WireMock.equalTo(variantId),
                "mode", WireMock.equalTo(mode),
                "page", WireMock.equalTo(page),
                "size", WireMock.equalTo(size),
                "sort", WireMock.equalTo(sort)
        ));
    }

    public void stubGetPagedFeederResults(String resultUuid, String result, String networkUuid, String variantId, String filters, String mode, String page, String size, String sort) {
        wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/feeder_results/paged"))
                .withQueryParam("networkUuid", WireMock.equalTo(networkUuid))
                .withQueryParam("variantId", WireMock.equalTo(variantId))
                .withQueryParam("filters", WireMock.equalTo(filters))
                .withQueryParam("mode", WireMock.equalTo(mode))
                .withQueryParam("page", WireMock.equalTo(page))
                .withQueryParam("size", WireMock.equalTo(size))
                .withQueryParam("sort", WireMock.equalTo(sort))
                .willReturn(WireMock.okJson(result)));
    }

    public void verifyGetPagedFeederResults(String resultUuid, String networkUuid, String variantId, String filters, String mode, String page, String size, String sort) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/results/" + resultUuid + "/feeder_results/paged", Map.of(
                "networkUuid", WireMock.equalTo(networkUuid),
                "variantId", WireMock.equalTo(variantId),
                "filters", WireMock.equalTo(filters),
                "mode", WireMock.equalTo(mode),
                "page", WireMock.equalTo(page),
                "size", WireMock.equalTo(size),
                "sort", WireMock.equalTo(sort)
        ));
    }

    public void verifyShortcircuitRun(String networkUuid, String variantId, boolean debug) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/networks/" + networkUuid + "/run-and-save", true,
                Map.of(
                        "receiver", WireMock.matching(".*"),
                        "reporterId", WireMock.matching(".*"),
                        "variantId", WireMock.equalTo(variantId),
                        "debug", WireMock.equalTo(String.valueOf(debug))),
                null);
    }
}
