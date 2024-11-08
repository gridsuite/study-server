/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityFactorsIdsByGroup;
import org.gridsuite.study.server.repository.StudyEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService {

    static final String RESULT_UUID = "resultUuid";
    private static final String RESULTS = "results";
    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    private String sensitivityAnalysisServerBaseUri;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    SensitivityAnalysisService(RemoteServicesProperties remoteServicesProperties,
                               NetworkModificationTreeService networkModificationTreeService,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.sensitivityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("sensitivity-analysis-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void setSensitivityAnalysisServerBaseUri(String sensitivityAnalysisServerBaseUri) {
        this.sensitivityAnalysisServerBaseUri = sensitivityAnalysisServerBaseUri + DELIMITER;
    }

    public UUID runSensitivityAnalysis(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid,
                                       String variantId,
                                       UUID reportUuid,
                                       String userId,
                                       UUID parametersUuid,
                                       UUID loadFlowParametersUuid) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
            .queryParam("reportUuid", reportUuid.toString())
            .queryParam("reporterId", nodeUuid.toString())
            .queryParam("reportType", StudyService.ReportType.SENSITIVITY_ANALYSIS.reportKey);
        if (parametersUuid != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersUuid.toString());
        }
        if (loadFlowParametersUuid != null) {
            uriComponentsBuilder.queryParam("loadFlowParametersUuid", loadFlowParametersUuid);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getSensitivityAnalysisResult(UUID nodeUuid, UUID rootNetworkUuid, String selector) {
        String result;
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SENSITIVITY_ANALYSIS);
        if (resultUuid == null) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
            .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString())
            .queryParam("selector", selector).build().encode().toUri();
        try {
            result = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SENSITIVITY_ANALYSIS_ERROR);
            }
        }
        return result;
    }

    public byte[] exportSensitivityResultsAsCsv(UUID nodeUuid, UUID rootNetworkUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SENSITIVITY_ANALYSIS);
        if (resultUuid == null) {
            throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString(), "csv")
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SensitivityAnalysisCsvFileInfos> httpEntity = new HttpEntity<>(sensitivityAnalysisCsvFileInfos, headers);
        try {
            return restTemplate.exchange(uri, HttpMethod.POST, httpEntity, byte[].class).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SENSITIVITY_ANALYSIS_ERROR);
            }
        }

    }

    public String getSensitivityResultsFilterOptions(UUID nodeUuid, UUID rootNetworkUuid, String selector) {
        String options;
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SENSITIVITY_ANALYSIS);
        if (resultUuid == null) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString(), "filter-options")
                .queryParam("selector", selector).build().encode().toUri();
        try {
            options = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SENSITIVITY_ANALYSIS_ERROR);
            }
        }
        return options;
    }

    public String getSensitivityAnalysisStatus(UUID nodeUuid, UUID rootNetworkUuid) {
        String result;
        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SENSITIVITY_ANALYSIS);

        if (resultUuid == null) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        try {
            result = restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public void stopSensitivityAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

        UUID resultUuid = networkModificationTreeService.getComputationResultUuid(nodeUuid, rootNetworkUuid, ComputationType.SENSITIVITY_ANALYSIS);
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
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public void invalidateSensitivityAnalysisStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void deleteSensitivityAnalysisResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
    }

    public void deleteSensitivityAnalysisResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results")
                .toUriString();
            restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getSensitivityAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, Integer.class);
    }

    public void assertSensitivityAnalysisNotRunning(UUID nodeUuid, UUID rootNetworkUuid) {
        String sas = getSensitivityAnalysisStatus(nodeUuid, rootNetworkUuid);
        if (SensitivityAnalysisStatus.RUNNING.name().equals(sas)) {
            throw new StudyException(SENSITIVITY_ANALYSIS_RUNNING);
        }
    }

    public UUID getSensitivityAnalysisParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getSensitivityAnalysisParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setSensitivityAnalysisParametersUuid(createDefaultSensitivityAnalysisParameters());
        }
        return studyEntity.getSensitivityAnalysisParametersUuid();
    }

    public String getSensitivityAnalysisParameters(UUID parametersUuid) {

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid)
            .toUriString();
        try {
            return restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_PARAMETERS_NOT_FOUND);
            }
            throw handleHttpError(e, GET_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID createDefaultSensitivityAnalysisParameters() {

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/default")
            .buildAndExpand()
            .toUriString();

        try {
            return restTemplate.postForObject(sensitivityAnalysisServerBaseUri + path, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID createSensitivityAnalysisParameters(String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            return restTemplate.postForObject(sensitivityAnalysisServerBaseUri + path, httpEntity, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateSensitivityAnalysisParameters(UUID sourceParametersUuid) {

        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
                .queryParam("duplicateFrom", sourceParametersUuid)
            .buildAndExpand()
            .toUriString();

        try {
            return restTemplate.postForObject(sensitivityAnalysisServerBaseUri + path, null, UUID.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public void updateSensitivityAnalysisParameters(UUID parametersUuid, String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.put(sensitivityAnalysisServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public void deleteSensitivityAnalysisParameters(UUID uuid) {

        Objects.requireNonNull(uuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        try {
            restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_SENSITIVITY_ANALYSIS_PARAMETERS_FAILED);
        }

    }

    public String getSensitivityAnalysisDefaultProvider() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/default-provider")
                .buildAndExpand()
                .toUriString();

        try {
            return restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, GET_SENSITIVITY_ANALYSIS_DEFAULT_PROVIDER_FAILED);
        }
    }

    public Long getSensitivityAnalysisFactorsCount(UUID networkUuid, String variantId, SensitivityFactorsIdsByGroup factorsIds, Boolean isInjectionsSet) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/factors-count")
                .queryParam("isInjectionsSet", isInjectionsSet);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        factorsIds.getIds().forEach((key, value) -> uriComponentsBuilder.queryParam(String.format("ids[%s]", key), value));

        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.GET, null, Long.class).getBody();
    }
}
