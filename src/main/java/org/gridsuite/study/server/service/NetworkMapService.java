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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.gridsuite.study.server.dto.InfoTypeParameters;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

@Service
public class NetworkMapService {
    private final RestTemplate restTemplate;

    private String networkMapServerBaseUri;

    public NetworkMapService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.networkMapServerBaseUri = remoteServicesProperties.getServiceUri("network-map-server");
        this.restTemplate = restTemplate;
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> httpEntity = new HttpEntity<>(substationsIds, headers);
        return restTemplate.postForObject(networkMapServerBaseUri + url, httpEntity, String.class);
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

        try {
            return restTemplate.getForObject(networkMapServerBaseUri + builder.build().toUriString(), String.class, networkUuid, elementId);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(EQUIPMENT_NOT_FOUND);
            }
            if (HttpStatus.NOT_IMPLEMENTED.equals(e.getStatusCode())) {
                throw new StudyException(StudyException.Type.NOT_IMPLEMENTED, e.getMessage());
            }
            throw handleHttpError(e, GET_NETWORK_ELEMENT_FAILED);
        }
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<?> httpEntity = new HttpEntity<>(optionalParameters, headers);
        return restTemplate.postForObject(networkMapServerBaseUri + builder.buildAndExpand(networkUuid).toUriString(), httpEntity, String.class);
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

        try {
            return restTemplate.getForObject(networkMapServerBaseUri + builder.build().toUriString(), String.class, networkUuid);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, GET_NETWORK_COUNTRY_FAILED);
            }
        }
    }

    public String getNominalVoltages(UUID networkUuid, String variantId) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/nominal-voltages";
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            uriBuilder = uriBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        try {
            return restTemplate.getForObject(networkMapServerBaseUri + uriBuilder.build().toUriString(), String.class, networkUuid);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, GET_NETWORK_NOMINAL_VOLTAGES_FAILED);
            }
        }
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> httpEntity = new HttpEntity<>(substationsIds, headers);
        return restTemplate.postForObject(networkMapServerBaseUri + url, httpEntity, String.class);
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
        return restTemplate.getForObject(networkMapServerBaseUri + url, String.class);
    }

    public String getBranchOr3WTVoltageLevelId(UUID networkUuid, String variantId, String equipmentId, ThreeSides side) {
        UriComponentsBuilder builder = UriComponentsBuilder
            .fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/branch-or-3wt/{equipmentId}/voltage-level-id")
            .queryParam(QUERY_PARAM_SIDE, side.name());
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();
        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_NETWORK_ELEMENT_FAILED);
        }
        return equipmentMapData;
    }

    public String getHvdcLineShuntCompensators(UUID networkUuid, String variantId, String hvdcId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(
                DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/hvdc-lines/{hvdcId}/shunt-compensators");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, hvdcId).toUriString();
        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_NETWORK_ELEMENT_FAILED);
        }
        return equipmentMapData;
    }

    public String getVoltageLevelSubstationId(UUID networkUuid, String variantId,
                                              String voltageLevelId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/substation-id");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        return restTemplate.exchange(networkMapServerBaseUri + builder.build().toUriString(), HttpMethod.GET, null,
                new ParameterizedTypeReference<String>() {
                }, networkUuid, voltageLevelId).getBody();
    }

    public List<IdentifiableInfos> getVoltageLevelBusesOrBusbarSections(UUID networkUuid, String variantId,
                                                                        String voltageLevelId,
                                                                        String busPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + busPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restTemplate.exchange(networkMapServerBaseUri + builder.build().toUriString(), HttpMethod.GET, null,
                new ParameterizedTypeReference<List<IdentifiableInfos>>() {
                }, networkUuid, voltageLevelId).getBody();
    }

    public String getVoltageLevelSwitches(UUID networkUuid, String variantId,
                                                           String voltageLevelId,
                                                           String switchesPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION
                + "/networks/{networkUuid}/voltage-levels/{voltageLevelId}/" + switchesPath);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        return restTemplate.exchange(networkMapServerBaseUri + builder.build().toUriString(), HttpMethod.GET, null,
                new ParameterizedTypeReference<String>() {
                }, networkUuid, voltageLevelId).getBody();
    }

    public void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }
}
