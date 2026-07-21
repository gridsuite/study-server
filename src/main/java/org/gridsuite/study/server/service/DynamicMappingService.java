/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Service
public class DynamicMappingService {
    private final RestTemplate restTemplate;
    private String dynamicMappingServerBaseUri;

    public DynamicMappingService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.dynamicMappingServerBaseUri = remoteServicesProperties.getServiceUri("dynamic-mapping-server");
        this.restTemplate = restTemplate;
    }

    public void setDynamicMappingServerBaseUri(String dynamicMappingServerBaseUri) {
        this.dynamicMappingServerBaseUri = dynamicMappingServerBaseUri;
    }

    public String getMappedModels(UUID mappingId) {
        String path = UriComponentsBuilder.fromPath("/mappings/{mappingId}/models")
            .buildAndExpand(mappingId)
            .toUriString();
        return restTemplate.getForObject(dynamicMappingServerBaseUri + path, String.class);
    }
}
