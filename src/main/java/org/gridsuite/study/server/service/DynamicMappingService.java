/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DYNAMIC_MAPPING_API_VERSION;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMappingService {
    public static final String API_VERSION = DYNAMIC_MAPPING_API_VERSION;
    public static final String DYNAMIC_MAPPING_END_POINT_NETWORK = "/network";
    public static final String NETWORK_MATCHES_URI = "/{networkUuid}/matches/rule";
    public static final String NETWORK_VALUES_URI = "/{networkUuid}/values";
    private final RestTemplate restTemplate;
    private String dynamicMappingServerBaseUri;

    public DynamicMappingService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.dynamicMappingServerBaseUri = remoteServicesProperties.getServiceUri("dynamic-mapping-server");
        this.restTemplate = restTemplate;
    }

    public void setDynamicMappingServerBaseUri(String dynamicMappingServerBaseUri) {
        this.dynamicMappingServerBaseUri = dynamicMappingServerBaseUri;
    }

    public String getNetworkValues(UUID networkUuid) {
        String networkBasePath = buildEndPointUrl(dynamicMappingServerBaseUri, API_VERSION, DYNAMIC_MAPPING_END_POINT_NETWORK);
        String url = UriComponentsBuilder.fromUriString(networkBasePath + NETWORK_VALUES_URI)
                .buildAndExpand(networkUuid)
                .toUriString();
        return restTemplate.getForObject(url, String.class);
    }

    public String getNetworkMatches(UUID networkUuid, String ruleToMatch) {
        String networkBasePath = buildEndPointUrl(dynamicMappingServerBaseUri, API_VERSION, DYNAMIC_MAPPING_END_POINT_NETWORK);
        String url = UriComponentsBuilder.fromUriString(networkBasePath + NETWORK_MATCHES_URI)
                .buildAndExpand(networkUuid)
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(ruleToMatch, headers);
        return restTemplate.postForObject(url, httpEntity, String.class);
    }

    public String getMappedModels(UUID mappingId) {
        String networkBasePath = buildEndPointUrl(dynamicMappingServerBaseUri, API_VERSION, null);
        String url = UriComponentsBuilder.fromUriString(networkBasePath + "/mappings/{mappingId}/models").buildAndExpand(mappingId).toUriString();
        return restTemplate.getForObject(url, String.class);
    }
}
