/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_GLOBAL_FILTERS;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_NETWORK_UUID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public abstract class AbstractStateEstimationRestService extends AbstractComputationRestService {

    protected static final String RESULTS = "results";

    protected AbstractStateEstimationRestService(RemoteServicesProperties remoteServicesProperties, RestClient restClient) {
        super(remoteServicesProperties.getServiceUri("state-estimation-server"), restClient);
    }

    protected String getStateEstimationServerResult(UUID resultUuid, UUID networkUuid, String variantId, String globalFilters, String... pathSegments) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUri)
            .pathSegment(pathSegments)
            .queryParam(QUERY_PARAM_NETWORK_UUID, "{networkUuid}");

        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("resultUuid", resultUuid);
        uriVariables.put(QUERY_PARAM_NETWORK_UUID, networkUuid);

        if (!StringUtils.isBlank(variantId)) {
            uriBuilder.queryParam(QUERY_PARAM_VARIANT_ID, "{variantId}");
            uriVariables.put(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (!StringUtils.isBlank(globalFilters)) {
            uriBuilder.queryParam(QUERY_PARAM_GLOBAL_FILTERS, "{globalFilters}");
            uriVariables.put(QUERY_PARAM_GLOBAL_FILTERS, globalFilters);
        }
        // template variables are strictly encoded once, and the URI overload avoids encoding a second time
        URI uri = uriBuilder.encode().buildAndExpand(uriVariables).toUri();
        try {
            return restClient.get().uri(uri).retrieve().body(String.class);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof InterruptedIOException || e.getCause() instanceof HttpTimeoutException;
            throw new ResponseStatusException(timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY,
                "State estimation server " + (timeout ? "timed out" : "is unreachable"), e);
        }
    }
}
