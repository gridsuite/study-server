/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkmap;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class NetworkMapClient extends AbstractRestClient {
    public NetworkMapClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("network-map-server"), restTemplate);
    }

    public String getElementSchema(String elementType, String infoType) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_MAP_API_VERSION + "/schemas/{elementType}/{infoType}")
            .buildAndExpand(elementType, infoType).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, String.class);
    }
}
