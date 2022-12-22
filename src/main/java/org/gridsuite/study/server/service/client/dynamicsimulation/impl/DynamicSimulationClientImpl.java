/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation.impl;

import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Objects;
import java.util.UUID;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationClientImpl extends AbstractRestClient implements DynamicSimulationClient {

    @Autowired
    public DynamicSimulationClientImpl(@Value("${backing-services.dynamic-simulation-server.base-uri:http://dynamic-simulation-server/}") String baseUri,
                                       RestTemplate restTemplate) {
        super(baseUri, restTemplate);
    }

    @Override
    public UUID run(UUID networkUuid, String variantId, int startTime, int stopTime, String mappingName) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{networkUuid}/run")
                .queryParam("variantId", variantId)
                .queryParam("startTime", startTime)
                .queryParam("stopTime", stopTime)
                .queryParam("mappingName", mappingName)
                .buildAndExpand(networkUuid);

        // call dynamic-simulation REST API
        UUID resultUuid = getRestTemplate().postForObject(uriBuilder.toUriString(), null, UUID.class);
        return resultUuid;
    }

    @Override
    public UUID getTimeSeriesResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/timeseries")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        UUID timeseriesUuid = getRestTemplate().getForObject(uriBuilder.toUriString(), UUID.class);
        return timeseriesUuid;
    }

    @Override
    public UUID getTimeLineResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/timeline")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        UUID timelineUuid = getRestTemplate().getForObject(uriBuilder.toUriString(), UUID.class);
        return timelineUuid;
    }

    @Override
    public String getStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/status")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        String status = getRestTemplate().getForObject(uriBuilder.toUriString(), String.class);
        return status;
    }

    @Override
    public void deleteResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}")
                .buildAndExpand(resultUuid);

        // call dynamic-simulation REST API
        getRestTemplate().delete(uriBuilder.toUriString());
    }
}
