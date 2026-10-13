/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.VariantInfos;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationRestService;
import org.gridsuite.study.server.service.common.ComputationParameters;
import org.gridsuite.study.server.utils.ResultParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.*;
import static org.gridsuite.study.server.service.client.util.UrlUtil.buildEndPointUrl;
import static org.gridsuite.study.server.utils.StudyUtils.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitRestService extends AbstractComputationRestService implements ComputationParameters {

    static final String RESULT_UUID = "resultUuid";

    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";
    private final ObjectMapper objectMapper;

    @Autowired
    public ShortCircuitRestService(RemoteServicesProperties remoteServicesProperties,
                                   RestClient restClient,
                                   ObjectMapper objectMapper) {
        super(remoteServicesProperties.getServiceUri("shortcircuit-server"), restClient);
        this.objectMapper = objectMapper;
    }

    public UUID runShortCircuit(UUID rootNetworkUuid, VariantInfos variantInfos, String busId, UUID parametersUuid, ReportInfos reportInfos, String userId, boolean debug) {

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(reportInfos.nodeUuid(), rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid().toString())
                .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid().toString())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StringUtils.isBlank(busId) ? StudyService.ReportType.SHORT_CIRCUIT.reportKey :
                        StudyService.ReportType.SHORT_CIRCUIT_ONE_BUS.reportKey)
                .queryParam(QUERY_PARAM_PARAMETERS_UUID, parametersUuid);

        if (!StringUtils.isBlank(busId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_BUS_ID, busId);
        }

        if (!StringUtils.isBlank(variantInfos.getVariantId())) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantInfos.getVariantId());
        }

        if (debug) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_DEBUG, true);
        }

        var path = uriComponentsBuilder.buildAndExpand(variantInfos.getNetworkUuid()).toUriString();

        return restClient.post().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    private String getShortCircuitAnalysisResultResourcePath(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        return UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results" + "/{resultUuid}").buildAndExpand(resultUuid).toUriString();
    }

    private String getShortCircuitAnalysisResultsPageResourcePath(UUID resultUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(resultUuid);
        if (resultPath == null) {
            return null;
        }
        if (type == ShortcircuitAnalysisType.ALL_BUSES) {
            resultPath += "/fault_results";
        } else if (type == ShortcircuitAnalysisType.ONE_BUS) {
            resultPath += "/feeder_results";
        }
        return resultPath + "/paged";
    }

    public String getShortCircuitAnalysisResult(ResultParameters resultParameters, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, String globalFilters, boolean paged, Pageable
            pageable) {
        if (paged) {
            return getShortCircuitAnalysisResultsPage(resultParameters, mode, type, filters, globalFilters, pageable);
        } else {
            return getShortCircuitAnalysisResult(resultParameters.getResultUuid(), mode);
        }
    }

    private String getShortCircuitAnalysisCsvResultResourcePath(UUID resultUuid) {
        if (resultUuid == null) {
            throw new StudyException(NOT_FOUND, "Result of short circuit analysis was not found");
        }
        String path = DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/csv";
        return UriComponentsBuilder.fromPath(path).buildAndExpand(resultUuid).toUriString();
    }

    public ResponseEntity<byte[]> getShortCircuitAnalysisCsvResultResource(URI resourcePath, String headersCsv) {
        Objects.requireNonNull(headersCsv);
        return restClient.post().uri(resourcePath)
            .contentType(MediaType.APPLICATION_JSON)
            .body(headersCsv)
            .retrieve().toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> getShortCircuitAnalysisCsvResult(UUID resultUuid, UUID networkUuid, String variantId, String filters, String globalFilters, Sort sort, String headersCsv) {
        String resultPath = getShortCircuitAnalysisCsvResultResourcePath(resultUuid);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri + resultPath)
                .queryParam(QUERY_PARAM_NETWORK_UUID, networkUuid)
                .queryParam(QUERY_PARAM_VARIANT_ID, variantId);

        addFiltersToQueryParams(builder, filters, globalFilters);
        addSortToQueryParams(builder, sort);

        return getShortCircuitAnalysisCsvResultResource(builder.build().encode().toUri(), headersCsv); // need to encode because of filter JSON array
    }

    public String getShortCircuitAnalysisResult(UUID resultUuid, FaultResultsMode mode) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(resultUuid);
        if (resultPath == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri + resultPath)
                .queryParam(QUERY_PARAM_MODE, mode);

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResultsPage(ResultParameters resultParameters, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, String globalFilters, Pageable pageable) {
        String resultsPath = getShortCircuitAnalysisResultsPageResourcePath(resultParameters.getResultUuid(), type);
        if (resultsPath == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri + resultsPath)
                .queryParam(QUERY_PARAM_NETWORK_UUID, resultParameters.getNetworkUuid())
                .queryParam(QUERY_PARAM_VARIANT_ID, resultParameters.getVariantId())
                .queryParam(QUERY_PARAM_MODE, mode);

        addFiltersToQueryParams(builder, filters, globalFilters);
        addPageableToQueryParams(builder, pageable);

        return getShortCircuitAnalysisResource(builder.build().encode().toUri()); // need to encode because of filter JSON array
    }

    public String getShortCircuitAnalysisStatus(UUID resultUuid) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(resultUuid);
        if (resultPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri + resultPath + "/status");

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResource(URI resourcePath) {
        return restClient.get().uri(resourcePath)
            .retrieve().body(String.class);
    }

    public void stopShortCircuitAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
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
            throw new UncheckedIOException("Failed to serialize node receiver", e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restClient.put().uri(baseUri + path)
            .headers(headers -> headers.set(HEADER_USER_ID, userId))
            .contentType(MediaType.APPLICATION_JSON)
            .retrieve().toBodilessEntity();
    }

    public void deleteShortCircuitAnalysisResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results", restClient, baseUri);
    }

    public void deleteAllShortCircuitAnalysisResults() {
        deleteShortCircuitAnalysisResults(null);
    }

    public Integer getShortCircuitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/supervision/results-count").toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(Integer.class);
    }

    public void invalidateShortCircuitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restClient.put().uri(baseUri + path)
                .body(Void.class)
                .retrieve().toBodilessEntity();
        }
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, SHORT_CIRCUIT_API_VERSION, restClient);
    }

    private UriComponentsBuilder getBaseUriForParameters() {
        return UriComponentsBuilder.fromUriString(baseUri).pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters");
    }

    public UUID createParameters(@Nullable final String parametersInfos) {
        final UriComponentsBuilder uri = getBaseUriForParameters();
        if (StringUtils.isBlank(parametersInfos)) {
            return restClient.post().uri(uri.pathSegment("default").build().toUri())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve().body(UUID.class);
        } else {
            return restClient.post().uri(uri.build().toUri())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(parametersInfos)
                .retrieve().body(UUID.class);
        }
    }

    @Override
    public UUID createDefaultParameters() {
        return createParameters(null);
    }

    public void updateParameters(final UUID parametersUuid, @Nullable final String parametersInfos) {
        var requestSpec = restClient.put().uri(getBaseUriForParameters()
                .pathSegment("{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUri())
            .contentType(MediaType.APPLICATION_JSON);
        if (parametersInfos != null) {
            requestSpec.body(parametersInfos);
        }
        requestSpec.retrieve().toBodilessEntity();
    }

    public String getParameters(UUID parametersUuid) {
        return restClient.get().uri(getBaseUriForParameters()
                .pathSegment("{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUri())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve().body(String.class);
    }

    @Override
    public UUID duplicateParameters(UUID parametersUuid) {
        return restClient.post().uri(getBaseUriForParameters()
                .pathSegment("{parametersUuid}", "duplicate")
                .buildAndExpand(parametersUuid)
                .toUri())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve().body(UUID.class);
    }

    @Override
    public void deleteParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        restClient.delete().uri(baseUri + path)
            .retrieve().toBodilessEntity();
    }

    public Map<String, Double> getVoltageLevelIccValues(UUID resultUuid, String voltageLevelId) {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/fault_results/icc")
            .queryParam(QUERY_PARAM_VOLTAGE_LEVEL_ID, voltageLevelId)
            .buildAndExpand(resultUuid)
            .toUriString();
        return restClient.get().uri(baseUri + path)
            .retrieve().body(new ParameterizedTypeReference<Map<String, Double>>() { });
    }

    public ResponseEntity<Resource> downloadDebugFile(UUID resultUuid) {
        String resultBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, "results");
        String url = UriComponentsBuilder.fromUriString(resultBaseUrl + "/{resultUuid}/download-debug-file")
            .buildAndExpand(resultUuid)
            .toUriString();
        return getRestClient().get().uri(url)
            .retrieve().toEntity(Resource.class);
    }

    public String getSpecificParameters() {
        String parametersBaseUrl = buildEndPointUrl(getBaseUri(), SHORT_CIRCUIT_API_VERSION, "parameters");

        return getRestClient().get().uri(parametersBaseUrl + "/specific-parameters")
            .retrieve().body(String.class);
    }
}
