/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.dynamicmargincalculation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORTER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_TYPE;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_REPORT_UUID;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicMarginCalculationRestService extends AbstractComputationRestService implements ComputationParameters {

    public static final String API_VERSION = DYNAMIC_MARGIN_CALCULATION_API_VERSION;
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER = "parameters";
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_RUN = "networks";
    public static final String DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT = "results";

    private final ObjectMapper objectMapper;

    public DynamicMarginCalculationRestService(
            RemoteServicesProperties remoteServicesProperties, RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(remoteServicesProperties.getServiceUri("dynamic-margin-calculation-server"), restTemplate);
        this.objectMapper = objectMapper;
    }

    public String getParameters(UUID parametersUuid, String userId) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.isNotBlank(userId)) {
            headers.set(HEADER_USER_ID, userId);
        }

        return getRestTemplate()
                .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
    }

    private String getParametersWithUuidUrl(UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        return UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();
    }

    public UUID createParameters(String parameters) {
        Objects.requireNonNull(parameters);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    @Override
    public UUID createDefaultParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/default")
                .buildAndExpand()
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(parametersInfos);

        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        getRestTemplate().put(url, httpEntity);
    }

    @Override
    public UUID duplicateParameters(UUID sourceParameterId) {
        Objects.requireNonNull(sourceParameterId);

        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/duplicate")
                .buildAndExpand(sourceParameterId)
                .toUriString();

        return getRestTemplate().postForObject(url, null, UUID.class);
    }

    public void deleteParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-margin-calculation REST API
        getRestTemplate().delete(url);
    }

    public UUID runDynamicMarginCalculation(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid,
                                            String variantId, UUID reportUuid, UUID dynamicSimulationParametersUuid,
                                            UUID dynamicSecurityAnalysisParametersUuid, UUID parametersUuid, String userId,
                                                    boolean debug) {

        // create receiver for getting back the notification in rabbitmq
        String receiver;

        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        ReportInfos reportInfos = new ReportInfos(reportUuid, nodeUuid);

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
        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }
        uriComponentsBuilder
                .queryParam("dynamicSimulationParametersUuid", dynamicSimulationParametersUuid)
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
        HttpEntity<?> httpEntity = new HttpEntity<>(null, headers);

        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    public DynamicMarginCalculationStatus getStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        Objects.requireNonNull(resultUuid);

        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl + "/{resultUuid}/status")
                .buildAndExpand(resultUuid)
                .toUriString();

        // call dynamic-margin-calculation REST API
        return getRestTemplate().getForObject(url, DynamicMarginCalculationStatus.class);
    }

    public void invalidateStatus(List<UUID> resultUuids) {
        if (CollectionUtils.isEmpty(resultUuids)) {
            return;
        }

        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl + "/invalidate-status")
                .queryParam("resultUuid", resultUuids)
                .build()
                .toUriString();

        getRestTemplate().put(url, null);
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

    public void deleteAllResults() {
        deleteResults(null);
    }

    public Integer getResultsCount() {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);
        String url = UriComponentsBuilder
                .fromUriString(resultBaseUrl)
                .toUriString();

        // call dynamic-margin-calculation REST API
        return getRestTemplate().getForObject(url, Integer.class);
    }

    public UUID getDynamicMarginCalculationParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getDynamicMarginCalculationParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setDynamicMarginCalculationParametersUuid(createDefaultParameters());
        }
        return studyEntity.getDynamicMarginCalculationParametersUuid();
    }

    public String getProvider(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromUriString(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        return getRestTemplate().getForObject(url, String.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    public String getProviders() {
        String url = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, "providers");
        return getRestTemplate().getForObject(url, String.class);
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_MARGIN_CALCULATION_API_VERSION, DYNAMIC_MARGIN_CALCULATION_END_POINT_RESULT);
        String url = UriComponentsBuilder.fromUriString(resultBaseUrl + "/{resultUuid}/download-debug-file")
                .buildAndExpand(resultUuid)
                .toUriString();
        return getRestTemplate().exchange(url, HttpMethod.GET, null, Resource.class);
    }
}
