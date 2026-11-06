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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class GeoDataService {

    private final RestClient restClient;

    private String geoDataServerBaseUri;

    public GeoDataService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        this.geoDataServerBaseUri = remoteServicesProperties.getServiceUri("geo-data-server");
        this.restClient = restClient;
    }

    public String getLinesGraphics(UUID networkUuid, String variantId, List<String> linesIds) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/lines/infos")
                .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();
        var requestSpec = restClient.post().uri(geoDataServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (linesIds != null) {
            requestSpec.body(linesIds);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public String getSubstationsGraphics(UUID networkUuid, String variantId, List<String> substationsIds) {
        var uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + GEO_DATA_API_VERSION + "/substations/infos")
                .queryParam(NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder
                .buildAndExpand()
                .toUriString();

        var requestSpec = restClient.post().uri(geoDataServerBaseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (substationsIds != null) {
            requestSpec.body(substationsIds);
        }
        return requestSpec.retrieve().body(String.class);
    }

    public void setGeoDataServerBaseUri(String geoDataServerBaseUri) {
        this.geoDataServerBaseUri = geoDataServerBaseUri;
    }
}
