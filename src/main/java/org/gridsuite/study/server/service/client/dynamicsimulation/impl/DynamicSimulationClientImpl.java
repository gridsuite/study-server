/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsimulation.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.event.EventInfos;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.gridsuite.study.server.service.client.dynamicsimulation.DynamicSimulationClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSimulationClientImpl extends AbstractRestClient implements DynamicSimulationClient {

    public DynamicSimulationClientImpl(RemoteServicesProperties remoteServicesProperties,
                                       RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-simulation-server"), restTemplate);
    }

    private String getParametersWithUuidUrl(UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        return UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();
    }

    // --- Parameters related methods --- //

    @Override
    public String getProvider(@NonNull UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        return getRestTemplate().getForObject(url, String.class);
    }

    @Override
    public String getParameters(@NonNull UUID parametersUuid, String userId) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.isNotBlank(userId)) {
            headers.set(HEADER_USER_ID, userId);
        }

        return getRestTemplate()
                .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    @Override
    public UUID createParameters(@NonNull String parametersInfos) {
        Objects.requireNonNull(parametersInfos);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    @Override
    public void updateParameters(@NonNull UUID parametersUuid, @NonNull String parametersInfos) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(parametersInfos);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        getRestTemplate().put(url, httpEntity);
    }

    @Override
    public UUID duplicateParameters(@NonNull UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    @Override
    public void deleteParameters(@NonNull UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-margin-calculation REST API
        getRestTemplate().delete(url);
    }

    @Override
    public UUID createDefaultParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/default")
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    // --- Run computation related methods --- //

    @Override
    public UUID run(String receiver, UUID networkUuid, String variantId, ReportInfos reportInfos, UUID parametersUuid, List<EventInfos> events, String userId, boolean debug) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endPointUrl + "/{networkUuid}/run");
        if (StringUtils.isNotBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }
        uriComponentsBuilder
                .queryParam("parametersUuid", parametersUuid)
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
        HttpEntity<List<EventInfos>> httpEntity = new HttpEntity<>(events, headers);
        return getRestTemplate().postForObject(uriComponent.toUriString(), httpEntity, UUID.class);
    }

    @Override
    public UUID getTimeSeriesResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/timeseries")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
    }

    @Override
    public UUID getTimelineResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/timeline")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), UUID.class);
    }

    @Override
    public DynamicSimulationStatus getStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl + "/{resultUuid}/status")
                .buildAndExpand(resultUuid);

        return getRestTemplate().getForObject(uriComponents.toUriString(), DynamicSimulationStatus.class);
    }

    @Override
    public void invalidateStatus(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(endPointUrl + "/invalidate-status");

        uriComponentsBuilder.queryParam("resultUuid", resultUuids);

        var uriComponents = uriComponentsBuilder.build();

        getRestTemplate().exchange(uriComponents.toUriString(), HttpMethod.PUT, null, new ParameterizedTypeReference<List<UUID>>() { });
    }

    @Override
    public void deleteResults(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT);
        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl).queryParam(QUERY_PARAM_RESULTS_UUIDS, resultUuids);
        // call dynamic-simulation REST API
        getRestTemplate().delete(uriComponents.build().toUriString());
    }

    @Override
    public void deleteAllResults() {
        deleteResults(null);
    }

    @Override
    public Integer getResultsCount() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SIMULATION_API_VERSION, DYNAMIC_SIMULATION_END_POINT_RESULT_COUNT);
        var uriComponents = UriComponentsBuilder.fromUriString(endPointUrl);
        // call dynamic-simulation REST API
        return getRestTemplate().getForObject(uriComponents.toUriString(), Integer.class);
    }
}
