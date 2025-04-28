/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation.impl;

import com.powsybl.ws.commons.computation.dto.DebugInfos;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationParametersInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.DYNAMIC_SIMULATION_NOT_FOUND;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationClientImpl extends AbstractRestClient implements DynamicSimulationClient {

    @Autowired
    public DynamicSimulationClientImpl(RemoteServicesProperties remoteServicesProperties,
                                       RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-simulation-server"), restTemplate);
    }

    @Override
    public UUID run(String provider, String receiver, UUID networkUuid, String variantId, ReportInfos reportInfos, DynamicSimulationParametersInfos parameters, String userId, DebugInfos debugInfos) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{networkUuid}/run");
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (provider != null && !provider.isBlank()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        Optional.ofNullable(debugInfos).ifPresent(debugInfosValue -> {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, debugInfosValue.debug());
            uriComponentsBuilder.queryParam(QUERY_PARAM_BROWSER_TAB_UUID, debugInfosValue.browserTabUuid());

        });
        uriComponentsBuilder
                .queryParam("mappingName", parameters.getMapping())
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid())
                .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.DYNAMIC_SIMULATION.reportKey);
        var uriComponent = uriComponentsBuilder
                .buildAndExpand(networkUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // call dynamic-simulation REST API
        HttpEntity<DynamicSimulationParametersInfos> httpEntity = new HttpEntity<>(parameters, headers);
        return getRestTemplate().postForObject(uriComponent.toUriString(), httpEntity, UUID.class);
    }

    @Override
    public UUID getTimeSeriesResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{resultUuid}/timeseries")
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
    public UUID getTimelineResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{resultUuid}/timeline")
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

        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/{resultUuid}/status")
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
    public void invalidateStatus(List<UUID> resultUuids) {
        Objects.requireNonNull(resultUuids);
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "/invalidate-status");

        uriComponentsBuilder.queryParam("resultUuid", resultUuids);

        var uriComponents = uriComponentsBuilder.build();

        // call dynamic-simulation REST API
        try {
            getRestTemplate().exchange(uriComponents.toUriString(), HttpMethod.PUT, null, new ParameterizedTypeReference<List<UUID>>() { });
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SIMULATION_NOT_FOUND);
            }
            throw e;
        }
    }

    @Override
    public void deleteResults(List<UUID> resultsUuids) {
        if (resultsUuids != null && resultsUuids.isEmpty()) {
            return;
        }
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultsUuids);
        // call dynamic-simulation REST API
        getRestTemplate().delete(uriComponents.build().toUriString());
    }

    @Override
    public void deleteAllResults() {
        deleteResults(null);
    }

    @Override
    public Integer getResultsCount() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT);
        var uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl);
        // call dynamic-simulation REST API
        return getRestTemplate().getForObject(uriComponents.toUriString(), Integer.class);
    }
}
