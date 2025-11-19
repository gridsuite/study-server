/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.utils.ResultParameters;
import org.gridsuite.study.server.utils.StudyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.COMPUTATION_RUNNING;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@Service
public class PccMinService extends AbstractComputationService {
    static final String RESULT_UUID = "resultUuid";
    static final String BUS_ID = "busId";
    private static final String PARAMETER_UUID = "{parametersUuid}";
    private static final String PCC_MIN_URI = DELIMITER + PCC_MIN_API_VERSION;
    private static final String PARAMETERS_URI = PCC_MIN_URI + DELIMITER + PATH_PARAM_PARAMETERS;
    private static final String PARAMETER_URI = PARAMETERS_URI + DELIMITER + PARAMETER_UUID;
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Setter
    private String pccMinServerBaseUri;

    @Autowired
    public PccMinService(RemoteServicesProperties remoteServicesProperties,
                         ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.pccMinServerBaseUri = remoteServicesProperties.getServiceUri("pcc-min-server");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public UUID runPccMin(UUID networkUuid, String variantId, RunPccMinParametersInfos parametersInfos, ReportInfos reportInfos, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(PCC_MIN_URI + DELIMITER + "networks/{networkUuid}/run-and-save")
            .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid().toString())
            .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
            .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.PCC_MIN.reportKey);

        if (parametersInfos.getShortCircuitParametersUuid() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_SHORT_CIRCUIT_UUID, parametersInfos.getShortCircuitParametersUuid());
        }
        if (parametersInfos.getPccMinParametersUuid() != null) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_PCC_MIN_UUID, parametersInfos.getPccMinParametersUuid());
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (!StringUtils.isBlank(parametersInfos.getBusId())) {
            uriComponentsBuilder.queryParam(BUS_ID, parametersInfos.getBusId());
        }
        var path = uriComponentsBuilder.queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopPccMin(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

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
            .fromPath(PCC_MIN_URI + DELIMITER + "results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.put(pccMinServerBaseUri + path, Void.class);
    }

    public String getPccMinStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        String path = UriComponentsBuilder
            .fromPath(PCC_MIN_URI + DELIMITER + "results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        return restTemplate.getForObject(pccMinServerBaseUri + path, String.class);
    }

    public void deletePccMinResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + PCC_MIN_API_VERSION + "/results", restTemplate, pccMinServerBaseUri);
    }

    public void deleteAllPccMinResults() {
        deletePccMinResults(null);
    }

    public Integer getPccMinResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(PCC_MIN_URI + DELIMITER + "supervision/results-count").toUriString();
        return restTemplate.getForObject(pccMinServerBaseUri + path, Integer.class);
    }

    public void assertPccMinNotRunning(UUID resultUuid) {
        String status = getPccMinStatus(resultUuid);
        if (PccMinStatus.RUNNING.name().equals(status)) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }

    public void invalidatePccMinStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(PCC_MIN_URI + DELIMITER + "results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(pccMinServerBaseUri + path, Void.class);
        }
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuidOpt) {
        return List.of();
    }

    private String gePccMinResultsPageResourcePath(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        return UriComponentsBuilder.fromPath(PCC_MIN_URI + DELIMITER + "results" + "/{resultUuid}").buildAndExpand(resultUuid).toUriString();
    }

    public String getPccMinResultsPage(ResultParameters resultParameters, String filters, String globalFilters, Pageable pageable) {
        String resultsPath = gePccMinResultsPageResourcePath(resultParameters.getResultUuid());
        if (resultsPath == null) {
            return null;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(pccMinServerBaseUri + resultsPath);
        if (filters != null && !filters.isEmpty()) {
            builder.queryParam(QUERY_PARAM_FILTERS, filters);
        }
        if (globalFilters != null && !globalFilters.isEmpty()) {
            builder.queryParam(QUERY_PARAM_GLOBAL_FILTERS, URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
            builder.queryParam(QUERY_PARAM_NETWORK_UUID, resultParameters.getNetworkUuid());
            if (!StringUtils.isBlank(resultParameters.getVariantId())) {
                builder.queryParam(QUERY_PARAM_VARIANT_ID, resultParameters.getVariantId());
            }
        }
        StudyUtils.addPageableToQueryParams(builder, pageable);
        return restTemplate.getForObject(builder.build().encode().toUri(), String.class);
    }

    public UUID createPccMinParameters(String parameters) {
        var path = UriComponentsBuilder
            .fromPath(PARAMETERS_URI)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updatePccMinParameters(UUID parametersUuid, @Nullable String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(PARAMETER_URI);
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        restTemplate.put(pccMinServerBaseUri + path, httpEntity);
    }

    public UUID getPccMinParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getPccMinParametersUuid() == null) {
            studyEntity.setPccMinParametersUuid(createDefaultPccMinParameters());
        }
        return studyEntity.getPccMinParametersUuid();
    }

    public UUID createDefaultPccMinParameters() {
        var path = UriComponentsBuilder
            .fromPath(PARAMETERS_URI + DELIMITER + "default")
            .buildAndExpand()
            .toUriString();

        return restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public String getPccMinParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String path = UriComponentsBuilder.fromPath(PARAMETER_URI)
            .buildAndExpand(parametersUuid).toUriString();

        return restTemplate.getForObject(pccMinServerBaseUri + path, String.class);
    }

    public void deletePccMinParameters(UUID uuid) {
        Objects.requireNonNull(uuid);

        String path = UriComponentsBuilder
            .fromPath(PARAMETER_URI)
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(pccMinServerBaseUri + path);
    }

    public UUID duplicatePccMinParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String path = UriComponentsBuilder
            .fromPath(PARAMETERS_URI)
            .queryParam("duplicateFrom", sourceParametersUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }
}
