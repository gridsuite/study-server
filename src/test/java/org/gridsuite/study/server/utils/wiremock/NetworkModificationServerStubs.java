/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;

/**
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
 */
public class NetworkModificationServerStubs {
    private static final String SHARING_LINKS_URI = "/v1/groups/sharing-links";
    private static final String QUERY_PARAM_RECREATE = "recreate";

    private final WireMockServer wireMock;

    public NetworkModificationServerStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public void stubCreateSharingLinks(boolean recreate, String userId) {
        wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(SHARING_LINKS_URI))
            .withQueryParam(QUERY_PARAM_RECREATE, equalTo(Boolean.toString(recreate)))
            .withHeader(HEADER_USER_ID, equalTo(userId))
            .willReturn(WireMock.ok()));
    }

    public void verifyCreateSharingLinks(boolean recreate, int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, SHARING_LINKS_URI,
            Map.of(QUERY_PARAM_RECREATE, equalTo(Boolean.toString(recreate))), nbRequests);
    }
}
