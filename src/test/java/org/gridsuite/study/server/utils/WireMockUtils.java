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

import static org.gridsuite.study.server.StudyConstants.*;
import static org.junit.Assert.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class WireMockUtils {
    private static final String DELIMITER = "/";

    public static final String URI_NETWORK_DATA = "/v1/networks";

    private static final String URI_NETWORK_MODIFICATION = "/v1/network-modifications";

    private static final String URI_NETWORK_MODIFICATION_GROUPS = "/v1/groups";

    private final WireMockServer wireMock;

    public WireMockUtils(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubNetworkElementInfosGet(String networkUuid, String elementType, String infoType, String elementId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubNetworkElementInfosGetNotFound(String networkUuid, String elementType, String infoType, String elementId) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
                .willReturn(WireMock.notFound())
        ).getId();
    }

    public UUID stubNetworkElementInfosGetWithError(String networkUuid, String elementType, String infoType, String elementId) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
                .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
    }

    public void verifyNetworkElementInfosGet(UUID stubUuid, String networkUuid, String elementType, String infoType, String elementId) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId, Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType), QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType)));
    }

    public UUID stubNetworkElementsInfosGet(String networkUuid, String elementType, String infoType, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements"))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkElementsInfosGet(UUID stubUuid, String networkUuid, String elementType, String infoType) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements", Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType), QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType)));
    }

    public UUID stubNetworkElementsIdsGet(String networkUuid, String elementType, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-ids"))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkElementsIdsGet(UUID stubUuid, String networkUuid, String elementType) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-ids", Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType)));
    }

    public UUID stubNetworkEquipmentsInfosGet(String networkUuid, String equipmentPath, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + equipmentPath))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkEquipmentsInfosGet(UUID stubUuid, String networkUuid, String equipmentPath) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + equipmentPath, Map.of());
    }

    public UUID stubNetworkEquipmentsInfosGet(String networkUuid, String infoTypePath, String equipmentType, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath))
                .withQueryParam(QUERY_PARAM_EQUIPMENT_TYPE, WireMock.equalTo(equipmentType))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkEquipmentsInfosGet(UUID stubUuid, String networkUuid, String infoTypePath, String equipmentType) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath, Map.of(QUERY_PARAM_EQUIPMENT_TYPE, WireMock.equalTo(equipmentType)));
    }

    public UUID stubNetworkEquipmentInfosGet(String networkUuid, String infoTypePath, String equipmentId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath + DELIMITER + equipmentId))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkEquipmentInfosGet(UUID stubUuid, String networkUuid, String infoTypePath, String equipmentId) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath + DELIMITER + equipmentId, Map.of());
    }

    public UUID stubNetworkModificationGet() {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathMatching(URI_NETWORK_MODIFICATION_GROUPS + "/.*/modifications"))
            .withQueryParam("errorOnGroupNotFound", WireMock.equalTo("false"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationGet(String groupUuid, String result) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + groupUuid + "/modifications"))
            .withQueryParam("errorOnGroupNotFound", WireMock.equalTo("false"))
            .willReturn(WireMock.ok().withBody(result))
        ).getId();
    }

    public UUID stubNetworkModificationPost(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION))
            .willReturn(WireMock.ok()
                .withBody(responseBody)
                .withHeader("Content-Type", "application/json"))
        ).getId();
    }

    public UUID stubNetworkModificationPostWithError(String requestBody) {
        return stubNetworkModificationPostWithError(requestBody, "Internal Server Error");
    }

    public UUID stubNetworkModificationPostWithError(String requestBody, String errorMessage) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.serverError().withBody(errorMessage))
        ).getId();
    }

    public UUID stubNetworkModificationPostWithBodyAndError(String requestBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.badRequest())
        ).getId();
    }

    public UUID stubNetworkModificationPut(String modificationUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationPutWithBody(String modificationUuid, String requestBody) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationPutWithBodyAndError(String modificationUuid, String requestBody) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid))
            .withRequestBody(WireMock.equalToJson(requestBody))
            .willReturn(WireMock.badRequest())
        ).getId();
    }

    public UUID stubDuplicateModificationGroup() {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION_GROUPS))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .withQueryParam("groupUuid", WireMock.matching(".*"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationDeleteGroup() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + ".*"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyNetworkModificationsGet(UUID stubId, String groupUuid) {
        verifyGetRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + groupUuid + "/modifications", Map.of("errorOnGroupNotFound", WireMock.equalTo("false")));
    }

    public void verifyNetworkModificationPost(UUID stubId, String requestBody, String networkUuid) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION, false,
            Map.of("networkUuid", WireMock.equalTo(networkUuid), "groupUuid", WireMock.matching(".*")),
            requestBody);
    }

    public void verifyNetworkModificationPostWithVariant(UUID stubId, String requestBody, String networkUuid, String variantId) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION, false,
            Map.of("networkUuid", WireMock.equalTo(networkUuid), "groupUuid", WireMock.matching(".*"), "variantId", WireMock.equalTo(variantId)),
            requestBody);
    }

    public void verifyNetworkModificationPut(UUID stubId, String modificationUuid, String requestBody) {
        verifyPutRequest(stubId, URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid, false, Map.of(), requestBody);
    }

    public void verifyDuplicateModificationGroup(UUID stubId, int nbRequests) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS, Map.of("duplicateFrom", WireMock.matching(".*"), "groupUuid", WireMock.matching(".*")), nbRequests);
    }

    public void verifyNetworkModificationDeleteGroup(UUID stubId) {
        verifyDeleteRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + ".*", true, Map.of());
    }

    public void verifyPostRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(stubId, urlPath, queryParams, 1);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams, int nbRequests) {
        verifyPostRequest(stubId, urlPath, false, queryParams, null, nbRequests);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        verifyPostRequest(stubId, urlPath, regexMatching, queryParams, body, 1);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.postRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.postRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, body, nbRequests);
    }

    public void verifyPutRequestWithUrlMatching(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams, String body) {
        verifyPutRequest(stubId, urlPath, true, queryParams, body);
    }

    public void verifyPutRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, String body) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.putRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.putRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, body, 1);
    }

    public void verifyDeleteRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, null, 1);
    }

    public void verifyGetRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo(urlPath));
        queryParams.forEach(requestBuilder::withQueryParam);
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubId, 1);
    }

    private void verifyRequest(UUID stubId, RequestPatternBuilder requestBuilder, Map<String, StringValuePattern> queryParams, String body, int nbRequests) {
        queryParams.forEach(requestBuilder::withQueryParam);
        if (body != null) {
            requestBuilder.withRequestBody(WireMock.equalToJson(body));
        }
        wireMock.verify(nbRequests, requestBuilder);
        removeRequestForStub(stubId, nbRequests);
    }

    private void removeRequestForStub(UUID stubId, int nbRequests) {
        List<ServeEvent> serveEvents = wireMock.getServeEvents(ServeEventQuery.forStubMapping(stubId)).getServeEvents();
        assertEquals(nbRequests, serveEvents.size());
        for (ServeEvent serveEvent : serveEvents) {
            wireMock.removeServeEvent(serveEvent.getId());
        }
    }
}
