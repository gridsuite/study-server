/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client.shortcircuit;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.SHORT_CIRCUIT_API_VERSION;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */
@Service
public class ShortCircuitClient extends AbstractRestClient {

    private static final String PARAMETERS_ENDPOINT = "parameters";

    public ShortCircuitClient(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("shortcircuit-server"), restTemplate);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, "results");
        String url = UriComponentsBuilder.fromUriString(resultBaseUrl + "/{resultUuid}/download-debug-file")
            .buildAndExpand(resultUuid)
            .toUriString();
        return getRestTemplate().exchange(url, HttpMethod.GET, null, Resource.class);
    }

    public String getSpecificParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, PARAMETERS_ENDPOINT);

        return getRestTemplate().getForObject(parametersBaseUrl + "/specific-parameters", String.class);
    }

    public String getParameters(UUID parameterUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, PARAMETERS_ENDPOINT);
        String url = UriComponentsBuilder.fromUriString(parametersBaseUrl + "/{parameterUuid}")
            .buildAndExpand(parameterUuid)
            .toUriString();
        return getRestTemplate().getForObject(url, String.class);
    }

    public void updateParameters(UUID parameterUuid, @Nullable String parameters) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, PARAMETERS_ENDPOINT);
        String url = UriComponentsBuilder.fromUriString(parametersBaseUrl + "/{parameterUuid}")
            .buildAndExpand(parameterUuid)
            .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        getRestTemplate().put(url, new HttpEntity<>(parameters, headers));
    }
}
