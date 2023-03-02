/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation.impl;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_NOT_FOUND;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationClientImpl extends AbstractRestClient implements DynamicSimulationClient {

    @Autowired
    public DynamicSimulationClientImpl(@Value("${gridsuite.services.dynamic-simulation-server.base-uri:http://dynamic-simulation-server/}") String baseUri,
                                       RestTemplate restTemplate) {
        super(baseUri, restTemplate);
    }

    @Override
    public UUID run(String receiver, UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{networkUuid}/run");
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam("variantId", variantId);
        }
        uriComponentsBuilder
                .queryParam("startTime", startTime)
                .queryParam("stopTime", stopTime)
                .queryParam("mappingName", mappingName)
                .queryParam("receiver", receiver);
        var uriComponent = uriComponentsBuilder
                .buildAndExpand(networkUuid);

        // call dynamic-simulation REST API
        return getRestTemplate().postForObject(uriComponent.toUriString(), null, UUID.class);
    }

    @Override
    public UUID getTimeSeriesResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/timeseries")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        UUID timeseriesUuid;
        try {
            timeseriesUuid = getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SIMULATION_NOT_FOUND);
            }
            throw e;
        }
        return timeseriesUuid;
    }

    @Override
    public UUID getTimeLineResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/timeline")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        UUID timelineUuid;
        try {
            timelineUuid = getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SIMULATION_NOT_FOUND);
            }
            throw e;
        }
        return timelineUuid;
    }

    @Override
    public DynamicSimulationStatus getStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/status")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        DynamicSimulationStatus status;
        try {
            status = getRestTemplate().getForObject(uriComponents.toUriString(), DynamicSimulationStatus.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SIMULATION_NOT_FOUND);
            }
            throw e;
        }
        return status;
    }

    @Override
    public void deleteResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        getRestTemplate().delete(uriComponents.toUriString());
    }
}
