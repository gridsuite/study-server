/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service.shortcircuit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.lang.Nullable;
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
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Service
public class ShortCircuitService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";

    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    @Setter
    private String shortCircuitServerBaseUri;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Autowired
    public ShortCircuitService(RemoteServicesProperties remoteServicesProperties,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.shortCircuitServerBaseUri = remoteServicesProperties.getServiceUri("shortcircuit-server");
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID runShortCircuit(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid, String variantId, String busId, Optional<UUID> parametersUuid, UUID reportUuid, String userId) {

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString())
                .queryParam("reportType", StringUtils.isBlank(busId) ? StudyService.ReportType.SHORT_CIRCUIT.reportKey :
                        StudyService.ReportType.SHORT_CIRCUIT_ONE_BUS.reportKey)
                .queryParamIfPresent("parametersUuid", parametersUuid);

        if (!StringUtils.isBlank(busId)) {
            uriComponentsBuilder.queryParam("busId", busId);
        }

        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ShortCircuitParameters> httpEntity = new HttpEntity<>(headers);

        return restTemplate.exchange(shortCircuitServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
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

    public String getShortCircuitAnalysisResult(UUID rootNetworkUuid, String variantId, UUID resultUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, String globalFilters, boolean paged, Pageable pageable) {
        if (paged) {
            return getShortCircuitAnalysisResultsPage(rootNetworkUuid, variantId, resultUuid, mode, type, filters, globalFilters, pageable);
        } else {
            return getShortCircuitAnalysisResult(resultUuid, mode);
        }
    }

    private String getShortCircuitAnalysisCsvResultResourcePath(UUID resultUuid) {
        if (resultUuid == null) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
        }
        String path = DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/csv";
        return UriComponentsBuilder.fromPath(path).buildAndExpand(resultUuid).toUriString();
    }

    public byte[] getShortCircuitAnalysisCsvResultResource(URI resourcePath, String headersCsv) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(headersCsv, headers);
        try {
            return restTemplate.exchange(resourcePath, HttpMethod.POST, entity, byte[].class).getBody();
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SHORT_CIRCUIT_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SHORT_CIRCUIT_ANALYSIS_ERROR);
            }
        }
    }

    public byte[] getShortCircuitAnalysisCsvResult(UUID resultUuid, String headersCsv) {
        String resultPath = getShortCircuitAnalysisCsvResultResourcePath(resultUuid);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath);
        return getShortCircuitAnalysisCsvResultResource(builder.build().toUri(), headersCsv);
    }

    public String getShortCircuitAnalysisResult(UUID resultUuid, FaultResultsMode mode) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(resultUuid);
        if (resultPath == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultPath)
                .queryParam("mode", mode);

        return getShortCircuitAnalysisResource(builder.build().toUri());
    }

    public String getShortCircuitAnalysisResultsPage(UUID rootNetworkUuid, String variantId, UUID resultUuid, FaultResultsMode mode, ShortcircuitAnalysisType type, String filters, String globalFilters, Pageable pageable) {
        String resultsPath = getShortCircuitAnalysisResultsPageResourcePath(resultUuid, type);
        if (resultsPath == null) {
            return null;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri + resultsPath)
                .queryParam("rootNetworkUuid", rootNetworkUuid)
                .queryParam("variantId", variantId)
                .queryParam("mode", mode);

        if (filters != null && !filters.isEmpty()) {
            builder.queryParam("filters", filters);
        }

        if (globalFilters != null && !globalFilters.isEmpty()) {
            builder.queryParam("globalFilters", globalFilters);
        }

        addPageableToQueryParams(builder, pageable);

        return getShortCircuitAnalysisResource(builder.build().encode().toUri()); // need to encode because of filter JSON array
    }

    public String getShortCircuitAnalysisStatus(UUID resultUuid) {
        String resultPath = getShortCircuitAnalysisResultResourcePath(resultUuid);
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
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(shortCircuitServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public void deleteShortCircuitAnalysisResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results", restTemplate, shortCircuitServerBaseUri);
    }

    public void deleteAllShortCircuitAnalysisResults() {
        deleteShortCircuitAnalysisResults(null);
    }

    public Integer getShortCircuitResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(shortCircuitServerBaseUri + path, Integer.class);
    }

    public void assertShortCircuitAnalysisNotRunning(UUID scsResultUuid, UUID oneBusScsResultUuid) {
        String scs = getShortCircuitAnalysisStatus(scsResultUuid);
        String oneBusScs = getShortCircuitAnalysisStatus(oneBusScsResultUuid);
        if (ShortCircuitStatus.RUNNING.name().equals(scs) || ShortCircuitStatus.RUNNING.name().equals(oneBusScs)) {
            throw new StudyException(SHORT_CIRCUIT_ANALYSIS_RUNNING);
        }
    }

    public void invalidateShortCircuitStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(shortCircuitServerBaseUri + path, Void.class);
        }
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, SHORT_CIRCUIT_API_VERSION, shortCircuitServerBaseUri, SHORT_CIRCUIT_ANALYSIS_NOT_FOUND, restTemplate);
    }

    private UriComponentsBuilder getBaseUriForParameters() {
        return UriComponentsBuilder.fromUriString(shortCircuitServerBaseUri).pathSegment(SHORT_CIRCUIT_API_VERSION, "parameters");
    }

    public UUID createParameters(@Nullable final String parametersInfos) {
        final UriComponentsBuilder uri = getBaseUriForParameters();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            if (StringUtils.isBlank(parametersInfos)) {
                return restTemplate.postForObject(uri.pathSegment("default").build().toUri(), new HttpEntity<>(headers), UUID.class);
            } else {
                headers.setContentType(MediaType.APPLICATION_JSON);
                return restTemplate.postForObject(uri.build().toUri(), new HttpEntity<>(parametersInfos, headers), UUID.class);
            }
        } catch (final HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SHORTCIRCUIT_PARAMETERS_FAILED);
        }
    }

    public void updateParameters(final UUID parametersUuid, @Nullable final String parametersInfos) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            restTemplate.put(getBaseUriForParameters()
                .pathSegment("{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUri(), new HttpEntity<>(parametersInfos, headers));
        } catch (final HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SHORTCIRCUIT_PARAMETERS_FAILED);
        }
    }

    public String getParameters(UUID parametersUuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            return restTemplate.exchange(getBaseUriForParameters()
                .pathSegment("{parametersUuid}")
                .buildAndExpand(parametersUuid)
                .toUri(), HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        } catch (final HttpStatusCodeException e) {
            throw handleHttpError(e, GET_SHORTCIRCUIT_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateParameters(UUID parametersUuid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            return restTemplate.postForObject(getBaseUriForParameters()
                .queryParam("duplicateFrom", parametersUuid)
                .build()
                .toUri(), new HttpEntity<>(headers), UUID.class);
        } catch (final HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SHORTCIRCUIT_PARAMETERS_FAILED);
        }
    }

    public void deleteShortcircuitParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + SHORT_CIRCUIT_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(shortCircuitServerBaseUri + path);
    }
}
