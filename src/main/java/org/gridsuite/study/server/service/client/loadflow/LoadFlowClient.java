/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.loadflow;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;

@Service
public class LoadFlowClient extends AbstractRestClient {
    public LoadFlowClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("loadflow-server"), restTemplate);
    }

    public String getProviders() {
        return getRestTemplate().getForObject(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/providers", String.class);
    }

    public String getSpecificParameters() {
        return getRestTemplate().getForObject(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/specific-parameters", String.class);
    }

    public String getDefaultLimitReductions() {
        return getRestTemplate().getForObject(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/parameters/default-limit-reductions", String.class);
    }

    public LoadFlowParametersInfos getParameters(UUID parameterUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        return getRestTemplate().getForObject(getBaseUri() + path, LoadFlowParametersInfos.class);
    }

    public void updateParameters(UUID parameterUuid, @Nullable String parameters) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        getRestTemplate().put(getBaseUri() + path, new HttpEntity<>(parameters, headers));
    }
}
