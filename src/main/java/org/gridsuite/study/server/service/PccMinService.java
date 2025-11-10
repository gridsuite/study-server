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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.utils.ResultParameters;
import org.gridsuite.study.server.utils.StudyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import java.util.UUID;
import org.springframework.data.domain.Pageable;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.StudyException.Type.CREATE_PCC_MIN_PARAMETERS_FAILED;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Maissa SOUISSI <maissa.souissi at rte-france.com>
 */
@Service
public class PccMinService extends AbstractComputationService {
    static final String RESULT_UUID = "resultUuid";
    static final String FILTER_UUID = "filterUuid";
    static final String BUS_ID = "busId";
    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

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
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/networks/{networkUuid}/run-and-save")
            .queryParam(QUERY_PARAM_REPORT_UUID, reportInfos.reportUuid().toString())
            .queryParam(QUERY_PARAM_REPORTER_ID, reportInfos.nodeUuid())
            .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.PCC_MIN.reportKey);

        if (parametersInfos.getShortCircuitParametersUuid() != null) {
            uriComponentsBuilder.queryParam("shortCircuitParametersUuid", parametersInfos.getShortCircuitParametersUuid());
        }
        if (parametersInfos.getFilterUuid() != null) {
            uriComponentsBuilder.queryParam(FILTER_UUID, parametersInfos.getFilterUuid());
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
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.put(pccMinServerBaseUri + path, Void.class);
    }

    public String getPccMinStatus(UUID resultUuid) {
        if (resultUuid == null) {
            return null;
        }
        try {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/results/{resultUuid}/status")
                .buildAndExpand(resultUuid).toUriString();
            return restTemplate.getForObject(pccMinServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(PCC_MIN_NOT_FOUND);
            }
            throw e;
        }
    }

    public void deletePccMinResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + PCC_MIN_API_VERSION + "/results", restTemplate, pccMinServerBaseUri);
    }

    public void deleteAllPccMinResults() {
        deletePccMinResults(null);
    }

    public Integer getPccMinResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(pccMinServerBaseUri + path, Integer.class);
    }

    public void assertPccMinNotRunning(UUID resultUuid) {
        String status = getPccMinStatus(resultUuid);
        if (PccMinStatus.RUNNING.name().equals(status)) {
            throw new StudyException(PCC_MIN_RUNNING);
        }
    }

    public void invalidatePccMinStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/results/invalidate-status")
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
        return UriComponentsBuilder.fromPath(DELIMITER + PCC_MIN_API_VERSION + "/results" + "/{resultUuid}").buildAndExpand(resultUuid).toUriString();
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
        return getPccMinResource(builder.build().encode().toUri());
    }

    public String getPccMinResource(URI resourcePath) {
        String result;
        try {
            result = restTemplate.getForObject(resourcePath, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(PCC_MIN_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public UUID createPccMinParameters(String parameters) {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/parameters")
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_PCC_MIN_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

    public void updatePccMinParameters(UUID parametersUuid, @Nullable String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + PCC_MIN_API_VERSION + "/parameters/{uuid}");
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.put(pccMinServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_PCC_MIN_PARAMETERS_FAILED);
        }
    }

    public UUID getPccMinParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getPccMinParametersUuid() == null) {
            studyEntity.setPccMinParametersUuid(createDefaultPccMinParameters());
        }
        return studyEntity.getPccMinParametersUuid();
    }

    public UUID createDefaultPccMinParameters() {
        var path = UriComponentsBuilder
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + "/parameters/default")
            .buildAndExpand()
            .toUriString();

        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_PCC_MIN_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

    public String getPccMinParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parameters;

        String path = UriComponentsBuilder.fromPath(DELIMITER + PCC_MIN_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(parametersUuid).toUriString();

        try {
            parameters = restTemplate.getForObject(pccMinServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(PCC_MIN_PARAMETERS_NOT_FOUND);
            }

            throw handleHttpError(e, GET_PCC_MIN_PARAMETERS_FAILED);
        }
        return parameters;
    }

    public void deletePccMinParameters(UUID uuid) {
        Objects.requireNonNull(uuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + PARAMETERS_URI)
            .buildAndExpand(uuid)
            .toUriString();

        try {
            restTemplate.delete(pccMinServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_PCC_MIN_PARAMETERS_FAILED);
        }
    }

    public UUID duplicatePccMinParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + PCC_MIN_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
            .queryParam("duplicateFrom", sourceParametersUuid)
            .buildAndExpand()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        try {
            return restTemplate.exchange(pccMinServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_PCC_MIN_PARAMETERS_FAILED);
        }
    }
}
