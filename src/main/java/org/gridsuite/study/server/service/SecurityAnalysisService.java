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
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.ReportInfos;
import org.gridsuite.study.server.dto.RunSecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.SecurityAnalysisStatus;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.service.common.AbstractComputationService;
import org.gridsuite.study.server.service.securityanalysis.SecurityAnalysisResultType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
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

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.*;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class SecurityAnalysisService extends AbstractComputationService {

    static final String RESULT_UUID = "resultUuid";

    private static final String PARAMETERS_URI = "/parameters/{parametersUuid}";

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Setter
    private String securityAnalysisServerBaseUri;

    @Autowired
    public SecurityAnalysisService(RemoteServicesProperties remoteServicesProperties,
                                   ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.securityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("security-analysis-server");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public String getSecurityAnalysisResult(UUID resultUuid, UUID networkUuid, String variantId, SecurityAnalysisResultType resultType, String filters, String globalFilters, Pageable pageable) {
        if (resultUuid == null) {
            return null;
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/" + getPagedPathFromResultType(resultType))
            .queryParam(QUERY_PARAM_PAGE, pageable.getPageNumber())
            .queryParam(QUERY_PARAM_SIZE, pageable.getPageSize());

        buildPathWithQueryParamUnpaged(pathBuilder, networkUuid, variantId, filters, globalFilters, pageable.getSort());
        String path = pathBuilder.buildAndExpand(resultUuid).toUriString();

        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
    }

    public byte[] getSecurityAnalysisResultCsv(UUID resultUuid, UUID networkUuid, String variantId, SecurityAnalysisResultType resultType, String globalFilters, String filters, Sort sort, String csvTranslations) {
        if (resultUuid == null) {
            throw new StudyException(NOT_FOUND, "Result for security analysis not found");
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/" + getExportPathFromResultType(resultType));

        buildPathWithQueryParamUnpaged(pathBuilder, networkUuid, variantId, filters, globalFilters, sort);
        String path = pathBuilder.buildAndExpand(resultUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(csvTranslations, headers);
        return restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, entity, byte[].class).getBody();
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

    private void buildPathWithQueryParamUnpaged(UriComponentsBuilder pathBuilder, UUID networkUuid, String variantId, String filters, String globalFilters, Sort sort) {
        if (filters != null && !filters.isEmpty()) {
            pathBuilder.queryParam(QUERY_PARAM_FILTERS, URLEncoder.encode(filters, StandardCharsets.UTF_8));
        }
        if (!StringUtils.isEmpty(globalFilters)) {
            pathBuilder.queryParam(QUERY_PARAM_GLOBAL_FILTERS, URLEncoder.encode(globalFilters, StandardCharsets.UTF_8));
            pathBuilder.queryParam(QUERY_PARAM_NETWORK_UUID, networkUuid);
            if (!StringUtils.isBlank(variantId)) {
                pathBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
        }
        for (Sort.Order order : sort) {
            pathBuilder.queryParam(QUERY_PARAM_SORT, order.getProperty() + "," + order.getDirection());
        }
    }

    public UUID runSecurityAnalysis(UUID networkUuid, String variantId, RunSecurityAnalysisParametersInfos parametersInfos, ReportInfos reportInfos, String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportInfos.reportUuid().toString())
                .queryParam("reporterId", reportInfos.nodeUuid())
                .queryParam("reportType", StudyService.ReportType.SECURITY_ANALYSIS.reportKey);
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (parametersInfos.getSecurityAnalysisParametersUuid() != null) {
            uriComponentsBuilder.queryParam("parametersUuid", parametersInfos.getSecurityAnalysisParametersUuid());
        }
        if (parametersInfos.getLoadFlowParametersUuid() != null) {
            uriComponentsBuilder.queryParam("loadFlowParametersUuid", parametersInfos.getLoadFlowParametersUuid());
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", parametersInfos.getContingencyListNames())
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        Objects.requireNonNull(userId);

        if (resultUuid == null) {
            return;
        }

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)),
                    StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public SecurityAnalysisStatus getSecurityAnalysisStatus(UUID resultUuid) {

        if (resultUuid == null) {
            return null;
        }

        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();

        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, SecurityAnalysisStatus.class);
    }

    public void deleteSecurityAnalysisResults(List<UUID> resultsUuids) {
        deleteCalculationResults(resultsUuids, DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results", restTemplate, securityAnalysisServerBaseUri);
    }

    public void deleteAllSecurityAnalysisResults() {
        deleteSecurityAnalysisResults(null);
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

    public void assertSecurityAnalysisNotRunning(UUID resultUuid) {
        SecurityAnalysisStatus sas = getSecurityAnalysisStatus(resultUuid);
        if (sas == SecurityAnalysisStatus.RUNNING) {
            throw new StudyException(COMPUTATION_RUNNING);
        }
    }

    public void updateSecurityAnalysisParameters(UUID parametersUuid, @Nullable String parameters) {
        var uriBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/{uuid}");
        String path = uriBuilder.buildAndExpand(parametersUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        restTemplate.put(securityAnalysisServerBaseUri + path, httpEntity);
    }

    public UUID duplicateSecurityAnalysisParameters(UUID sourceParametersUuid) {
        Objects.requireNonNull(sourceParametersUuid);

        var path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + DELIMITER + PATH_PARAM_PARAMETERS)
                .queryParam("duplicateFrom", sourceParametersUuid)
                .buildAndExpand().toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

        return restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getSecurityAnalysisParameters(UUID parametersUuid) {
        Objects.requireNonNull(parametersUuid);

        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(parametersUuid).toUriString();

        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
    }

    public UUID getSecurityAnalysisParametersUuidOrElseCreateDefaults(StudyEntity studyEntity) {
        if (studyEntity.getSecurityAnalysisParametersUuid() == null) {
            studyEntity.setSecurityAnalysisParametersUuid(createDefaultSecurityAnalysisParameters());

        }
        return studyEntity.getSecurityAnalysisParametersUuid();
    }

    public void deleteSecurityAnalysisParameters(UUID uuid) {
        Objects.requireNonNull(uuid);
        String path = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + PARAMETERS_URI)
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(securityAnalysisServerBaseUri + path);
    }

    public UUID createDefaultSecurityAnalysisParameters() {

        var path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters/default")
                .buildAndExpand()
                .toUriString();

        return restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, null, UUID.class).getBody();
    }

    public UUID createSecurityAnalysisParameters(String parameters) {
        var path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/parameters")
                .buildAndExpand()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> httpEntity = new HttpEntity<>(parameters, headers);

        return restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void updateSecurityAnalysisProvider(UUID parameterUuid, String provider) {
        Objects.requireNonNull(provider);

        var path = UriComponentsBuilder
            .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + PARAMETERS_URI + "/provider")
            .buildAndExpand(parameterUuid)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<String> httpEntity = new HttpEntity<>(provider, headers);

        restTemplate.exchange(securityAnalysisServerBaseUri + path, HttpMethod.PUT, httpEntity, Void.class);
    }

    public String getSecurityAnalysisDefaultProvider() {
        String path = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/default-provider")
                .buildAndExpand()
                .toUriString();

        return restTemplate.getForObject(securityAnalysisServerBaseUri + path, String.class);
    }

    @Override
    public List<String> getEnumValues(String enumName, UUID resultUuid) {
        return getEnumValues(enumName, resultUuid, SECURITY_ANALYSIS_API_VERSION, securityAnalysisServerBaseUri, restTemplate);
    }
}
