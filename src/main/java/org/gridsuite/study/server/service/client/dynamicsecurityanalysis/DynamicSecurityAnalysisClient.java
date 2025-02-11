/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsecurityanalysis;

import lombok.NonNull;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.client.AbstractRestClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.notification.NotificationService.HEADER_USER_ID;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Service
public class DynamicSecurityAnalysisClient extends AbstractRestClient {
    public static final String API_VERSION = DYNAMIC_SECURITY_ANALYSIS_API_VERSION;
    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER = "parameters";
    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN = "networks";
    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT = "results";

    protected DynamicSecurityAnalysisClient(RemoteServicesProperties remoteServicesProperties,
                                            RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-security-analysis-server"), restTemplate);
    }

    private String getParametersWithUuidUrl(UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        return UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl + "/{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();
    }

    // --- Related parameters methods --- //

    public String getDefaultProvider() {
        String rootBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, null);

        String url = UriComponentsBuilder
                .fromHttpUrl(rootBaseUrl + "/default-provider")
                .toUriString();

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().getForObject(url, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_NOT_FOUND);
            }
            throw handleHttpError(e, GET_DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_FAILED);
        }
    }

    public String getProvider(@NonNull UUID parametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().getForObject(url, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_PROVIDER_NOT_FOUND);
            }
            throw handleHttpError(e, GET_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED);
        }
    }

    public void updateProvider(@NonNull UUID parametersUuid, @NonNull String provider) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl + "/{uuid}/provider")
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(provider, headers);

        // call dynamic-security-analysis REST API
        try {
            getRestTemplate().put(url, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED);
        }
    }

    public String getParameters(@NonNull UUID parametersUuid) {
        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().getForObject(url, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND);
            }
            throw handleHttpError(e, GET_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID createParameters(@NonNull String parametersInfos) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl)
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().postForObject(url, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public void updateParameters(@NonNull UUID parametersUuid, @NonNull String parametersInfos) {
        String url = getParametersWithUuidUrl(parametersUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parametersInfos, headers);

        // call dynamic-security-analysis REST API
        try {
            getRestTemplate().put(url, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateParameters(@NonNull UUID sourceParametersUuid) {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand()
                .toUriString();

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().postForObject(url, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DUPLICATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public void deleteParameters(@NonNull UUID parametersUuid) {
        String url = getParametersWithUuidUrl(parametersUuid);

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(url);
    }

    public UUID createDefaultParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(parametersBaseUrl + "/default")
                .buildAndExpand()
                .toUriString();

        // call dynamic-security-analysis REST API
        try {
            return getRestTemplate().postForObject(url, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    // --- Related run computation methods --- //

    public UUID run(String provider, @NonNull String receiver, @NonNull UUID networkUuid, String variantId,
                    @NonNull ReportInfos reportInfos, @NonNull UUID dynamicSimulationResultUuid,
                    @NonNull UUID parametersUuid, String userId) {
        String runBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(runBaseUrl + "/{networkUuid}/run");
        if (variantId != null && !variantId.isBlank()) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (provider != null && !provider.isBlank()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        uriComponentsBuilder
                .queryParam("dynamicSimulationResultUuid", dynamicSimulationResultUuid)
                .queryParam("parametersUuid", parametersUuid)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid())
                .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.DYNAMIC_SECURITY_ANALYSIS.reportKey);
        String url = uriComponentsBuilder
                .buildAndExpand(networkUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // call dynamic-security-analysis REST API
        HttpEntity<?> httpEntity = new HttpEntity<>(null, headers);
        return getRestTemplate().postForObject(url, httpEntity, UUID.class);
    }

    // --- Related result methods --- //

    public DynamicSecurityAnalysisStatus getStatus(@NonNull UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromHttpUrl(resultBaseUrl + "/{resultUuid}/status")
                .buildAndExpand(resultUuid)
                .toUriString();

        // call dynamic-security-analysis REST API
        return getRestTemplate().getForObject(url, DynamicSecurityAnalysisStatus.class);
    }

    public void invalidateStatus(@NonNull List<UUID> resultUuids) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromHttpUrl(resultBaseUrl + "/invalidate-status")
                .queryParam("resultUuid", resultUuids)
                .build()
                .toUriString();

        // call dynamic-security-analysis REST API
        try {
            getRestTemplate().exchange(url, HttpMethod.PUT, null, new ParameterizedTypeReference<List<UUID>>() { });
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
    }

    public void deleteResult(@NonNull UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        String url = UriComponentsBuilder
                .fromHttpUrl(resultBaseUrl + "/{resultUuid}")
                .buildAndExpand(resultUuid)
                .toUriString();

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(url);
    }

    public void deleteResults() {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);
        String url = UriComponentsBuilder
                .fromHttpUrl(resultBaseUrl)
                .toUriString();

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(url);
    }

    public Integer getResultsCount() {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);
        String url = UriComponentsBuilder
                .fromHttpUrl(resultBaseUrl)
                .toUriString();

        // call dynamic-security-analysis REST API
        return getRestTemplate().getForObject(url, Integer.class);
    }
}
