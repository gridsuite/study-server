/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.removeRequestForStub;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili@rte-france.com>
 */
public class UserAdminServerStubs {
    private final WireMockServer wireMock;

    public UserAdminServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubGetUserProfile(String sub, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + sub + "/profile"))
                .willReturn(WireMock.ok().withBody(responseBody)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyGetUserProfile(UUID stubUuid, String sub) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/users/" + sub + "/profile"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
    }

}
