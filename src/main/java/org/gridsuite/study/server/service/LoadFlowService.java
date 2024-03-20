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
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
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
public class LoadFlowService {

    static final String RESULT_UUID = "resultUuid";
    static final String RESULTS_UUIDS = "resultsUuids";
    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    private String loadFlowServerBaseUri;

    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public LoadFlowService(RemoteServicesProperties remoteServicesProperties,
                           NetworkModificationTreeService networkModificationTreeService,
                           NetworkService networkStoreService, ObjectMapper objectMapper,
                           NotificationService notificationService,
                           RestTemplate restTemplate) {
        this.loadFlowServerBaseUri = remoteServicesProperties.getServiceUri("loadflow-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.restTemplate = restTemplate;
    }

    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, UUID parametersUuid, String userId, Float limitReduction) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);
        UUID reportUuid = getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportUuid.toString())
                .queryParam(QUERY_PARAM_REPORTER_ID, nodeUuid.toString())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.LOADFLOW.reportKey);
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

    public String getLoadFlowResultOrStatus(UUID nodeUuid, String filters, Sort sort, String suffix) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}" + suffix);
        if (!StringUtils.isEmpty(filters)) {
            uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (sort != null) {
            sort.forEach(order -> uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection()));
        }
        String path = uriComponentsBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();

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

    public String getLoadFlowResult(UUID nodeUuid, String filters, Sort sort) {
        return getLoadFlowResultOrStatus(nodeUuid, filters, sort, "");
    }

    public String getLoadFlowStatus(UUID nodeUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, null, null, "/status");
    }

    public void stopLoadFlow(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);
        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(loadFlowServerBaseUri + path, Void.class);
    }

    public void invalidateLoadFlowStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(loadFlowServerBaseUri + path, Void.class);
        }
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void assertLoadFlowNotRunning(UUID nodeUuid) {
        String scs = getLoadFlowStatus(nodeUuid);
        if (LoadFlowStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(LOADFLOW_RUNNING);
        }
    }

    public List<LimitViolationInfos> getLimitViolations(UUID nodeUuid, String filters, String globalFilters, Sort sort, String variantId, UUID networkUuid) {
        List<LimitViolationInfos> result = new ArrayList<>();
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);

        if (resultUuidOpt.isPresent()) {
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/limit-violations");
            if (filters != null && !filters.isEmpty()) {
                uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
            }
            if (globalFilters != null && !globalFilters.isEmpty()) {
                uriComponentsBuilder.queryParam("globalFilters", URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
                //TODO: delete it when merging filter library
                uriComponentsBuilder.queryParam("networkUuid", networkUuid);
                if (variantId != null && !variantId.isBlank()) {
                    uriComponentsBuilder.queryParam("variantId", variantId);
                }
            }
            if (sort != null) {
                sort.forEach(order -> uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection()));
            }
            String path = uriComponentsBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();
            try {
                ResponseEntity<List<LimitViolationInfos>> responseEntity = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<LimitViolationInfos>>() {
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
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(sourceParametersUuid).toUriString();

        try {
            return restTemplate.postForObject(loadFlowServerBaseUri + path, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public void updateLoadFlowParameters(UUID parametersUuid, String parameters) {

        Objects.requireNonNull(parameters);

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
}
