/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.dynamicmapping;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@Service
public class DynamicMappingClient extends AbstractRestClient {
    public DynamicMappingClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-mapping-server"), restTemplate);
    }

    public String getMappedModels(UUID mappingId) {
        String path = UriComponentsBuilder.fromPath("/mappings/{mappingId}/models").buildAndExpand(mappingId).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }
}
