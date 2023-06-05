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

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.IdentifiableInfos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.EQUIPMENT_NOT_FOUND;

@Service
public class NetworkMapService {

    static final String QUERY_PARAM_SUBSTATION_ID = "substationId";

    static final String QUERY_PARAM_SUBSTATIONS_IDS = "substationsIds";

    static final String QUERY_PARAM_LINE_ID = "lineId";

    @Autowired
    private RestTemplate restTemplate;

    private String networkMapServerBaseUri;

    @Autowired
    public NetworkMapService(
            @Value("${gridsuite.services.network-map-server.base-uri:http://network-map-server/}") String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }

    public String getElementsInfos(UUID networkUuid, String variantId, List<String> substationsIds, String elementType, String infoType) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATIONS_IDS, substationsIds);
        }
        builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        builder = builder.queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        builder = builder.queryParam(QUERY_PARAM_INFO_TYPE, infoType);
        String url = builder.buildAndExpand(networkUuid).toUriString();
        return restTemplate.getForObject(networkMapServerBaseUri + url, String.class);
    }

    public String getElementInfos(UUID networkUuid, String variantId, String elementType, String infoType, String elementId) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements/{elementId}";
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        builder = builder.queryParam(QUERY_PARAM_INFO_TYPE, infoType);
        String url = builder.buildAndExpand(networkUuid, elementId).toUriString();
        return restTemplate.getForObject(networkMapServerBaseUri + url, String.class);
    }

    public String getEquipmentsMapData(UUID networkUuid, String variantId, List<String> substationsIds,
            String equipmentPath) {
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

    public String getElementsIds(UUID networkUuid, String variantId, List<String> substationsIds, String elementType) {
        String path = DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/elements-ids";

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromPath(path);
        if (substationsIds != null) {
            builder = builder.queryParam(QUERY_PARAM_SUBSTATIONS_IDS, substationsIds);
        }
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        builder = builder.queryParam(QUERY_PARAM_ELEMENT_TYPE, elementType);
        String url = builder.buildAndExpand(networkUuid).toUriString();
        return restTemplate.getForObject(networkMapServerBaseUri + url, String.class);
    }

    public String getEquipmentMapData(UUID networkUuid, String variantId, String equipmentPath, String equipmentId) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(
                DELIMITER + NETWORK_MAP_API_VERSION + "/networks/{networkUuid}/" + equipmentPath + "/{equipmentUuid}");
        if (!StringUtils.isBlank(variantId)) {
            builder = builder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        String path = builder.buildAndExpand(networkUuid, equipmentId).toUriString();

        String equipmentMapData;
        try {
            equipmentMapData = restTemplate.getForObject(networkMapServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(EQUIPMENT_NOT_FOUND);
            } else {
                throw e;
            }
        }
        return equipmentMapData;
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

    public void setNetworkMapServerBaseUri(String networkMapServerBaseUri) {
        this.networkMapServerBaseUri = networkMapServerBaseUri;
    }
}
