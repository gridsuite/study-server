/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.powsybl.iidm.network.ThreeSides;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.dto.InfoTypeParameters;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class NetworkMapService {
    private final RestClient restClient;

    private String networkMapServerBaseUri;

    public NetworkMapService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        this.networkMapServerBaseUri = remoteServicesProperties.getServiceUri("network-map-server");
        this.restClient = restClient;
    }

    public String getElementSchema(String elementType, String infoType) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/schemas/{elementType}/{infoType}")
            .buildAndExpand(elementType, infoType).toUriString();
        return restClient.get().uri(networkMapServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String getElementsInfos(UUID networkUuid,
                                   String variantId,
                                   List<String> substationsIds,
                                   String elementType,
                                   List<Double> nominalVoltages,
                                   String infoType,
                                   Map<String, String> optionalParameters) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        if (nominalVoltages != null && !nominalVoltages.isEmpty()) {
            builder = builder.queryParam(QUERY_PARAM_NOMINAL_VOLTAGES, nominalVoltages);
        }
        queryParamInfoTypeParameters(InfoTypeParameters.builder()
                .optionalParameters(optionalParameters)
                .build(), builder);
        String url = builder.buildAndExpand(networkUuid).toUriString();
        var requestSpec = restClient.post().uri(networkMapServerBaseUri + url)
            .contentType(MediaType.APPLICATION_JSON);
        if (substationsIds != null) {
            requestSpec.body(substationsIds);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public String getElementInfos(UUID networkUuid,
                                  String variantId,
                                  String elementType,
                                  String infoType,
                                  Map<String, String> optionalParameters,
                                  String elementId) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements/{elementId}";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_INFO_TYPE, infoType)
            .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        queryParamInfoTypeParameters(InfoTypeParameters.builder()
            .optionalParameters(optionalParameters)
            .build(), builder);

        return restClient.get().uri(networkMapServerBaseUri + builder.build().toUriString(), networkUuid, elementId)
            .retrieve().body(String.class);
    }

    public String getElementsInfosByIds(UUID networkUuid,
                                        String variantId,
                                        String elementType,
                                        String infoType,
                                        Map<String, String> optionalParameters,
                                        List<String> elementIds) {
        if (elementIds == null || elementIds.isEmpty()) {
            return "[]";
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements-by-ids")
                .queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType)
                .queryParam(QUERY_PARAM_INFO_TYPE, infoType)
                .queryParamIfPresent(QUERY_PARAM_VARIANT_ID, Optional.ofNullable(variantId));
        queryParamInfoTypeParameters(InfoTypeParameters.builder()
                .optionalParameters(optionalParameters)
                .build(), builder);
        return restClient.post().uri(networkMapServerBaseUri + builder.buildAndExpand(networkUuid).toUriString())
            .body(elementIds)
            .retrieve().body(String.class);
    }

    public String getAllElementsInfos(UUID networkUuid,
                                      String variantId,
                                      List<String> substationsIds,
                                      Map<String, Map<String, String>> optionalParameters) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/all";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_INFO_TYPE, "TAB");
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        var requestSpec = restClient.post().uri(networkMapServerBaseUri + builder.buildAndExpand(networkUuid).toUriString())
            .contentType(MediaType.APPLICATION_JSON);
        if (optionalParameters != null) {
            requestSpec.body(optionalParameters);
        }
        return requestSpec.retrieve().body(String.class);
    }

    private void queryParamInfoTypeParameters(InfoTypeParameters infoTypesParameters, UriComponentsBuilder builder) {
        infoTypesParameters.getOptionalParameters().forEach((key, value) -> builder.queryParam(String.format(QUERY_FORMAT_OPTIONAL_PARAMS, key), value));
    }

    public String getCountries(UUID networkUuid, String variantId) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/countries";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restClient.get().uri(networkMapServerBaseUri + builder.build().toUriString(), networkUuid)
            .retrieve().body(String.class);
    }

    public String getNominalVoltages(UUID networkUuid, String variantId) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/nominal-voltages";
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            uriBuilder = uriBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restClient.get().uri(networkMapServerBaseUri + uriBuilder.build().toUriString(), networkUuid)
            .retrieve().body(String.class);
    }

    public String getElementsIds(UUID networkUuid, String variantId, List<String> substationsIds, String elementType, List<Double> nominalVoltages) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements-ids";

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        if (nominalVoltages != null && !nominalVoltages.isEmpty()) {
            builder = builder.queryParam(QUERY_PARAM_NOMINAL_VOLTAGES, nominalVoltages);
        }
        String url = builder.buildAndExpand(networkUuid).toUriString();
        var requestSpec = restClient.post().uri(networkMapServerBaseUri + url)
            .contentType(MediaType.APPLICATION_JSON);
        if (substationsIds != null) {
            requestSpec.body(substationsIds);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public String getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds, String equipmentPath) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath;
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromPath(path);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String url = builder.buildAndExpand(networkUuid).toUriString();
        return restClient.get().uri(networkMapServerBaseUri + url)
            .retrieve().body(String.class);
    }

    public String getBranchOr3WTVoltageLevelId(UUID networkUuid, String variantId, String equipmentId, ThreeSides side) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/branch-or-3wt/{equipmentId}/voltage-level-id")
            .queryParam(QUERY_PARAM_SIDE, side.name());
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();
        return restClient.get().uri(networkMapServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String getHvdcLineShuntCompensators(UUID networkUuid, String variantId, String hvdcId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(
                DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/hvdc-lines/{hvdcId}/shunt-compensators");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, hvdcId).toUriString();
        return restClient.get().uri(networkMapServerBaseUri + path)
            .retrieve().body(String.class);
    }

    public String getVoltageLevelSubstationId(UUID networkUuid, String variantId,
                                              String voltageLevelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/substation-id");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        return restClient.get().uri(networkMapServerBaseUri + builder.build().toUriString(), networkUuid, voltageLevelId)
            .retrieve().body(new ParameterizedTypeReference<String>() {
                });
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID networkUuid, String variantId,
                                                                        String voltageLevelId,
                                                                        String busPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restClient.get().uri(networkMapServerBaseUri + builder.build().toUriString(), networkUuid, voltageLevelId)
            .retrieve().body(new ParameterizedTypeReference<List<IdentifiableInfos>>() {
                });
    }

    public String getVoltageLevelTopologyInfos(UUID networkUuid, String variantId,
                                               String voltageLevelId,
                                               String path) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restClient.get().uri(networkMapServerBaseUri + builder.build().toUriString(), networkUuid, voltageLevelId)
            .retrieve().body(new ParameterizedTypeReference<String>() {
                });
    }

    public void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }
}
