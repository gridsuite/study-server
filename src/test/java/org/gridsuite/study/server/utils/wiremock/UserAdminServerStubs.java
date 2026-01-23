/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi@rte-france.com>
 */

public class UserAdminServerStubs {
    private final WireMockServer wireMock;

    public UserAdminServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void verifyUserProfile(String userId) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/users/" + userId + "/profile", Map.of());
    }

    public UUID stubUserProfile(String userId) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + userId + "/profile"))
            .willReturn(WireMock.ok())).getId();
    }

    public UUID stubUserProfile(String userId, String profileJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + userId + "/profile"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(profileJson))).getId();
    }
}
