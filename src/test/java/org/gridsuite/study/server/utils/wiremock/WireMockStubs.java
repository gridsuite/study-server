/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.StringValuePattern;
import com.powsybl.iidm.network.TwoSides;
import org.gridsuite.filter.utils.EquipmentType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.dto.InfoTypeParameters.QUERY_PARAM_BUS_ID_TO_ICC_VALUES;
import static org.gridsuite.study.server.utils.wiremock.WireMockUtils.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public class WireMockStubs {
    private static final String DELIMITER = "/";

    public static final String URI_NETWORK_DATA = "/v1/networks";

    private static final String URI_NETWORK_MODIFICATION = "/v1/network-modifications";
    private static final String URI_NETWORK_AREA_DIAGRAM = "/v1/network-area-diagram/config/positions";

    private static final String URI_NETWORK_MODIFICATION_GROUPS = "/v1/groups";

    public static final String FIRST_VARIANT_ID = "first_variant_id";

    private final WireMockServer wireMock;
    public final CaseServerStubs caseServer;
    public final NetworkConversionServerStubs networkConversionServer;

    public WireMockStubs(WireMockServer wireMock) {
        this.wireMock = wireMock;
        this.caseServer = new CaseServerStubs(wireMock);
        this.networkConversionServer = new NetworkConversionServerStubs(wireMock);
    }

    public UUID stubNetworkElementInfosGet(String networkUuid, String elementType, String infoType, String elementId, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public UUID stubNetworkElementInfosWithIccByBusIdValuesGet(String networkUuid, String elementType, String infoType, String elementId, String iccByBusIdValues, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId))
            .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
            .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
            .withQueryParam("optionalParameters[" + QUERY_PARAM_BUS_ID_TO_ICC_VALUES + "]", WireMock.equalTo(iccByBusIdValues))
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
        verifyGetRequest(wireMock, stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId,
            Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType),
                   QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType)));
    }

    public void verifyNetworkElementInfosWithIccByBusIdValuesGet(UUID stubUuid, String networkUuid, String elementType, String infoType, String elementId, String iccByBusIdValues) {
        verifyGetRequest(wireMock, stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements" + DELIMITER + elementId,
            Map.of(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType),
                QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType),
                "optionalParameters[" + QUERY_PARAM_BUS_ID_TO_ICC_VALUES + "]", WireMock.equalTo(iccByBusIdValues)));
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
        verifyPostRequest(wireMock, stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements", false,
                Map.of(
                        QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType),
                        QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType)
                ),
                requestBody
        );
    }

    public UUID stubNetworkElementsByIdsPost(String networkUuid, String elementType, String infoType, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo(URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-by-ids"))
                .withQueryParam(QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType))
                .withQueryParam(QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType))
                .willReturn(WireMock.ok()
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody(responseBody))
        ).getId();
    }

    public void verifyNetworkElementsByIdsPost(UUID stubUuid, String networkUuid, String elementType, String infoType, String requestBody) {
        verifyPostRequest(wireMock, stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-by-ids", false,
                Map.of(
                        QUERY_PARAM_ELEMENT_TYPE, WireMock.equalTo(elementType),
                        QUERY_PARAM_INFO_TYPE, WireMock.equalTo(infoType)
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
        verifyPostRequest(wireMock, stubUuid, URI_NETWORK_DATA + DELIMITER + networkUuid + DELIMITER + "elements-ids", false,
                Map.of(),
                requestBody);
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
        verifyGetRequest(wireMock, stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + groupUuid + "/network-modifications-count", Map.of(QUERY_PARAM_STASHED, WireMock.equalTo("false")));
    }

    public void verifyNetworkModificationPost(String requestBody) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, URI_NETWORK_MODIFICATION, false,
            Map.of("groupUuid", WireMock.matching(".*")),
            requestBody);
    }

    public void verifyNetworkModificationPostWithVariant(String requestBody) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, URI_NETWORK_MODIFICATION, false,
            Map.of("groupUuid", WireMock.matching(".*")),
            requestBody);
    }

    public void verifyNetworkModificationPut(UUID stubId, String modificationUuid, String requestBody) {
        verifyPutRequest(wireMock, stubId, URI_NETWORK_MODIFICATION + DELIMITER + modificationUuid, false, Map.of(), requestBody);
    }

    public void verifyDuplicateModificationGroup(UUID stubId, int nbRequests) {
        verifyPostRequest(wireMock, stubId, URI_NETWORK_MODIFICATION_GROUPS, Map.of("duplicateFrom", WireMock.matching(".*"), "groupUuid", WireMock.matching(".*")), nbRequests);
    }

    public void verifyNetworkModificationDeleteGroup(UUID stubId, boolean errorOnGroupNotFound) {
        verifyDeleteRequest(wireMock, stubId, URI_NETWORK_MODIFICATION_GROUPS + DELIMITER + ".*", true, Map.of("errorOnGroupNotFound", WireMock.equalTo(String.valueOf(errorOnGroupNotFound))));
    }

    public void verifyNetworkModificationDeleteIndex(UUID stubId) {
        verifyNetworkModificationDeleteIndex(stubId, 1);
    }

    public void verifyNetworkModificationDeleteIndex(UUID stubId, int nbRequests) {
        verifyDeleteRequest(wireMock, stubId, URI_NETWORK_MODIFICATION + DELIMITER + "index.*", true, Map.of("networkUuid", WireMock.matching(".*"), "groupUuids", WireMock.matching(".*")), nbRequests);
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
        removeRequestForStub(wireMock, stubUuid, 1);
    }

    public void verifyBranchOr3WTVoltageLevelIdGet(UUID stubUuid, String networkUuid, String equipmentId) {
        RequestPatternBuilder requestBuilder = WireMock.getRequestedFor(WireMock.urlPathEqualTo("/v1/networks/" + networkUuid + "/branch-or-3wt/" + equipmentId + "/voltage-level-id"));
        wireMock.verify(1, requestBuilder);
        removeRequestForStub(wireMock, stubUuid, 1);
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

        verifyPostRequest(wireMock, stubUuid, "/v1/networks/" + networkUuid + "/run-and-save", params);
    }

    public void verifyLoadFlowProviderGet(UUID loadFlowProviderStubUuid, UUID loadFlowParametersUuid) {
        verifyGetRequest(wireMock, loadFlowProviderStubUuid, "/v1/parameters/" + loadFlowParametersUuid + "/provider", Map.of());
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

        verifyDeleteRequest(wireMock, stubUuid, "/v1/results", false, params);
    }

    public UUID stubCreateRunningLoadflowStatus(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/results/running-status"))
            .willReturn(WireMock.ok().withHeader("Content-Type", "application/json").withBody(responseBody))
        ).getId();
    }

    public void verifyCreateRunningLoadflowStatus(UUID stubUuid) {
        verifyPostRequest(wireMock, stubUuid, "/v1/results/running-status", false, new HashMap<>(), null);
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

    public UUID stubDeleteReport() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
    }

    public UUID stubSendReport() {
        return wireMock.stubFor(WireMock.put(WireMock.urlPathMatching("/v1/reports/.*"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifySendReport(UUID stubUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/reports/.*", true, Map.of(), null);
    }

    public void verifyDeleteReport(UUID stubUuid) {
        verifyDeleteRequest(wireMock, stubUuid, "/v1/reports", false, Map.of());
    }

    public void verifyCountriesGet(UUID stubUuid, String networkUuid) {
        verifyGetRequest(wireMock, stubUuid, "/v1/networks/" + networkUuid + "/countries", Map.of());
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
        verifyGetRequest(wireMock, stubUuid, "/v1/networks/" + networkUuid + "/nominal-voltages", Map.of());
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
        verifyPostRequest(wireMock, stubUuid, "/v1/filters/evaluate",
                Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid)));
    }

    public void verifyGlobalFilterEvaluate(UUID stubUuid, String networkUuid, List<EquipmentType> equipmentTypes) {
        verifyPostRequest(wireMock, stubUuid, "/v1/global-filter",
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
        verifyGetRequest(wireMock, stubUuid, "/v1/filters/" + filterUuid + "/export",
                Map.of(NETWORK_UUID, WireMock.equalTo(networkUuid)));
    }

    public void verifyFiltersExport(UUID stubUuid, List<String> filtersUuid, String networkUuid) {
        Map<String, StringValuePattern> queryParams = new HashMap<>();
        queryParams.put(NETWORK_UUID, WireMock.equalTo(networkUuid));
        for (String filterUuid : filtersUuid) {
            queryParams.put(IDS, WireMock.equalTo(filterUuid));
        }
        verifyGetRequest(wireMock, stubUuid, "/v1/filters/export", queryParams);
    }

    public void verifyFiltersEvaluate(UUID stubUuid, String filtersBody, String networkUuid) {
        Map<String, StringValuePattern> queryParams = new HashMap<>();
        queryParams.put(NETWORK_UUID, WireMock.equalTo(networkUuid));
        verifyPostRequest(wireMock, stubUuid, "/v1/filters/evaluate/identifiables", false, queryParams, filtersBody);
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
        verifyGetRequest(wireMock, stubUuid, "/v1/network-modifications/indexation-infos",
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
        verifyPostRequest(wireMock, stubUuid, URI_NETWORK_AREA_DIAGRAM, true, Map.of(), null);
    }

    public UUID stubPccMinRun(String networkUuid, String variantId, String resultUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save.*"))
            .withQueryParam("variantId", WireMock.equalTo(variantId))
            .willReturn(WireMock.okJson("\"" + resultUuid + "\""))

        ).getId();
    }

    public UUID stubComputationRun(String networkUuid, String variantId, String resultUuid) {
        MappingBuilder builder = WireMock.post(
            WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save.*")
        );
        if (variantId != null) {
            builder = builder.withQueryParam("variantId", WireMock.equalTo(variantId));
        }
        return wireMock.stubFor(
            builder.willReturn(WireMock.okJson("\"" + resultUuid + "\""))
        ).getId();
    }

    public void verifyComputationRun(UUID stubUuid, String networkUuid, Map<String, StringValuePattern> queryParams) {
        verifyPostRequest(
            wireMock,
            stubUuid,
            "/v1/networks/" + networkUuid + "/run-and-save",
            queryParams);
    }

    public UUID stubResultsCount(int count) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo(
                "/v1/supervision/results-count"))
            .willReturn(WireMock.okJson(String.valueOf(count)))
        ).getId();
    }

    public UUID stubDeleteResults(String path) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathMatching(path))
            .withQueryParam("resultsUuids", matching(".*"))
            .willReturn(WireMock.ok())
        ).getId();
    }

    public void verifyResultsCountGet(UUID stubId) {
        verifyGetRequest(wireMock, stubId, "/v1/supervision/results-count", Map.of());
    }

    public UUID stubComputationStatusGet(String resultUuid, String statusJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/results/" + resultUuid + "/status"))
            .willReturn(WireMock.okJson(statusJson))
        ).getId();
    }

    public void verifyPccMinRun(UUID stubUuid, String networkUuid, String variantId) {
        verifyPostRequest(wireMock, stubUuid, "/v1/networks/" + networkUuid + "/run-and-save",
            Map.of("variantId", WireMock.equalTo(variantId)));
    }

    public void verifyComputationStop(UUID stubUuid, String resultUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/results/" + resultUuid + "/stop", false, Map.of(), null);
    }

    public void verifyPccMinStop(UUID stubUuid, String resultUuid) {
        verifyPutRequest(wireMock, stubUuid, "/v1/results/" + resultUuid + "/stop", true, Map.of(), null);
    }

    public UUID stubPccMinFailed(String networkUuid, String variantId, String resultUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathMatching("/v1/networks/" + networkUuid + "/run-and-save.*"))
            .withQueryParam("variantId", WireMock.equalTo(variantId))
            .willReturn(WireMock.okJson("\"" + resultUuid + "\""))
        ).getId();
    }

    public void verifyPccMinFail(UUID stubUuid, String networkUuid, String variantId) {
        verifyPostRequest(
            wireMock,
            stubUuid,
            "/v1/networks/" + networkUuid + "/run-and-save",
            true,
            Map.of("variantId", WireMock.equalTo(variantId)),
            null,
            1
        );
    }

    public void verifyComputationStatus(UUID stubUuid, String resultUuid) {
        verifyGetRequest(wireMock, stubUuid, "/v1/results/" + resultUuid + "/status", Map.of());
    }

    public UUID stubPagedPccMinResult(String resultUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid))
            .withQueryParam("page", WireMock.equalTo("0"))
            .withQueryParam("size", WireMock.equalTo("20"))
            .withQueryParam("sort", WireMock.equalTo("id,DESC"))
            .withQueryParam("filters", WireMock.equalTo("fakeFilters"))
            .withQueryParam("globalFilters", WireMock.equalTo("fakeGlobalFilters"))
            .willReturn(WireMock.okJson(responseBody))
        ).getId();
    }

    public void verifyPccMinPagedGet(UUID stubId, String resultUuid) {
        verifyGetRequest(
            wireMock,
            stubId,
            "/v1/results/" + resultUuid,
            Map.of(
                "page", WireMock.equalTo("0"),
                "size", WireMock.equalTo("20"),
                "sort", WireMock.equalTo("id,DESC"),
                "filters", WireMock.equalTo("fakeFilters"),
                "globalFilters", WireMock.equalTo("fakeGlobalFilters")
            ));
    }

    public UUID stubGenerateSvg(UUID networkUuid, String variantId, String voltageLevelId, String body) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/svg/" + networkUuid + "/" + voltageLevelId))
            .withQueryParam(QUERY_PARAM_VARIANT_ID, WireMock.equalTo(variantId))
            .withRequestBody(WireMock.equalTo(body))
            .willReturn(WireMock.ok("generatedSvg")))
            .getId();
    }

    public void verifyGenerateSvg(UUID stubId, UUID networkUuid, String variantId, String voltageLevelId, String body) {
        verifyPostRequest(wireMock, stubId,
            "/v1/svg/" + networkUuid + "/" + voltageLevelId,
            false,
            Map.of(
                QUERY_PARAM_VARIANT_ID, WireMock.equalTo(variantId)
            ),
            body);
    }

    public UUID stubGetVoltageLevelIccValues(UUID resultUuid, String voltageLevelId, String expectedBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/fault_results/icc"))
            .withQueryParam("voltageLevelId", WireMock.equalTo(voltageLevelId))
            .willReturn(WireMock.okJson(expectedBody))
        ).getId();
    }

    public void verifyGetVoltageLevelIccValues(UUID stubUuid, UUID resultUuid, String voltageLevelId) {
        verifyGetRequest(wireMock, stubUuid, "/v1/results/" + resultUuid + "/fault_results/icc", Map.of("voltageLevelId", WireMock.equalTo(voltageLevelId)));
    }

    public void verifyExportPccMinResult(UUID stubId, UUID resultUuid) {
        verifyPostRequest(
            wireMock, stubId,
            "/v1/results/" + resultUuid + "/csv",
            Map.of(
                "sort", WireMock.equalTo("id,DESC"),
                "filters", WireMock.equalTo("fakeFilters"),
                "globalFilters", WireMock.equalTo("fakeGlobalFilters")
            ),
            1
        );
    }

    public UUID stubCreateComputationParameter(UUID paramUuid) {
        return
            wireMock.stubFor(
                post(urlPathEqualTo("/v1/parameters"))
                    .willReturn(okJson("\"" + paramUuid + "\""))
            ).getId();
    }

    public void verifyComputationParameterPost(UUID stubUuid, String bodyJson) {
        verifyPostRequest(wireMock, stubUuid, "/v1/parameters", false, Map.of(), bodyJson);
    }

    public UUID stubUserProfilesGet(String userId, String responseBody) {
        return wireMock.stubFor(
            get(urlPathEqualTo("/v1/users/" + userId + "/profile"))
                .willReturn(okJson(responseBody))
        ).getId();
    }

    public void verifyDuplicateParameters(String paramUUID) {
        wireMock.verify(
            postRequestedFor(urlPathEqualTo("/v1/parameters"))
                .withQueryParam("duplicateFrom", equalTo(paramUUID))
        );
    }

    public void verifyUserProfileGet(UUID stubUuid, String useProfilel) {
        verifyGetRequest(wireMock, stubUuid, "/v1/users/" + useProfilel + "/profile", Map.of());
    }

    public void stubParameterPut(WireMockServer wireMockServer, String paramUuid, String responseJson) {
        wireMockServer.stubFor(WireMock.put(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
            .willReturn(WireMock.okJson(responseJson))
        );
    }

    public void verifyParameterPut(WireMockServer wireMockServer, String paramUuid) {
        wireMockServer.verify(
            putRequestedFor(urlEqualTo("/v1/parameters/" + paramUuid))
        );
    }

    public UUID stubPccMinParametersGet(String paramUuid, String responseBody) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/parameters/" + paramUuid))
            .willReturn(WireMock.ok().withBody(responseBody))
        ).getId();
    }

    public void verifyPccMinParametersGet(UUID stubUuid, String paramUuid) {
        verifyGetRequest(wireMock, stubUuid, "/v1/parameters/" + paramUuid, Map.of());
    }

    public UUID stubDeleteReports() {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/reports"))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteReports(UUID stubId) {
        verifyDeleteRequest(wireMock, stubId, "/v1/reports", false, Map.of());
    }

    public UUID stubDeleteResult(String resultUuid) {
        return wireMock.stubFor(WireMock.delete(WireMock.urlPathEqualTo("/v1/results"))
            .withQueryParam("resultsUuids", WireMock.equalTo(resultUuid))
            .willReturn(WireMock.ok())).getId();
    }

    public void verifyDeleteResult(UUID stubId, String resultUuid) {
        verifyDeleteRequest(wireMock, stubId, "/v1/results", false, Map.of("resultsUuids", WireMock.equalTo(resultUuid)));
    }

    public UUID stubGetResultStatus(String resultUuid, String statusJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/results/" + resultUuid + "/status"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(statusJson))).getId();
    }

    public void verifyGetResultStatus(UUID stubId, String resultUuid) {
        verifyGetResultStatus(stubId, resultUuid, 1);
    }

    public void verifyGetResultStatus(UUID stubId, String resultUuid, int nbRequests) {
        verifyGetRequest(wireMock, stubId, "/v1/results/" + resultUuid + "/status", Map.of(), nbRequests);
    }

    public void verifyDeleteReports(UUID stubId, int nbRequests) {
        verifyDeleteRequest(wireMock, stubId, "/v1/reports", false, Map.of(), nbRequests);
    }

    public void verifyDeleteParameters(UUID stubId, int nbRequests) {
        verifyDeleteRequest(wireMock, stubId, "/v1/parameters/.*", true, Map.of(), nbRequests);
    }

    public void verifyDeleteNetworkVisualizationParams(UUID stubId) {
        verifyDeleteRequest(wireMock, stubId, "/v1/network-visualizations-params/.*", true, Map.of());
    }

    public void verifyDeleteSpreadsheetConfigCollection(UUID stubId) {
        verifyDeleteRequest(wireMock, stubId, "/v1/spreadsheet-config-collections/.*", true, Map.of());
    }

    public UUID stubGetReportsLogs(String body) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathMatching("/v1/reports/.*/logs.*"))
            .willReturn(WireMock.ok()
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(body))).getId();
    }

    public void verifyGetReport() {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/.*", true,
            Map.of("severityLevels", WireMock.matching(".*"), "defaultName", WireMock.matching(".*")), 1);
    }

    public void verifyGetReportLogs(String reportId) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs", Map.of());
    }

    public void verifyGetReportLogs(String reportId, String severityLevels, String message) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs",
            Map.of("severityLevels", WireMock.equalTo(severityLevels), "message", WireMock.equalTo(message)));
    }

    public void verifyGetReportLogsPaged(String reportId, int page, int size) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs",
            Map.of("paged", WireMock.equalTo("true"), "page", WireMock.equalTo(String.valueOf(page)), "size", WireMock.equalTo(String.valueOf(size))));
    }

    public void verifyGetReportLogsPaged(String reportId, int page, int size, String severityLevels, String message) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs",
            Map.of("paged", WireMock.equalTo("true"), "page", WireMock.equalTo(String.valueOf(page)), "size", WireMock.equalTo(String.valueOf(size)),
                "severityLevels", WireMock.equalTo(severityLevels), "message", WireMock.equalTo(message)));
    }

    public void verifyGetReportLogsSearchWithReportId(String reportId, String searchTerm, int pageSize) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs/search",
            Map.of("searchTerm", WireMock.equalTo(searchTerm), "pageSize", WireMock.equalTo(String.valueOf(pageSize))));
    }

    public void verifyGetReportLogsSearchWithReportId(String reportId, String searchTerm, int pageSize, String severityLevels, String message) {
        WireMockUtilsCriteria.verifyGetRequest(wireMock, "/v1/reports/" + reportId + "/logs/search",
            Map.of("searchTerm", WireMock.equalTo(searchTerm), "pageSize", WireMock.equalTo(String.valueOf(pageSize)),
                "severityLevels", WireMock.equalTo(severityLevels), "message", WireMock.equalTo(message)));
    }

    public void verifyGetReportLogsSearch(UUID stubId, List<UUID> reportIds) {
        StringValuePattern[] reportIdMatchers = reportIds.stream()
            .map(uuid -> WireMock.equalTo(uuid.toString()))
            .toArray(StringValuePattern[]::new);
        verifyGetRequestWithMultiValueParams(wireMock, stubId, "/v1/reports/logs/search",
            Map.of("reportIds", WireMock.havingExactly(reportIdMatchers)));
    }

    public void verifyGetReportLogs(UUID stubId, List<UUID> reportIds) {
        StringValuePattern[] reportIdMatchers = reportIds.stream()
            .map(uuid -> WireMock.equalTo(uuid.toString()))
            .toArray(StringValuePattern[]::new);
        verifyGetRequestWithMultiValueParams(wireMock, stubId, "/v1/reports/logs",
            Map.of("reportIds", WireMock.havingExactly(reportIdMatchers)));
    }

    public void verifyReindexAll(UUID stubId, String networkUuid) {
        verifyPostRequest(wireMock, stubId, "/v1/networks/" + networkUuid + "/reindex-all", Map.of());
    }

    public void verifyIndexedEquipments(UUID stubId, String networkUuid) {
        verifyHeadRequest(wireMock, stubId, "/v1/networks/" + networkUuid + "/indexed-equipments", Map.of());
    }

    public void verifyIndexedEquipments(UUID stubId, String networkUuid, int nbRequests) {
        verifyHeadRequest(wireMock, stubId, "/v1/networks/" + networkUuid + "/indexed-equipments", Map.of(), nbRequests);
    }

    public void verifyParametersProvider(UUID stubId, int nbRequests) {
        verifyPutRequest(wireMock, stubId, "/v1/parameters/.*/provider", true, Map.of(), null, nbRequests);
    }

    public void verifyDefaultProvider(UUID stubId, int nbRequests) {
        verifyGetRequest(wireMock, stubId, "/v1/default-provider", Map.of(), nbRequests);
    }

    public void verifyReportsDuplicate(UUID stubId) {
        verifyPostRequest(wireMock, stubId, "/v1/reports/.*/duplicate", true, Map.of(), null);
    }

    public void verifyUserProfile(UUID stubId, String userId) {
        verifyGetRequest(wireMock, stubId, "/v1/users/" + userId + "/profile", Map.of());
    }

    public UUID stubUserProfile(String userId) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + userId + "/profile"))
            .willReturn(WireMock.ok())).getId();
    }

    public UUID stubUserProfile(String userId, String profileJson) {
        return wireMock.stubFor(WireMock.get(WireMock.urlPathEqualTo("/v1/users/" + userId + "/profile"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(profileJson))).getId();
    }

    public UUID stubParameters(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/v1/parameters"))
                .atPriority(10)
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubParametersDefault(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters/default"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubSpreadsheetConfigDefault(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/spreadsheet-config-collections/default"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubNetworkVisualizationParamsDefault(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-visualizations-params/default"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubParametersDuplicateFrom(String duplicateFromUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubParametersDuplicateFromNotFound(String duplicateFromUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.notFound())).getId();
    }

    public UUID stubSpreadsheetConfigDuplicateFrom(String duplicateFromUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/spreadsheet-config-collections"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubSpreadsheetConfigDuplicateFromNotFound(String duplicateFromUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/spreadsheet-config-collections"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.notFound())).getId();
    }

    public UUID stubNetworkVisualizationParamsDuplicateFrom(String duplicateFromUuid, String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-visualizations-params"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubNetworkVisualizationParamsDuplicateFromNotFound(String duplicateFromUuid) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-visualizations-params"))
            .withQueryParam("duplicateFrom", WireMock.equalTo(duplicateFromUuid))
            .willReturn(WireMock.notFound())).getId();
    }

    public UUID stubParametersDuplicateFromAny(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/parameters"))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .atPriority(10)
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubSpreadsheetConfigDuplicateFromAny(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/spreadsheet-config-collections"))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public UUID stubNetworkVisualizationParamsDuplicateFromAny(String responseBody) {
        return wireMock.stubFor(WireMock.post(WireMock.urlPathEqualTo("/v1/network-visualizations-params"))
            .withQueryParam("duplicateFrom", WireMock.matching(".*"))
            .willReturn(WireMock.ok().withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).withBody(responseBody))).getId();
    }

    public void verifyParameters(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of(), nbRequests);
    }

    public void verifyParametersDefault(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters/default", Map.of(), nbRequests);
    }

    public void verifyParametersDuplicateFrom(String duplicateFromUuid) {
        verifyParametersDuplicateFrom(duplicateFromUuid, 1);
    }

    public void verifyParametersDuplicateFrom(String duplicateFromUuid, int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/parameters", Map.of("duplicateFrom", WireMock.equalTo(duplicateFromUuid)), nbRequests);
    }

    public void verifySpreadsheetConfigDefault(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/spreadsheet-config-collections/default", Map.of(), nbRequests);
    }

    public void verifySpreadsheetConfigDuplicateFrom(UUID stubId, String duplicateFromUuid) {
        verifySpreadsheetConfigDuplicateFrom(stubId, duplicateFromUuid, 1);
    }

    public void verifySpreadsheetConfigDuplicateFrom(UUID stubId, String duplicateFromUuid, int nbRequests) {
        verifyPostRequest(wireMock, stubId, "/v1/spreadsheet-config-collections", Map.of("duplicateFrom", WireMock.equalTo(duplicateFromUuid)), nbRequests);
    }

    public void verifyNetworkVisualizationParamsDefault(int nbRequests) {
        WireMockUtilsCriteria.verifyPostRequest(wireMock, "/v1/network-visualizations-params/default", Map.of(), nbRequests);
    }

    public void verifyNetworkVisualizationParamsDuplicateFrom(UUID stubId, String duplicateFromUuid) {
        verifyNetworkVisualizationParamsDuplicateFrom(stubId, duplicateFromUuid, 1);
    }

    public void verifyNetworkVisualizationParamsDuplicateFrom(UUID stubId, String duplicateFromUuid, int nbRequests) {
        verifyPostRequest(wireMock, stubId, "/v1/network-visualizations-params", Map.of("duplicateFrom", WireMock.equalTo(duplicateFromUuid)), nbRequests);
    }

    public void verifyParametersDuplicateFromAny(UUID stubId, int nbRequests) {
        verifyPostRequest(wireMock, stubId, "/v1/parameters", Map.of("duplicateFrom", WireMock.matching(".*")), nbRequests);
    }

    public void verifySpreadsheetConfigDuplicateFromAny(UUID stubId, int nbRequests) {
        verifyPostRequest(wireMock, stubId, "/v1/spreadsheet-config-collections", Map.of("duplicateFrom", WireMock.matching(".*")), nbRequests);
    }

    public void verifyNetworkVisualizationParamsDuplicateFromAny(UUID stubId, int nbRequests) {
        verifyPostRequest(wireMock, stubId, "/v1/network-visualizations-params", Map.of("duplicateFrom", WireMock.matching(".*")), nbRequests);
    }

    public void verifyNetworkAreaDiagramConfig(UUID stubId) {
        verifyPostRequest(wireMock, stubId, "/v1/network-area-diagram/config", Map.of("duplicateFrom", WireMock.matching(".*")));
    }

    public void verifyDiagramGridLayout(UUID stubId) {
        verifyPostRequest(wireMock, stubId, "/v1/diagram-grid-layout", Map.of());
    }

    public void verifyElementNameGet(UUID stubId, String elementUuid) {
        verifyGetRequest(wireMock, stubId, "/v1/elements/" + elementUuid + "/name", Map.of());
    }
}
