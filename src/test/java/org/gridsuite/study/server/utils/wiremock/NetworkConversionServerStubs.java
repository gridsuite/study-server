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
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.verifyPostRequest;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier@rte-france.com>
 */
public class NetworkConversionServerStubs {

    public static final String URI_NETWORK = "/v1/networks";
    WireMockServer wireMock;

    public NetworkConversionServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void verifyImportNetwork(UUID stubUuid, String caseUuid, String variantId, String importParametersAsJson) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("caseUuid", equalTo(caseUuid));
        params.put("receiver", WireMock.matching(".*"));

        if (variantId != null) {
            params.put("variantId", equalTo(variantId));
        }

        verifyPostRequest(wireMock, stubUuid, URI_NETWORK, false,
            params,
            importParametersAsJson
        );
    }

    public void verifyImportNetwork(UUID stubUuid, String caseUuid, String variantId) {
        verifyImportNetwork(stubUuid, caseUuid, variantId, null);
    }

    public UUID stubImportNetworkWithPostAction(String caseUuid, Map<String, Object> importParameters, String networkUuid, String networkId, String variantId, String caseFormat, String caseName, CountDownLatch countDownLatch) {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK))
            .withQueryParam("caseUuid", equalTo(caseUuid))
            .withQueryParam("receiver", WireMock.matching(".*"));
        if (variantId != null) {
            mappingBuilder = mappingBuilder.withQueryParam("variantId", equalTo(variantId));
        }
        mappingBuilder = mappingBuilder.withPostServeAction(POST_ACTION_SEND_INPUT,
                Parameters.from(
                    Map.of(
                        "payload", "",
                        "destination", "case.import.succeeded",
                        "networkUuid", networkUuid,
                        "networkId", networkId,
                        "caseFormat", caseFormat,
                        "caseName", caseName,
                        "importParameters", importParameters,
                        "latch", countDownLatch
                    )
                )
            )
            .willReturn(WireMock.ok());

        return wireMock.stubFor(mappingBuilder).getId();
    }

    public UUID stubImportNetwork(String caseUuid, String importParametersAsJson, String variantId, String caseFormat) {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK))
            .withQueryParam(CASE_UUID, WireMock.matching(caseUuid))
            .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.matching(variantId))
            .withQueryParam(CASE_FORMAT, WireMock.matching(caseFormat));

        if (importParametersAsJson != null) {
            mappingBuilder = mappingBuilder.withRequestBody(equalTo(importParametersAsJson))
                .willReturn(WireMock.ok());
        }

        return wireMock.stubFor(mappingBuilder).getId();
    }
}
