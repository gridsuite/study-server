/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.stateestimation;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class StateEstimationClient extends AbstractRestClient {
    public StateEstimationClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("state-estimation-server"), restTemplate);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + STATE_ESTIMATION_API_VERSION + "/results/{resultUuid}/download-debug-file")
            .buildAndExpand(resultUuid).toUriString();
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, null, Resource.class);
    }
}
