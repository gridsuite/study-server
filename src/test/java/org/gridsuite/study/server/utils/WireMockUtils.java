/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.powsybl.iidm.network.TwoSides;
import org.gridsuite.filter.utils.EquipmentType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.utils.SendInput.POST_ACTION_SEND_INPUT;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class WireMockUtils {
    private static final String DELIMITER = "/";

    public static final String URI_NETWORK_DATA = "/v1/networks";

    private static final String URI_NETWORK_MODIFICATION = "/v1/network-modifications";
    private static final String URI_NETWORK_AREA_DIAGRAM = "/v1/network-area-diagram/config/positions";

    private static final String URI_NETWORK_MODIFICATION_GROUPS = "/v1/groups";

    public static final String FIRST_VARIANT_ID = "first_variant_id";

    private final WireMockServer wireMock;

    public WireMockUtils(WireMockServer wireMock) {
        this.wireMock = wireMock;
    }

    public UUID stubAllNetworkElementInfosGet(String networkUuid, String infoType, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "all"))
            .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.equalTo(FIRST_VARIANT_ID))
            .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
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
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId,
            Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType),
                   QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType)));
    }

    public UUID stubNetworkElementsInfosPost(String networkUuid, String infoType, String elementType, List<Double> nominalVoltages, String responseBody) {
        MappingBuilder requestPatternBuilder = WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements"))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType));
        if (nominalVoltages != null && !nominalVoltages.isEmpty()) {
            for (Double voltage : nominalVoltages) {
                requestPatternBuilder.withQueryParam("nominalVoltages", WireMock.equalTo(voltage.toString()));
            }
        }

        return wireMock.stubFor(requestPatternBuilder
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkElementsInfosPost(UUID stubUuid, String networkUuid, String infoType, String elementType, String requestBody) {
        verifyPostRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements", false,
                Map.of(
                        QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType),
                        QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType)
                ),
                requestBody
        );
    }

    public UUID stubNetworkElementsIdsPost(String networkUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-ids"))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkElementsIdsPost(UUID stubUuid, String networkUuid, String requestBody) {
        verifyPostRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-ids", false,
                Map.of(),
                requestBody);
    }

    public UUID stubNetworkEquipmentsInfosGet(String networkUuid, String equipmentPath, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + equipmentPath))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkEquipmentsInfosGet(UUID stubUuid, String networkUuid, String equipmentPath) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + equipmentPath, Map.of());
    }

    public UUID stubNetworkEquipmentInfosGet(String networkUuid, String infoTypePath, String equipmentId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath + DELIMITER + equipmentId))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkEquipmentInfosGet(UUID stubUuid, String networkUuid, String infoTypePath, String equipmentId) {
        verifyGetRequest(stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + infoTypePath + DELIMITER + equipmentId, Map.of());
    }

    public UUID stubNetworkModificationCountGet(String groupUuid, Integer expectedCount) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathMatching(URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + groupUuid + "/network-modifications-count"))
            .withQueryParam(QUERY_PARAM_STASHED, WireMock.equalTo("false"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(String.valueOf(expectedCount)))
        ).getId();
    }

    public UUID stubNetworkModificationPost(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION))
            .willReturn(WireMock.ok()
                .withBody(responseBody)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
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

    public UUID stubDuplicateModificationGroup(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_MODIFICATION_GROUPS))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .withQueryParam("groupUuid", WireMock.matching(".*"))
            .willReturn(WireMock.ok().withBody(responseBody).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public UUID stubNetworkModificationDeleteGroup() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + ".*"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public UUID stubNetworkModificationDeleteIndex() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(URI_NETWORK_MODIFICATION + DELIMITER + "index.*"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyNetworkModificationCountsGet(UUID stubId, String groupUuid) {
        verifyGetRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + groupUuid + "/network-modifications-count", Map.of(QUERY_PARAM_STASHED, WireMock.equalTo("false")));
    }

    public void verifyNetworkModificationPost(UUID stubId, String requestBody) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION, false,
            Map.of("groupUuid", WireMock.matching(".*")),
            requestBody);
    }

    public void verifyNetworkModificationPostWithVariant(UUID stubId, String requestBody) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION, false,
            Map.of(),
            requestBody);
    }

    public void verifyNetworkModificationPut(UUID stubId, String modificationUuid, String requestBody) {
        verifyPutRequest(stubId, URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid, false, Map.of(), requestBody);
    }

    public void verifyDuplicateModificationGroup(UUID stubId, int nbRequests) {
        verifyPostRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS, Map.of("duplicateFrom", WireMock.matching(".*"), "groupUuid", WireMock.matching(".*")), nbRequests);
    }

    public void verifyPostRequest(UUID stubId, String urlPath, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(stubId, urlPath, queryParams, 1);
    }

    public void verifyNetworkModificationDeleteGroup(UUID stubId) {
        verifyDeleteRequest(stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + ".*", true, Map.of());
    }

    public void verifyNetworkModificationDeleteIndex(UUID stubId) {
        verifyNetworkModificationDeleteIndex(stubId, 1);
    }

    public void verifyNetworkModificationDeleteIndex(UUID stubId, int nbRequests) {
        verifyDeleteRequest(stubId, URI_NETWORK_MODIFICATION + DELIMITER + "index.*", true, Map.of(), nbRequests);
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
        verifyDeleteRequest(stubId, urlPath, regexMatching, queryParams, 1);
    }

    public void verifyDeleteRequest(UUID stubId, String urlPath, boolean regexMatching, Map<String, StringValuePattern> queryParams, int nbRequests) {
        RequestPatternBuilder requestBuilder = regexMatching ? WireMock.deleteRequestedFor(WireMock.urlPathMatching(urlPath)) : WireMock.deleteRequestedFor(WireMock.urlPathEqualTo(urlPath));
        verifyRequest(stubId, requestBuilder, queryParams, null, nbRequests);
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

    public UUID stubBranchOr3WTVoltageLevelIdGet(String networkUuid, String equipmentId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/branch-or-3wt/" + equipmentId + "/voltage-level-id"))
            .withQueryParam(QUERY_PARAM_SIDE, WireMock.equalTo(TwoSides.ONE.name()))
            .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.equalTo(FIRST_VARIANT_ID))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubHvdcLinesShuntCompensatorsGet(String networkUuid, String hvdcId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/hvdc-lines/" + hvdcId + "/shunt-compensators"))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubHvdcLinesShuntCompensatorsGetError(String networkUuid, String hvdcId) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/hvdc-lines/" + hvdcId + "/shunt-compensators"))
            .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
    }

    public void verifyHvdcLinesShuntCompensatorsGet(UUID stubUuid, String networkUuid, String hvdcId) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/hvdc-lines/" + hvdcId + "/shunt-compensators"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubUuid, 1);
    }

    public void verifyBranchOr3WTVoltageLevelIdGet(UUID stubUuid, String networkUuid, String equipmentId) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/branch-or-3wt/" + equipmentId + "/voltage-level-id"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubUuid, 1);
    }

    public UUID stubCaseExists(String caseUuid, boolean returnedValue) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists"))
                .willReturn(WireMock.ok().withBody(returnedValue ? "true" : "false")
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
        ).getId();
    }

    public void verifyCaseExists(UUID stubUuid, String caseUuid) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/exists"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(stubUuid, 1);
    }

    public UUID stubRunLoadFlow(UUID networkUuid, boolean withRatioTapChangers, UUID loadFlowResultUuid, String responseBody) {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/run-and-save"))
            .withQueryParam(QUERY_WITH_TAP_CHANGER, WireMock.equalTo(withRatioTapChangers ? "true" : "false"))
            .withQueryParam(QUERY_PARAM_RECEIVER, WireMock.matching(".*"));

        if (loadFlowResultUuid != null) {
            mappingBuilder.withQueryParam(QUERY_PARAM_RESULT_UUID, WireMock.equalTo(loadFlowResultUuid.toString()));
        }

        return wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(responseBody))).getId();
    }

    public UUID stubLoadFlowProvider(UUID parametersUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + parametersUuid + "/provider"))
                .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(responseBody))
        ).getId();
    }

    public void verifyRunLoadflow(UUID stubUuid, UUID networkUuid, boolean withRatioTapChangers, UUID loadFlowResultUuid) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put(QUERY_WITH_TAP_CHANGER, WireMock.equalTo(withRatioTapChangers ? "true" : "false"));
        params.put(QUERY_PARAM_RECEIVER, WireMock.matching(".*"));

        if (loadFlowResultUuid != null) {
            params.put(QUERY_PARAM_RESULT_UUID, WireMock.equalTo(loadFlowResultUuid.toString()));
        }

        verifyPostRequest(stubUuid, "/v1/networks/" + networkUuid + "/run-and-save", params);
    }

    public void verifyLoadFlowProviderGet(UUID loadFlowProviderStubUuid, UUID loadFlowParametersUuid) {
        verifyGetRequest(loadFlowProviderStubUuid, "/v1/parameters/" + loadFlowParametersUuid + "/provider", Map.of());
    }

    public UUID stubDeleteLoadFlowResults(List<UUID> loadFlowResultUuids) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/results"))
                .withQueryParam(QUERY_PARAM_RESULTS_UUIDS, WireMock.equalTo(String.join(",", loadFlowResultUuids.stream().map(UUID::toString).toList())))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyDeleteLoadFlowResults(UUID stubUuid, List<UUID> loadFlowResultUuids) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put(QUERY_PARAM_RESULTS_UUIDS, WireMock.equalTo(String.join(",", loadFlowResultUuids.stream().map(UUID::toString).toList())));

        verifyDeleteRequest(stubUuid, "/v1/results", false, params);
    }

    public UUID stubCreateRunningLoadflowStatus(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/results/running-status"))
            .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(responseBody))
        ).getId();
    }

    public void verifyCreateRunningLoadflowStatus(UUID stubUuid) {
        verifyPostRequest(stubUuid, "/v1/results/running-status", false, new HashMap<>(), null);
    }

    public UUID stubImportNetwork(String caseUuid, Map<String, Object> importParameters, String networkUuid, String networkId, String variantId, String caseFormat, String caseName, CountDownLatch countDownLatch) {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlPathEqualTo("/v1/networks"))
            .withQueryParam("caseUuid", WireMock.equalTo(caseUuid))
            .withQueryParam("receiver", WireMock.matching(".*"));
        if (variantId != null) {
            mappingBuilder = mappingBuilder.withQueryParam("variantId", WireMock.equalTo(variantId));
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

    public void verifyImportNetwork(UUID stubUuid, String caseUuid, String variantId) {
        HashMap<String, StringValuePattern> params = new HashMap<>();
        params.put("caseUuid", WireMock.equalTo(caseUuid));
        params.put("receiver", WireMock.matching(".*"));

        if (variantId != null) {
            params.put("variantId", WireMock.equalTo(variantId));
        }

        verifyPostRequest(stubUuid, "/v1/networks", params);
    }

    public UUID stubDisableCaseExpiration(String caseUuid) {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/cases/" + caseUuid + "/disableExpiration"))
                .willReturn(WireMock.ok())).getId();
    }

    public void verifyDisableCaseExpiration(UUID stubUuid, String caseUuid) {
        verifyPutRequest(stubUuid, "/v1/cases/" + caseUuid + "/disableExpiration", false, Map.of(), null);
    }

    public UUID stubCountriesGet(String networkUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/countries"))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubCountriesGetNotFoundError(String networkUuid) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/countries"))
                .willReturn(WireMock.notFound().withBody("Network not found"))
        ).getId();
    }

    public UUID stubCountriesGetError(String networkUuid) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/countries"))
                .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
    }

    public UUID stubSendReport() {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/reports/.*"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifySendReport(UUID stubUuid) {
        verifyPutRequest(stubUuid, "/v1/reports/.*", true, Map.of(), null);
    }

    public void verifyCountriesGet(UUID stubUuid, String networkUuid) {
        verifyGetRequest(stubUuid, "/v1/networks/" + networkUuid + "/countries", Map.of());
    }

    public UUID stubNominalVoltagesGet(String networkUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/nominal-voltages"))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubNominalVoltagesGetNotFoundError(String networkUuid) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/nominal-voltages"))
                .willReturn(WireMock.notFound().withBody("Network not found"))
        ).getId();
    }

    public UUID stubNominalVoltagesGetError(String networkUuid) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/nominal-voltages"))
                .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
    }

    public void verifyNominalVoltagesGet(UUID stubUuid, String networkUuid) {
        verifyGetRequest(stubUuid, "/v1/networks/" + networkUuid + "/nominal-voltages", Map.of());
    }

    public UUID stubFilterEvaluate(String networkUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/filters/evaluate"))
                .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubGlobalFilterEvaluate(String networkUuid, List<EquipmentType> equipmentTypes, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/global-filter"))
            .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
            .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.equalTo(""))
            .withQueryParam(QUERY_PARAM_EQUIPMENT_TYPES, WireMock.equalTo(String.join(",", equipmentTypes.stream().map(EquipmentType::name).toList())))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))
        ).getId();
    }

    public UUID stubFilterEvaluateNotFoundError(String networkUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/filters/evaluate"))
                .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
                .willReturn(WireMock.notFound().withBody("Network not found"))
        ).getId();
    }

    public UUID stubFilterEvaluateError(String networkUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/filters/evaluate"))
                .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
                .willReturn(WireMock.serverError().withBody("Internal Server Error"))
        ).getId();
    }

    public void verifyFilterEvaluate(UUID stubUuid, String networkUuid) {
        verifyPostRequest(stubUuid, "/v1/filters/evaluate",
                Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid)));
    }

    public void verifyGlobalFilterEvaluate(UUID stubUuid, String networkUuid, List<EquipmentType> equipmentTypes) {
        verifyPostRequest(stubUuid, "/v1/global-filter",
            Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid),
                QUERY_PARAM_VARIANT_ID, WireMock.equalTo(""),
                QUERY_PARAM_EQUIPMENT_TYPES, WireMock.equalTo(String.join(",", equipmentTypes.stream().map(EquipmentType::name).toList()))));
    }

    public UUID stubFilterExport(String networkUuid, String filterUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/filters/" + filterUuid + "/export"))
                .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
                .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubFiltersEvaluate(String networkUuid, String filtersBody, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/filters/evaluate/identifiables"))
            .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid))
                .withRequestBody(WireMock.equalToJson(filtersBody))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubFiltersExport(String networkUuid, List<String> filtersUuid, String responseBody) {
        MappingBuilder requestPatternBuilder = WireMock.get(WireMock.urlPathEqualTo("/v1/filters/export"))
            .withQueryParam(NETWORK_UUID, WireMock.equalTo(networkUuid));
        for (String filterUuid : filtersUuid) {
            requestPatternBuilder.withQueryParam(IDS, WireMock.equalTo(filterUuid));
        }
        return wireMock.stubFor(requestPatternBuilder.willReturn(WireMock.ok().withBody(responseBody))).getId();
    }

    public void verifyFilterExport(UUID stubUuid, String filterUuid, String networkUuid) {
        verifyGetRequest(stubUuid, "/v1/filters/" + filterUuid + "/export",
                Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid)));
    }

    public void verifyFiltersExport(UUID stubUuid, List<String> filtersUuid, String networkUuid) {
        Map<String, StringValuePattern> queryParams = new HashMap<>();
        queryParams.put(NETWORK_UUID, WireMock.equalTo(networkUuid));
        for (String filterUuid : filtersUuid) {
            queryParams.put(IDS, WireMock.equalTo(filterUuid));
        }
        verifyGetRequest(stubUuid, "/v1/filters/export", queryParams);
    }

    public void verifyFiltersEvaluate(UUID stubUuid, String filtersBody, String networkUuid) {
        Map<String, StringValuePattern> queryParams = new HashMap<>();
        queryParams.put(NETWORK_UUID, WireMock.equalTo(networkUuid));
        verifyPostRequest(stubUuid, "/v1/filters/evaluate/identifiables", false, queryParams, filtersBody);
    }

    public UUID stubSearchModifications(String networkUuid, String userInput, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/network-modifications/indexation-infos"))
                        .withQueryParam("networkUuid", WireMock.equalTo(networkUuid))
                        .withQueryParam("userInput", WireMock.equalTo(userInput))
                        .willReturn(WireMock.ok()
                                .withHeader("Content-Type", "application/json")
                                .withBody(responseBody)))
                .getId();
    }

    public void verifySearchModifications(UUID stubUuid, String networkUuid, String userInput) {
        verifyGetRequest(stubUuid, "/v1/network-modifications/indexation-infos",
                Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid),
                        "userInput", WireMock.equalTo(userInput)));
    }

    public UUID stubCreatePositionsFromCsv() {
        MappingBuilder mappingBuilder = WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_AREA_DIAGRAM))
                .withHeader("Content-Type", WireMock.containing("multipart/form-data"))
                .withMultipartRequestBody(WireMock.aMultipart()
                        .withName("file")
                        .withHeader("Content-Disposition", WireMock.containing("filename=\"positions.csv\""))
                )
                .withMultipartRequestBody(WireMock.aMultipart()
                        .withName("file_name")
                        .withBody(WireMock.equalTo("positions.csv"))
                );
        return wireMock.stubFor(mappingBuilder.willReturn(WireMock.ok().withHeader("Content-Type", "application/json"))).getId();
    }

    public void verifyStubCreatePositionsFromCsv(UUID stubUuid) {
        verifyPostRequest(stubUuid, URI_NETWORK_AREA_DIAGRAM, true, Map.of(), null);
    }
}
