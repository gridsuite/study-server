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
import org.gridsuite.study.server.RemoteServicesProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.service.NetworkMapService.QUERY_PARAM_LINE_ID;

@Service
public class GeoDataService {

    @Autowired
    private RestTemplate restTemplate;

    private String geoDataServerBaseUri;

    @Autowired
    public GeoDataService(RemoteServicesProperties remoteServicesProperties) {
        this.geoDataServerBaseUri = remoteServicesProperties.getServiceUri("geo-data-server");
    }

    public String getLinesGraphics(UUID networkUuid, String variantId, List<String> linesIds) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/lines")
                .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        if (linesIds != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_LINE_ID, linesIds);
        }

        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    public String getSubstationsGraphics(UUID networkUuid, String variantId, List<String> substationsIds) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/substations")
                .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        if (substationsIds != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_SUBSTATION_ID, substationsIds);
        }

        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        return restTemplate.getForObject(geoDataServerBaseUri + path, String.class);
    }

    public void setGeoDataServerBaseUri(String geoDataServerBaseUri) {
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }
}
