/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class LoadFlowService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";
    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    private final NetworkService networkStoreService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final NetworkModificationTreeService networkModificationTreeService;
    private String loadFlowServerBaseUri;

    @Autowired
    public LoadFlowService(RemoteServicesProperties remoteServicesProperties,
                           NetworkModificationTreeService networkModificationTreeService,
                           NetworkService networkStoreService, ObjectMapper objectMapper,
                           RestTemplate restTemplate) {
        this.loadFlowServerBaseUri = remoteServicesProperties.getServiceUri("loadflow-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID parametersUuid, UUID reportUuid, String userId, Float limitReduction) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid, rootNetworkUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportUuid.toString())
                .queryParam(QUERY_PARAM_REPORTER_ID, nodeUuid.toString())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.LOAD_FLOW.reportKey);
        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersUuid.toString());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (limitReduction != null) {
            uriComponentsBuilder.queryParam("limitReduction", limitReduction);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.POST, new HttpEntity<>(headers), UUID.class).getBody();
    }

    public void deleteLoadFlowResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(loadFlowServerBaseUri + path);
    }

    public void deleteLoadFlowResults() {
        try {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results").toUriString();
            restTemplate.delete(loadFlowServerBaseUri + path, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }

    }

    public Integer getLoadFlowResultsCount() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(loadFlowServerBaseUri + path, Integer.class);
    }

    public String getLoadFlowResultOrStatus(UUID nodeUuid, UUID rootNetworkUuid, String filters, Sort sort, String suffix) {
        String result;
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.LOAD_FLOW);

        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}" + suffix);
        if (!StringUtils.isEmpty(filters)) {
            uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (sort != null) {
            sort.forEach(order -> uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection()));
        }
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();

        try {
            result = restTemplate.getForObject(loadFlowServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(LOADFLOW_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public String getLoadFlowResult(UUID nodeUuid, UUID rootNetworkUuid, String filters, Sort sort) {
        return getLoadFlowResultOrStatus(nodeUuid, rootNetworkUuid, filters, sort, "");
    }

    public String getLoadFlowStatus(UUID nodeUuid, UUID rootNetworkUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, rootNetworkUuid, null, null, "/status");
    }

    public void stopLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.LOAD_FLOW);
        if (resultUuid == null) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public void invalidateLoadFlowStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(loadFlowServerBaseUri + path, Void.class);
        }
    }

    private String getVariantId(UUID nodeUuid, UUID rootNetworkUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid, rootNetworkUuid);
    }

    public void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void assertLoadFlowNotRunning(UUID nodeUuid, UUID rootNetworkUuid) {
        String scs = getLoadFlowStatus(nodeUuid, rootNetworkUuid);
        if (LoadFlowStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(LOADFLOW_RUNNING);
        }
    }

    public List<LimitViolationInfos> getLimitViolations(UUID nodeUuid, UUID rootNetworkUuid, String filters, String globalFilters, Sort sort, UUID networkUuid, String variantId) {
        List<LimitViolationInfos> result = new ArrayList<>();
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.LOAD_FLOW);

        if (resultUuid != null) {
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/limit-violations");
            if (!StringUtils.isEmpty(filters)) {
                uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
            }
            if (!StringUtils.isEmpty(globalFilters)) {
                uriComponentsBuilder.queryParam("globalFilters", URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
                uriComponentsBuilder.queryParam("networkUuid", networkUuid);
                if (!StringUtils.isBlank(variantId)) {
                    uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
                }
            }
            if (sort != null) {
                sort.forEach(order -> uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection()));
            }
            String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();
            try {
                ResponseEntity<List<LimitViolationInfos>> responseEntity = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
                result = responseEntity.getBody();
            } catch (HttpStatusCodeException e) {
                if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                    throw new StudyException(LOADFLOW_NOT_FOUND);
                }
                throw e;
            }
        }
        return result;
    }

    public LoadFlowParametersInfos getLoadFlowParameters(UUID parametersUuid) {

        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid).toUriString();
        try {
            return restTemplate.getForObject(loadFlowServerBaseUri + path, LoadFlowParametersInfos.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(LOADFLOW_PARAMETERS_NOT_FOUND);
            }
            throw handleHttpError(e, GET_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public UUID createLoadFlowParameters(String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            return restTemplate.postForObject(loadFlowServerBaseUri + path, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateLoadFlowParameters(UUID sourceParametersUuid) {

        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand(sourceParametersUuid).toUriString();

        try {
            return restTemplate.postForObject(loadFlowServerBaseUri + path, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public void updateLoadFlowParameters(UUID parametersUuid, @Nullable String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.put(loadFlowServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public void deleteLoadFlowParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(uuid)
                .toUriString();

        try {
            restTemplate.delete(loadFlowServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public void updateLoadFlowProvider(UUID parameterUuid, String provider) {
        Objects.requireNonNull(provider);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI + "/provider")
                .buildAndExpand(parameterUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();

        HttpEntity<String> httpEntity = new HttpEntity<>(provider, headers);

        try {
            restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PATCH, httpEntity, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_LOADFLOW_PROVIDER_FAILED);
        }
    }

    public String getLoadFlowDefaultProvider() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/default-provider")
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.getForObject(loadFlowServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_LOADFLOW_DEFAULT_PROVIDER_FAILED);
        }
    }

    public UUID createDefaultLoadFlowParameters() {

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/default")
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.postForObject(loadFlowServerBaseUri + path, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public UUID getLoadFlowParametersOrDefaultsUuid(StudyEntity studyEntity) {
        if (studyEntity.getLoadFlowParametersUuid() == null) {
            studyEntity.setLoadFlowParametersUuid(createDefaultLoadFlowParameters());
        }
        return studyEntity.getLoadFlowParametersUuid();
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, LOADFLOW_API_VERSION, loadFlowServerBaseUri, LOADFLOW_NOT_FOUND, restTemplate);
    }
}
