/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import lombok.Getter;
import lombok.NonNull;
import org.gridsuite.filter.globalfilter.GlobalFilter;
import org.gridsuite.filter.utils.EquipmentType;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
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
    public static final String FILTER_END_POINT_EVALUATE_IDS = "/filters/evaluate/identifiables";
    public static final String FILTER_END_POINT_EXPORT = "/filters/{id}/export";
    public static final String FILTERS_END_POINT_EXPORT = "/filters/export";

    private final RestTemplate restTemplate;

    @Getter // getter to facilitate to mock
    private final String baseUri;

    @Autowired
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
        var uriComponent = uriComponentsBuilder.build();

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

    public List<String> evaluateGlobalFilter(@NonNull final UUID networkUuid, @NonNull final String variantId,
                                             @NonNull final List<EquipmentType> equipmentTypes, @NonNull final GlobalFilter filter) {
        final UriComponents uriComponent = UriComponentsBuilder.fromHttpUrl(getBaseUri())
                .pathSegment(FILTER_API_VERSION, "global-filter")
                .queryParam("networkUuid", networkUuid)
                .queryParam("variantId", variantId)
                .queryParam("equipmentTypes", equipmentTypes)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return restTemplate.exchange(uriComponent.toUri(), HttpMethod.POST, new HttpEntity<>(filter, headers), new ParameterizedTypeReference<List<String>>() { })
                               .getBody();
        } catch (final HttpStatusCodeException ex) {
            if (HttpStatus.NOT_FOUND.equals(ex.getStatusCode())) {
                throw new StudyException(NETWORK_NOT_FOUND);
            } else {
                throw handleHttpError(ex, EVALUATE_FILTER_FAILED);
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

    public String evaluateFilters(UUID networkUuid, List<UUID> filtersUuid) {
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(filtersUuid);
        String endPointUrl = getBaseUri() + DELIMITER + FILTER_API_VERSION + FILTER_END_POINT_EVALUATE_IDS;

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        uriComponentsBuilder.queryParam("networkUuid", networkUuid);
        uriComponentsBuilder.queryParam("ids", filtersUuid);
        var uriComponent = uriComponentsBuilder.buildAndExpand();

        return restTemplate.getForObject(uriComponent.toUriString(), String.class);
    }
}
