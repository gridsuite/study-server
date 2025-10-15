/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.FILTER_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.EVALUATE_FILTER_FAILED;
import static org.gridsuite.study.server.StudyException.Type.NETWORK_NOT_FOUND;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class FilterService {

    public static final String FILTER_END_POINT_EVALUATE = "/filters/evaluate";
    public static final String FILTER_END_POINT_EXPORT = "/filters/{id}/export";
    public static final String FILTERS_END_POINT_EXPORT = "/filters/export";

    private final RestTemplate restTemplate;

    private final String baseUri;

    // getter to facilitate to mock
    public String getBaseUri() {
        return baseUri;
    }

    public FilterService(RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate) {
        this.baseUri = remoteServicesProperties.getServiceUri("filter-server");
        this.restTemplate = restTemplate;
    }

    public String evaluateFilter(UUID networkUuid, String variantId, String filter) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = getBaseUri() + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EVALUATE;

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        var uriComponent = uriComponentsBuilder
                .build();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(filter, headers);

        // call filter-server REST API
        try {
            return restTemplate.postForObject(uriComponent.toUriString(), request, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(e, EVALUATE_FILTER_FAILED);
            }
        }
    }

    public String exportFilter(UUID networkUuid, UUID filterUuid) {
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(filterUuid);
        String endPointUrl = getBaseUri() + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EXPORT;

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        var uriComponent = uriComponentsBuilder.buildAndExpand(filterUuid);

        return restTemplate.getForObject(uriComponent.toUriString(), String.class);
    }

    public String exportFilters(UUID networkUuid, List<UUID> filtersUuid, String variantId) {
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(filtersUuid);
        String endPointUrl = getBaseUri() + DELIMITER + FILTER_API_VERSION + FILTERS_END_POINT_EXPORT;

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        uriComponentsBuilder.queryParam("ids", filtersUuid);
        var uriComponent = uriComponentsBuilder.buildAndExpand();

        return restTemplate.getForObject(uriComponent.toUriString(), String.class);
    }
}
