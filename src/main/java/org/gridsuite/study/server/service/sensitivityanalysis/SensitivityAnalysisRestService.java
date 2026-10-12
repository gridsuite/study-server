/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.sensitivityanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisRestService extends AbstractComputationRestService implements ComputationParameters {

    static final String RESULT_UUID = "resultUuid";
    private static final String RESULTS = "results";
    private static final String QUERY_PARAM_RESULTS_SELECTOR = "selector";
    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    private final ObjectMapper objectMapper;

    SensitivityAnalysisRestService(RemoteServicesProperties remoteServicesProperties,
                                   RestClient restClient,
                                   ObjectMapper objectMapper) {
        super(remoteServicesProperties.getServiceUri("sensitivity-analysis-server"), restClient);
        this.objectMapper = objectMapper;
    }

    public UUID runSensitivityAnalysis(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid,
                                       String variantId,
                                       UUID reportUuid,
                                       String userId,
                                       UUID parametersUuid,
                                       UUID loadFlowParametersUuid,
                                       Map<UUID, String> elementsIdNameMap) {
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

        var requestSpec = restClient.post().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON);
        if (elementsIdNameMap != null) {
            requestSpec.body(elementsIdNameMap);
        }
        return requestSpec.retrieve().body(UUID.class);
    }

    public String getSensitivityAnalysisResult(UUID resultUuid, UUID networkUuid, String variantId, String selector, String filters, String globalFilters) {

        if (resultUuid == null) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUri)
            .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString())
            .queryParam(QUERY_PARAM_RESULTS_SELECTOR, selector);

        if (StringUtils.isNotBlank(filters)) {
            uriBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (!StringUtils.isEmpty(globalFilters)) {
            uriBuilder.queryParam("globalFilters", URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
            uriBuilder.queryParam("networkUuid", networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
        }
        URI uri = uriBuilder.build().encode().toUri();

        return restClient.get().uri(uri)
            .retrieve().body(String.class);
    }

    public ResponseEntity<byte[]> exportSensitivityResultsAsCsv(UUID resultUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos,
                                                                UUID networkUuid, String variantId, String selector, String filters,
                                                                String globalFilters) {
        if (resultUuid == null) {
            throw new StudyException(NOT_FOUND, "Result of sensitivity analysis was not found");
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(baseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString(), "csv")
                .queryParam(QUERY_PARAM_RESULTS_SELECTOR, selector);
        if (StringUtils.isNotBlank(filters)) {
            uriBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (!StringUtils.isEmpty(globalFilters)) {
            uriBuilder.queryParam("globalFilters", URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
            uriBuilder.queryParam("networkUuid", networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                uriBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
        }

        URI uri = uriBuilder.build().encode().toUri();

        var requestSpec = restClient.post().uri(uri)
            .contentType(MediaType.APPLICATION_JSON);
        if (sensitivityAnalysisCsvFileInfos != null) {
            requestSpec.body(sensitivityAnalysisCsvFileInfos);
        }
        return requestSpec.retrieve().toEntity(byte[].class);
    }

    public String getSensitivityResultsFilterOptions(UUID resultUuid, String selector) {
        if (resultUuid == null) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(baseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, RESULTS, resultUuid.toString(), "filter-options")
                .queryParam(QUERY_PARAM_RESULTS_SELECTOR, selector).build().encode().toUri();

        return restClient.get().uri(uri)
            .retrieve().body(String.class);
    }

    public String getSensitivityAnalysisStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    public void stopSensitivityAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

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

        restClient.put().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void invalidateSensitivityAnalysisStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restClient.put().uri(baseUri + path)
                .body(Void.class)
                .retrieve().toBodilessEntity();
        }
    }

    public void deleteSensitivityAnalysisResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results", restClient, baseUri);
    }

    public void deleteAllSensitivityAnalysisResults() {
        deleteSensitivityAnalysisResults(null);
    }

    public Integer getSensitivityAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/supervision/results-count").toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(Integer.class);
    }

    public UUID getSensitivityAnalysisParametersUuidOrElseCreateDefault(StudyEntity studyEntity) {
        if (studyEntity.getSensitivityAnalysisParametersUuid() == null) {
            // not supposed to happen because we create it as the study creation
            studyEntity.setSensitivityAnalysisParametersUuid(createDefaultParameters());
        }
        return studyEntity.getSensitivityAnalysisParametersUuid();
    }

    public String getSensitivityAnalysisParameters(UUID parametersUuid) {

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid)
            .toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    @Override
    public UUID createDefaultParameters() {

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/default")
            .buildAndExpand()
            .toUriString();

        return restClient.post().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public UUID createSensitivityAnalysisParameters(String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters")
            .buildAndExpand()
            .toUriString();

        return restClient.post().uri(baseUri + path)
            .body(parameters)
            .retrieve().body(UUID.class);
    }

    @Override
    public UUID duplicateParameters(UUID sourceParametersUuid) {

        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS + "/{uuid}/duplicate")
            .buildAndExpand(sourceParametersUuid)
            .toUriString();

        return restClient.post().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public void updateSensitivityAnalysisParameters(UUID parametersUuid, @Nullable String parameters) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid)
            .toUriString();

        var requestSpec = restClient.put().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    @Override
    public void deleteParameters(UUID uuid) {
        Objects.requireNonNull(uuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        restClient.delete().uri(baseUri + path)
            .retrieve().toBodilessEntity();
    }

    public String getSensitivityAnalysisFactorCount(UUID networkUuid, String variantId, String sensitivityAnalysisParameters) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/factor-count");
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        var requestSpec = restClient.post().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (sensitivityAnalysisParameters != null) {
            requestSpec.body(sensitivityAnalysisParameters);
        }
        return requestSpec.retrieve().body(String.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    public List<UUID> getElementIds(UUID parametersUuid) {

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + PARAMETERS_URI + "/contingency-lists-and-filters")
            .buildAndExpand(parametersUuid)
            .toUriString();

        try {
            List<UUID> elementIds = restClient.get().uri(baseUri + path)
                .retrieve().body(new ParameterizedTypeReference<List<UUID>>() { });
            return elementIds != null ? elementIds : List.of();

        } catch (RestClientException e) {
            return List.of();
        }
    }

    public String getProviders() {
        return getRestClient().get().uri(getBaseUri() + DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/providers")
            .retrieve().body(String.class);
    }

    public String getParameters(UUID parameterUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        return getRestClient().get().uri(getBaseUri() + path)
            .retrieve().body(String.class);
    }

    public void updateParameters(UUID parameterUuid, @Nullable String parameters) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        var requestSpec = getRestClient().put().uri(getBaseUri() + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        requestSpec.retrieve().toBodilessEntity();
    }
}
