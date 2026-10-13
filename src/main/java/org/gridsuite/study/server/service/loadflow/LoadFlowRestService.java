/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.loadflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.loadflow.LoadFlowParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import static org.gridsuite.study.server.StudyConstants.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class LoadFlowRestService extends AbstractComputationRestService implements ComputationParameters {
    private static final String QUERY_PARAM_APPLY_SOLVED_VALUES = "applySolvedValues";
    private static final String RESULT_UUID = "resultUuid";

    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    private final ObjectMapper objectMapper;

    public record ParametersInfos(
        UUID parametersUuid,
        boolean withRatioTapChangers,
        boolean applySolvedValues
    ) {
    }

    public LoadFlowRestService(RemoteServicesProperties remoteServicesProperties,
                               ObjectMapper objectMapper,
                               RestClient restClient) {
        super(remoteServicesProperties.getServiceUri("loadflow-server"), restClient);
        this.objectMapper = objectMapper;
    }

    public UUID runLoadFlow(NodeReceiver nodeReceiver, UUID loadflowResultUuid,
                            VariantInfos variantInfos, ParametersInfos parametersInfos, UUID reportUuid, String userId) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(nodeReceiver), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_WITH_TAP_CHANGER, parametersInfos.withRatioTapChangers)
                .queryParam(QUERY_PARAM_APPLY_SOLVED_VALUES, parametersInfos.applySolvedValues)
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportUuid.toString())
                .queryParam(QUERY_PARAM_REPORTER_ID, nodeReceiver.getNodeUuid().toString())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.LOAD_FLOW.reportKey);

        if (loadflowResultUuid != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_RESULT_UUID, loadflowResultUuid);
        }
        if (parametersInfos.parametersUuid != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_PARAMETERS_UUID, parametersInfos.parametersUuid.toString());
        }
        if (!StringUtils.isBlank(variantInfos.getVariantId())) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantInfos.getVariantId());
        }
        var path = uriComponentsBuilder.buildAndExpand(variantInfos.getNetworkUuid()).toUriString();

        return restClient.post().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    public void deleteLoadFlowResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + LOADFLOW_API_VERSION + "/results", restClient, baseUri);
    }

    public Integer getLoadFlowResultsCount() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/supervision/results-count").toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(Integer.class);
    }

    public String getLoadFlowResult(UUID resultUuid, String filters, Sort sort) {
        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}");
        if (!StringUtils.isEmpty(filters)) {
            uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (sort != null) {
            sort.forEach(order -> uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection()));
        }
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    public String getLoadFlowModifications(UUID resultUuid) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/modifications");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    public LoadFlowStatus getLoadFlowStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/status");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(LoadFlowStatus.class);
    }

    public void stopLoadFlow(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
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
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restClient.put().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void invalidateLoadFlowStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restClient.put().uri(baseUri + path)
                .retrieve().toBodilessEntity();
        }
    }

    public UUID createRunningStatus() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/running-status")
            .toUriString();

        return restClient.post().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public List<LimitViolationInfos> getLimitViolations(UUID resultUuid, String filters, String globalFilters, Sort sort, UUID networkUuid, String variantId) {
        List<LimitViolationInfos> result = new ArrayList<>();

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
            return restClient.get().uri(baseUri + path)
                .retrieve().body(new ParameterizedTypeReference<List<LimitViolationInfos>>() {
                });
        }
        return result;
    }

    public List<LimitViolationInfos> getCurrentLimitViolations(UUID resultUuid) {
        UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/current-limit-violations");
        String path = uriComponentsBuilder.buildAndExpand(resultUuid).toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(new ParameterizedTypeReference<List<LimitViolationInfos>>() {
            });
    }

    public LoadFlowParameters getCommonParameters(UUID parametersUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid).toUriString();
        String parameters = restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
        try {
            JsonNode commonParametersNode = objectMapper.readTree(parameters).get("commonParameters");
            return objectMapper.treeToValue(commonParametersNode, LoadFlowParameters.class);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public UUID createLoadFlowParameters(String parameters) {

        Objects.requireNonNull(parameters);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        return restClient.post().uri(baseUri + path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(parameters)
            .retrieve().body(UUID.class);
    }

    @Override
    public UUID duplicateParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS + DELIMITER + "{uuid}" + DELIMITER + "duplicate")
                .buildAndExpand(sourceParametersUuid).toUriString();

        return restClient.post().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public void updateLoadFlowParameters(UUID parametersUuid, @Nullable String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
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
    public void deleteParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid)
                .toUriString();

        restClient.delete().uri(baseUri + path)
            .retrieve().toBodilessEntity();
    }

    @Override
    public UUID createDefaultParameters() {

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/default")
                .buildAndExpand()
                .toUriString();

        return restClient.post().uri(baseUri + path)
            .retrieve().body(UUID.class);
    }

    public UUID getLoadFlowParametersOrDefaultsUuid(StudyEntity studyEntity) {
        if (studyEntity.getLoadFlowParametersUuid() == null) {
            studyEntity.setLoadFlowParametersUuid(createDefaultParameters());
        }
        return studyEntity.getLoadFlowParametersUuid();
    }

    public String getLoadFlowProvider(UUID parametersUuid) {

        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + PARAMETERS_URI + "/provider")
                .buildAndExpand(parametersUuid).toUriString();

        return restClient.get().uri(baseUri + path)
            .retrieve().body(String.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, LOADFLOW_API_VERSION, restClient);
    }

    public String getProviders() {
        return restClient.get().uri(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/providers")
            .retrieve().body(String.class);
    }

    public String getSpecificParameters() {
        return restClient.get().uri(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/specific-parameters")
            .retrieve().body(String.class);
    }

    public String getDefaultLimitReductions() {
        return restClient.get().uri(getBaseUri() + DELIMITER + LOADFLOW_API_VERSION + "/parameters/default-limit-reductions")
            .retrieve().body(String.class);
    }

    public String getParameters(UUID parameterUuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        return restClient.get().uri(getBaseUri() + path)
            .retrieve().body(String.class);
    }

    public void updateParameters(UUID parameterUuid, @Nullable String parameters) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/parameters/{parameterUuid}").buildAndExpand(parameterUuid).toUriString();
        var requestSpec = restClient.put().uri(getBaseUri() + path)
            .contentType(MediaType.APPLICATION_JSON);
        if (parameters != null) {
            requestSpec.body(parameters);
        }
        requestSpec.retrieve().toBodilessEntity();
    }
}
