/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.client.dynamicsecurityanalysis;

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
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
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

    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER = "parameters";
    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN = "networks";
    public static final String DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT = "results";

    protected DynamicSecurityAnalysisClient(RemoteServicesProperties remoteServicesProperties,
                                            RestTemplate restTemplate) {
        super(remoteServicesProperties.getServiceUri("dynamic-security-analysis-server"), restTemplate);
    }

    // --- Related parameters methods --- //

    public String getDefaultProvider() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, "default-provider");

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl)
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

    public String getProvider(UUID parametersUuid) {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl + "{uuid}/provider")
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

    public void updateProvider(UUID parametersUuid, String provider) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(provider);

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl + "{uuid}/provider")
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

    public String getParameters(UUID parametersUuid) {

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl + "{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();

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

    public UUID createParameters(String parametersInfos) {
        Objects.requireNonNull(parametersInfos);

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl)
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

    public void updateParameters(UUID parametersUuid, String parametersInfos) {
        Objects.requireNonNull(parametersUuid);
        Objects.requireNonNull(parametersInfos);

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl + "{uuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();

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

    public UUID duplicateParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl)
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

    public void deleteParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{uuid}")
                .buildAndExpand(parametersUuid);

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(uriComponents.toUriString());
    }

    public UUID createDefaultParameters() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_PARAMETER);

        String url = UriComponentsBuilder
                .fromHttpUrl(endPointUrl + "default")
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

    public UUID run(String provider, String receiver, UUID networkUuid, String variantId, ReportInfos reportInfos, UUID dynamicSimulationResultUuid, UUID parametersUuid, String userId) {
        Objects.requireNonNull(networkUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RUN);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{networkUuid}/run");
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
        var uriComponent = uriComponentsBuilder
                .buildAndExpand(networkUuid);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // call dynamic-security-analysis REST API
        HttpEntity<?> httpEntity = new HttpEntity<>(null, headers);
        return getRestTemplate().postForObject(uriComponent.toUriString(), httpEntity, UUID.class);

    }

    // --- Related result methods --- //

    public DynamicSecurityAnalysisStatus getStatus(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}/status")
                .buildAndExpand(resultUuid);

        // call dynamic-security-analysis REST API
        DynamicSecurityAnalysisStatus status;
        try {
            status = getRestTemplate().getForObject(uriComponents.toUriString(), DynamicSecurityAnalysisStatus.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
        return status;
    }

    public void invalidateStatus(List<UUID> resultUuids) {
        Objects.requireNonNull(resultUuids);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(endPointUrl + "invalidate-status");

        uriComponentsBuilder.queryParam("resultUuid", resultUuids);

        UriComponents uriComponents = uriComponentsBuilder.build();

        // call dynamic-security-analysis REST API
        try {
            getRestTemplate().exchange(uriComponents.toUriString(), HttpMethod.PUT, null, new ParameterizedTypeReference<List<UUID>>() { });
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
    }

    public void deleteResult(UUID resultUuid) {
        Objects.requireNonNull(resultUuid);
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);

        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl + "{resultUuid}")
                .buildAndExpand(resultUuid);

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(uriComponents.toUriString());

    }

    public void deleteResults() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);
        UriComponentsBuilder uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl);

        // call dynamic-security-analysis REST API
        getRestTemplate().delete(uriComponents.toUriString());
    }

    public Integer getResultsCount() {
        String endPointUrl = buildEndPointUrl(getBaseUri(), DYNAMIC_SECURITY_ANALYSIS_API_VERSION, DYNAMIC_SECURITY_ANALYSIS_END_POINT_RESULT);
        UriComponentsBuilder uriComponents = UriComponentsBuilder.fromHttpUrl(endPointUrl);

        // call dynamic-security-analysis REST API
        return getRestTemplate().getForObject(uriComponents.toUriString(), Integer.class);

    }

}
