/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
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
public class SecurityAnalysisService {

    static final String RESULT_UUID = "resultUuid";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Setter
    private String securityAnalysisServerBaseUri;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    public SecurityAnalysisService(RemoteServicesProperties remoteServicesProperties,
                                   NetworkModificationTreeService networkModificationTreeService,
                                   ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.securityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("security-analysis-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, SecurityAnalysisResultType resultType, String filters, Pageable pageable) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.SECURITY_ANALYSIS);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/" + getPagedPathFromResultType(resultType))
            .queryParam("page", pageable.getPageNumber())
            .queryParam("size", pageable.getPageSize());

        if (filters != null && !filters.isEmpty()) {
            pathBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }

        for (Sort.Order order : pageable.getSort()) {
            pathBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection());
        }

        String path = pathBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();

        try {
            result = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result;
    }

    public byte[] getSecurityAnalysisResultCsv(UUID nodeUuid, SecurityAnalysisResultType resultType, String csvTranslations) {
        ResponseEntity<byte[]> result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.SECURITY_ANALYSIS);

        if (resultUuidOpt.isEmpty()) {
            throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/" + getExportPathFromResultType(resultType));

        String path = pathBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(csvTranslations, headers);
        try {
            result = restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, entity, byte[].class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            } else {
                throw e;
            }
        }

        return result.getBody();
    }

    private String getPagedPathFromResultType(SecurityAnalysisResultType resultType) {
        return switch (resultType) {
            case NMK_CONTINGENCIES -> "nmk-contingencies-result/paged";
            case NMK_LIMIT_VIOLATIONS -> "nmk-constraints-result/paged";
            case N -> "n-result";
        };
    }

    private String getExportPathFromResultType(SecurityAnalysisResultType resultType) {
        return switch (resultType) {
            case NMK_CONTINGENCIES -> "nmk-contingencies-result/csv";
            case NMK_LIMIT_VIOLATIONS -> "nmk-constraints-result/csv";
            case N -> "n-result/csv";
        };
    }

    public UUID runSecurityAnalysis(UUID networkUuid, String variantId, RunSecurityAnalysisParametersInfos parametersInfos, ReportInfos reportInfos, String provider, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportInfos.getReportUuid().toString())
                .queryParam("reporterId", reportInfos.getReporterId())
                .queryParam("reportType", StudyService.ReportType.SECURITY_ANALYSIS.reportKey);
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (parametersInfos.getSecurityAnalysisParametersUuid() != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersInfos.getSecurityAnalysisParametersUuid());
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", parametersInfos.getContingencyListNames())
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        var additionalParameters = new LoadFlowParametersInfos(parametersInfos.getLoadFlowParameters(), parametersInfos.getSpecificParams());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoadFlowParametersInfos> httpEntity = new HttpEntity<>(additionalParameters, headers);

        return restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.SECURITY_ANALYSIS);

        if (resultUuidOpt.isEmpty()) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
    }

    public SecurityAnalysisStatus getSecurityAnalysisStatus(UUID nodeUuid) {
        SecurityAnalysisStatus status;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.SECURITY_ANALYSIS);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        try {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
                    .buildAndExpand(resultUuidOpt.get()).toUriString();

            status = restTemplate.getForObject(securityAnalysisServerBaseUri + path, SecurityAnalysisStatus.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }

        return status;
    }

    public void deleteSaResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    public void deleteSecurityAnalysisResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results").toUriString();
            restTemplate.delete(securityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getSecurityAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, Integer.class);
    }

    public void invalidateSaStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(securityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        SecurityAnalysisStatus sas = getSecurityAnalysisStatus(nodeUuid);
        if (sas == SecurityAnalysisStatus.RUNNING) {
            throw new StudyException(SECURITY_ANALYSIS_RUNNING);
        }
    }

    public void updateSecurityAnalysisParameters(UUID parametersUuid, String parameters) {

        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/{uuid}");
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        try {
            restTemplate.put(securityAnalysisServerBaseUri + path, httpEntity);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, UPDATE_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public UUID duplicateSecurityAnalysisParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/{sourceParametersUuid}")
                .buildAndExpand(sourceParametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        try {
            return restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
    }

    public String getSecurityAnalysisParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);
        String parameters;

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/{uuid}")
                .buildAndExpand(parametersUuid).toUriString();

        try {
            parameters = restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND);
            }
            throw handleHttpError(e, GET_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
        return parameters;
    }

    public UUID getSecurityAnalysisParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getSecurityAnalysisParametersUuid() == null) {
            studyEntity.setSecurityAnalysisParametersUuid(createDefaultSecurityAnalysisParameters());

        }
        return studyEntity.getSecurityAnalysisParametersUuid();
    }

    public void deleteSecurityAnalysisParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/{parametersUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    public UUID createDefaultSecurityAnalysisParameters() {

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/default")
                .buildAndExpand()
                .toUriString();

        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

    public UUID createSecurityAnalysisParameters(String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        UUID parametersUuid;
        try {
            parametersUuid = restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, CREATE_SECURITY_ANALYSIS_PARAMETERS_FAILED);
        }
        return parametersUuid;
    }

}
