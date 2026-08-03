/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.voltageinit;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.voltageinit.parameters.VoltageInitParametersInfos;
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
public class VoltageInitClient extends AbstractRestClient {
    public VoltageInitClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("voltage-init-server"), restTemplate);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/results/{resultUuid}/download-debug-file")
            .buildAndExpand(resultUuid).toUriString();
        return getRestTemplate().exchange(getBaseUri() + path, HttpMethod.GET, null, Resource.class);
    }

    public VoltageInitParametersInfos getParameters(UUID parameterUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + VOLTAGE_INIT_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, VoltageInitParametersInfos.class);
    }
}
