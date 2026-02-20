/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicmargincalculation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractRestClient;
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
public class DynamicMarginCalculationClient extends AbstractRestClient {
    public static final String API_VERSION = DYNAMIC_MARGIN_CALCULATION_API_VERSION;
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER = "parameters";
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN = "networks";
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT = "results";

    protected DynamicMarginCalculationClient(RemoteServicesProperties remoteServicesProperties,
                                             RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-margin-calculation-server"), restTemplate);
    }

    private String getParametersWithUuidUrl(UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        return UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();
    }

    // --- Related parameters methods --- //

    public String getDefaultProvider() {
        String rootBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, null);

        String url = UriComponentsBuilder
                .fromUriString(rootBaseUrl + "/default-provider")
                .toUriString();

        return getRestTemplate().getForObject(url, String.class);
    }

    public String getProvider(@NonNull UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        return getRestTemplate().getForObject(url, String.class);
    }

    public void updateProvider(@NonNull UUID parametersUuid, @NonNull String provider) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(provider);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(provider, headers);

        // call dynamic-margin-calculation REST API
        getRestTemplate().put(url, httpEntity);
    }

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

    public UUID createParameters(@NonNull String parametersInfos) {
        Objects.requireNonNull(parametersInfos);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    public void updateParameters(@NonNull UUID parametersUuid, @NonNull String parametersInfos) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(parametersInfos);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        getRestTemplate().put(url, httpEntity);
    }

    public UUID duplicateParameters(@NonNull UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    public void deleteParameters(@NonNull UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-margin-calculation REST API
        getRestTemplate().delete(url);
    }

    public UUID createDefaultParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/default")
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    // --- Related run computation methods --- //

    public UUID run(String provider, @NonNull String receiver, @NonNull UUID networkUuid, String variantId,
                    @NonNull ReportInfos reportInfos, @NonNull UUID dynamicSecurityAnalysisParametersUuid,
                    @NonNull UUID parametersUuid, @NonNull String dynamicSimulationParametersJson, String userId, boolean debug) {
        Objects.requireNonNull(receiver);
        Objects.requireNonNull(networkUuid);
        Objects.requireNonNull(reportInfos);
        Objects.requireNonNull(dynamicSecurityAnalysisParametersUuid);
        Objects.requireNonNull(parametersUuid);

        String runBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(runBaseUrl + "/{networkUuid}/run");
        if (StringUtils.isNotBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (StringUtils.isNotBlank(provider)) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }
        uriComponentsBuilder
                .queryParam("dynamicSecurityAnalysisParametersUuid", dynamicSecurityAnalysisParametersUuid)
                .queryParam("parametersUuid", parametersUuid)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid())
                .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.DYNAMIC_MARGIN_CALCULATION.reportKey);
        String url = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // call dynamic-margin-calculation REST API
        HttpEntity<?> httpEntity = new HttpEntity<>(dynamicSimulationParametersJson, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    // --- Related result methods --- //

    public DynamicMarginCalculationStatus getStatus(@NonNull UUID resultUuid) {
        Objects.requireNonNull(resultUuid);

        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl + "/{resultUuid}/status")
                .buildAndExpand(resultUuid)
                .toUriString();

        // call dynamic-margin-calculation REST API
        return getRestTemplate().getForObject(url, DynamicMarginCalculationStatus.class);
    }

    public void invalidateStatus(@NonNull List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl + "/invalidate-status")
                .queryParam("resultUuid", resultUuids)
                .build()
                .toUriString();

        getRestTemplate().exchange(url, HttpMethod.PUT, null, new ParameterizedTypeReference<List<UUID>>() { });
    }

    public void deleteResults(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);
        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl)
                .queryParam(QUERY_PARAM_RESULTS_UUIDS, resultUuids)
                .build()
                .toUriString();
        // call dynamic-margin-calculation REST API
        getRestTemplate().delete(url);
    }

    public Integer getResultsCount() {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);
        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl)
                .toUriString();

        // call dynamic-margin-calculation REST API
        return getRestTemplate().getForObject(url, Integer.class);
    }
}
