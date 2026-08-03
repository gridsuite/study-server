/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.networkconversion;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class NetworkConversionClient extends AbstractRestClient {
    public NetworkConversionClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("network-conversion-server"), restTemplate);
    }

    public String getCaseImportParameters(UUID caseUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + NETWORK_CONVERSION_API_VERSION + "/cases/{caseUuid}/import-parameters")
            .buildAndExpand(caseUuid).toUriString();
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, null, String.class).getBody();
    }
}
