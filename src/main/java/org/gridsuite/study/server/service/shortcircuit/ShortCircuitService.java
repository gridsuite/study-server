/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.NetworkService;
import org.gridsuite.study.server.service.StudyService.ReportType;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.SerializationUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.addPageableToQueryParams;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitService {
    @Setter private String shortCircuitServerBaseUri;

    private final NetworkService networkStoreService;
    private final NetworkModificationTreeService networkModificationTreeService;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ShortCircuitService(RemoteServicesProperties remoteServicesProperties,
                               NetworkModificationTreeService networkModificationTreeService,
                               NetworkService networkStoreService,
                               RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.shortCircuitServerBaseUri = remoteServicesProperties.getServiceUri("shortcircuit-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public UUID runShortCircuit(UUID studyUuid, UUID nodeUuid, @Nullable String busId, UUID shortCircuitParameters, String userId) {
        final UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        final String variantId = networkModificationTreeService.getVariantId(nodeUuid);
        final UUID reportUuid = networkModificationTreeService.getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder.fromHttpUrl(shortCircuitServerBaseUri)
                .pathSegment(SHORT_CIRCUIT_API_VERSION, "networks", "{networkUuid}", "run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString())
                .queryParam("reportType", StringUtils.isBlank(busId) ?
                        ReportType.ALL_BUSES_SHORTCIRCUIT_ANALYSIS.reportKey : ReportType.ONE_BUS_SHORTCIRCUIT_ANALYSIS.reportKey)
                .queryParam("parametersUuid", shortCircuitParameters);
        if (!StringUtils.isBlank(busId)) {
            uriComponentsBuilder.queryParam("busId", busId);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return restTemplate.postForObject(uriComponentsBuilder.build(networkUuid), new HttpEntity<Void>(headers), UUID.class);
    }

    private String getShortCircuitAnalysisResultResourcePath(UUID nodeUuid, ShortcircuitAnalysisType type) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, type);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }
        return UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results" + "/{resultUuid}").buildAndExpand(resultUuidOpt.get()).toUriString();
    }

    private String getShortCircuitAnalysisResultsPageResourcePath(UUID nodeUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
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

    public String getShortCircuitAnalysisResult(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, boolean paged, Pageable pageable) {
        if (paged) {
            return getShortCircuitAnalysisResultsPage(nodeUuid, mode, type, filters, pageable);
        } else {
            return getShortCircuitAnalysisResult(nodeUuid, mode, type);
        }
    }

    public String getShortCircuitAnalysisResult(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath)
                .queryParam("mode", mode);

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResultsPage(UUID nodeUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, Pageable pageable) {
        String resultsPath = getShortCircuitAnalysisResultsPageResourcePath(nodeUuid, type);
        if (resultsPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultsPath)
                .queryParam("mode", mode);

        if (filters != null && !filters.isEmpty()) {
            builder.queryParam("filters", filters);
        }

        addPageableToQueryParams(builder, pageable);

        return getShortCircuitAnalysisResource(builder.build().encode().toUri()); // need to encode because of filter JSON array
    }

    public String getShortCircuitAnalysisStatus(UUID nodeUuid, ShortcircuitAnalysisType type) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(nodeUuid, type);
        if (resultPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath + "/status");

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResource(URI resourcePath) {
        String result;
        try {
            result = restTemplate.getForObject(resourcePath, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public void stopShortCircuitAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getShortCircuitAnalysisResultUuid(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
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
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
    }

    public void deleteShortCircuitAnalysisResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(shortCircuitServerBaseUri + path);
    }

    public void deleteShortCircuitAnalysisResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results")
                .toUriString();
            restTemplate.delete(shortCircuitServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getShortCircuitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(shortCircuitServerBaseUri + path, Integer.class);
    }

    public void assertShortCircuitAnalysisNotRunning(UUID nodeUuid) {
        String scs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ALL_BUSES);
        String oneBusScs = getShortCircuitAnalysisStatus(nodeUuid, ShortcircuitAnalysisType.ONE_BUS);
        if (ShortCircuitStatus.RUNNING.name().equals(scs) || ShortCircuitStatus.RUNNING.name().equals(oneBusScs)) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_RUNNING);
        }
    }

    public void invalidateShortCircuitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/invalidate-status")
                    .queryParam("resultUuid", uuids).build().toUriString();

            restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
        }
    }

    /**
     * Create new parameters instance using defaults
     * @return the UUID of saved parameters
     */
    public UUID createParameters() {
        return this.createParameters(null);
    }

    /**
     * Create new parameters instance
     * @param jsonParametersInfo the parameter to use instead of defaults
     * @return the UUID of saved parameters
     */
    public UUID createParameters(@Nullable final String jsonParametersInfo) {
        final ResponseEntity<UUID> response = restTemplate.exchange(UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri)
                .pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters")
                .build().toUri(), HttpMethod.PUT, jsonParametersInfo == null ? null : new HttpEntity<>(jsonParametersInfo), UUID.class);
        if (response.getStatusCode().isError()) {
            throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "Request to short-circuit failed", new HttpClientErrorException(
                    response.getStatusCode(),
                    Optional.ofNullable(HttpStatus.resolve(response.getStatusCode().value())).map(HttpStatus::getReasonPhrase).orElseGet(() -> response.getStatusCode().toString()),
                    response.getHeaders(), SerializationUtils.serialize(response.getBody()), StandardCharsets.UTF_16));
        } else {
            return response.getBody();
        }
    }

    /**
     * Create or update parameters with the specified UUID
     * @param parametersUuid the UUID to use to create or update parameters
     * @param jsonParametersInfo the parameters to use, or use defaults ones if {@code null}
     */
    public void setParametersInfo(final UUID parametersUuid, @Nullable final String jsonParametersInfo) {
        restTemplate.put(UriComponentsBuilder.fromHttpUrl(shortCircuitServerBaseUri)
                .pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters", "{parametersUuid}")
                .build(Map.of("parametersUuid", parametersUuid)), jsonParametersInfo);
    }

    public String getParametersInfo(final UUID parametersUuid) {
        return restTemplate.getForObject(UriComponentsBuilder.fromHttpUrl(shortCircuitServerBaseUri)
                .pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters", "{parametersUuid}")
                .build(Map.of("parametersUuid", parametersUuid)), String.class);
    }

    /**
     * duplicate existing parameters
     * @param parametersUuid the parameters to duplicate
     * @return the UUID of the duplicated instance
     */
    public UUID duplicateParameters(@NonNull final UUID parametersUuid) {
        return restTemplate.postForObject(UriComponentsBuilder.fromHttpUrl(shortCircuitServerBaseUri)
                .pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters", "{parametersUuid}", "duplicate")
                .build(Map.of("parametersUuid", parametersUuid)), null, UUID.class);
    }
}
