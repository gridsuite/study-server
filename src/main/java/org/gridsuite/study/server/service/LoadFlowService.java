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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, UUID parametersUuid, String provider, String userId, Float limitReduction) {
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
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
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

    public void deleteLoadFlowResults(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results")
                    .queryParam(RESULTS_UUIDS, uuids).build().toUriString();
            restTemplate.delete(loadFlowServerBaseUri + path, Void.class);
        }
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

    public String getLoadFlowResultOrStatus(UUID nodeUuid, String suffix) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getLoadFlowResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}" + suffix)
                .buildAndExpand(resultUuidOpt.get()).toUriString();
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

    public String getLoadFlowResult(UUID nodeUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, "");
    }

    public String getLoadFlowStatus(UUID nodeUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, "/status");
    }

    public void stopLoadFlow(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getLoadFlowResultUuid(nodeUuid);
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

    public List<LimitViolationInfos> getLimitViolations(UUID nodeUuid) {
        List<LimitViolationInfos> result = new ArrayList<>();
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getLoadFlowResultUuid(nodeUuid);

        if (resultUuidOpt.isPresent()) {
            String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/limit-violations")
                .buildAndExpand(resultUuidOpt.get()).toUriString();
            try {
                var responseEntity = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<LimitViolationInfos>>() { });
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

    public LoadFlowParametersValues getLoadFlowParameters(UUID parametersUuid) {
        LoadFlowParametersValues parameters;

        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parametersUuid}")
            .buildAndExpand(parametersUuid).toUriString();
        try {
            parameters = restTemplate.getForObject(loadFlowServerBaseUri + path, LoadFlowParametersValues.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(LOADFLOW_PARAMETERS_NOT_FOUND);
            }
            throw e;
        }
        return parameters;
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

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    public UUID duplicateLoadFlowParameters(UUID sourceParametersUuid) {

        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters")
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand()
                .toUriString();

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    public void updateLoadFlowParameters(UUID parametersUuid, String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_LOADFLOW_PARAMETERS_FAILED);
        }
    }

    public void deleteLoadFlowParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parametersUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(loadFlowServerBaseUri + path);
    }

    public UUID createDefaultLoadFlowParameters() {

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/default")
                .buildAndExpand()
                .toUriString();

        UUID parametersUuid;

        try {
            parametersUuid = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_LOADFLOW_PARAMETERS_FAILED);
        }

        return parametersUuid;
    }

    @Transactional
    public UUID getLoadFlowParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getLoadFlowParametersUuid() == null) {
            studyEntity.setLoadFlowParametersUuid(createDefaultLoadFlowParameters());
        }
        return studyEntity.getLoadFlowParametersUuid();
    }
}
