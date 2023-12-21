/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.security.SecurityAnalysisParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SecurityAnalysisParametersInfos;
import org.gridsuite.study.server.dto.SecurityAnalysisParametersValues;
import org.gridsuite.study.server.dto.SecurityAnalysisStatus;
import org.gridsuite.study.server.repository.SecurityAnalysisParametersEntity;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

@Service
public class SecurityAnalysisService {

    static final String RESULT_UUID = "resultUuid";

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private String securityAnalysisServerBaseUri;

    private final NetworkModificationTreeService networkModificationTreeService;

    private static final double DEFAULT_FLOW_PROPORTIONAL_THRESHOLD = 0.1; // meaning 10.0 %
    private static final double DEFAULT_LOW_VOLTAGE_PROPORTIONAL_THRESHOLD = 0.01; // meaning 1.0 %
    private static final double DEFAULT_HIGH_VOLTAGE_PROPORTIONAL_THRESHOLD = 0.01; // meaning 1.0 %
    private static final double DEFAULT_LOW_VOLTAGE_ABSOLUTE_THRESHOLD = 1.0; // 1.0 kV
    private static final double DEFAULT_HIGH_VOLTAGE_ABSOLUTE_THRESHOLD = 1.0; // 1.0 kV

    @Autowired
    public SecurityAnalysisService(RemoteServicesProperties remoteServicesProperties,
                                   NetworkModificationTreeService networkModificationTreeService,
                                   ObjectMapper objectMapper) {
        this.securityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("security-analysis-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public String getSecurityAnalysisResult(UUID nodeUuid, SecurityAnalysisResultType resultType, String filters, Pageable pageable) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        UriComponentsBuilder pathBuilder = UriComponentsBuilder.fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/" + getPathFromResultType(resultType))
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

    private String getPathFromResultType(SecurityAnalysisResultType resultType) {
        return switch (resultType) {
            case NMK_CONTINGENCIES -> "nmk-contingencies-result/paged";
            case NMK_LIMIT_VIOLATIONS -> "nmk-constraints-result/paged";
            case N -> "n-result";
        };
    }

    public UUID runSecurityAnalysis(UUID networkUuid, UUID reportUuid, UUID nodeUuid, String variantId, String provider, List<String> contingencyListNames, SecurityAnalysisParametersInfos securityAnalysisParameters,
            String receiver, String userId) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SECURITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString())
                .queryParam("reportType", StudyService.ReportType.SECURITY_ANALYSIS.reportKey);
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder.queryParam("contingencyListName", contingencyListNames)
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SecurityAnalysisParametersInfos> httpEntity = new HttpEntity<>(securityAnalysisParameters, headers);

        return restTemplate
                .exchange(securityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void stopSecurityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

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
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSecurityAnalysisResultUuid(nodeUuid);

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

    public void setSecurityAnalysisServerBaseUri(String securityAnalysisServerBaseUri) {
        this.securityAnalysisServerBaseUri = securityAnalysisServerBaseUri;
    }

    public void assertSecurityAnalysisNotRunning(UUID nodeUuid) {
        SecurityAnalysisStatus sas = getSecurityAnalysisStatus(nodeUuid);
        if (sas == SecurityAnalysisStatus.RUNNING) {
            throw new StudyException(SECURITY_ANALYSIS_RUNNING);
        }
    }

    public static SecurityAnalysisParametersEntity toEntity(SecurityAnalysisParametersValues parameters) {
        Objects.requireNonNull(parameters);
        return new SecurityAnalysisParametersEntity(parameters.getLowVoltageAbsoluteThreshold(), parameters.getLowVoltageProportionalThreshold(), parameters.getHighVoltageAbsoluteThreshold(), parameters.getHighVoltageProportionalThreshold(), parameters.getFlowProportionalThreshold());
    }

    public static SecurityAnalysisParametersValues fromEntity(SecurityAnalysisParametersEntity entity) {
        Objects.requireNonNull(entity);
        return SecurityAnalysisParametersValues.builder()
                .lowVoltageAbsoluteThreshold(entity.getLowVoltageAbsoluteThreshold())
                .lowVoltageProportionalThreshold(entity.getLowVoltageProportionalThreshold())
                .highVoltageAbsoluteThreshold(entity.getHighVoltageAbsoluteThreshold())
                .highVoltageProportionalThreshold(entity.getHighVoltageProportionalThreshold())
                .flowProportionalThreshold(entity.getFlowProportionalThreshold())
                .build();
    }

    public static SecurityAnalysisParametersValues getDefaultSecurityAnalysisParametersValues() {
        return SecurityAnalysisParametersValues.builder()
                .lowVoltageAbsoluteThreshold(DEFAULT_LOW_VOLTAGE_ABSOLUTE_THRESHOLD)
                .lowVoltageProportionalThreshold(DEFAULT_LOW_VOLTAGE_PROPORTIONAL_THRESHOLD)
                .highVoltageAbsoluteThreshold(DEFAULT_HIGH_VOLTAGE_ABSOLUTE_THRESHOLD)
                .highVoltageProportionalThreshold(DEFAULT_HIGH_VOLTAGE_PROPORTIONAL_THRESHOLD)
                .flowProportionalThreshold(DEFAULT_FLOW_PROPORTIONAL_THRESHOLD)
                .build();
    }

    public static SecurityAnalysisParameters toSecurityAnalysisParameters(SecurityAnalysisParametersEntity entity) {
        if (entity == null) {
            return SecurityAnalysisParameters.load()
                    // the default values are overloaded
                    .setIncreasedViolationsParameters(getIncreasedViolationsParameters(DEFAULT_FLOW_PROPORTIONAL_THRESHOLD, DEFAULT_LOW_VOLTAGE_PROPORTIONAL_THRESHOLD, DEFAULT_LOW_VOLTAGE_ABSOLUTE_THRESHOLD, DEFAULT_HIGH_VOLTAGE_PROPORTIONAL_THRESHOLD, DEFAULT_HIGH_VOLTAGE_ABSOLUTE_THRESHOLD));
        }
        SecurityAnalysisParameters securityAnalysisParameters = new SecurityAnalysisParameters();
        securityAnalysisParameters.setIncreasedViolationsParameters(getIncreasedViolationsParameters(entity.getFlowProportionalThreshold(), entity.getLowVoltageProportionalThreshold(), entity.getLowVoltageAbsoluteThreshold(), entity.getHighVoltageProportionalThreshold(), entity.getHighVoltageAbsoluteThreshold()));
        return securityAnalysisParameters;
    }

    public static SecurityAnalysisParameters.IncreasedViolationsParameters getIncreasedViolationsParameters(double flowProportionalThreshold, double lowVoltageProportionalThreshold, double lowVoltageAbsoluteThreshold, double highVoltageProportionalThreshold, double highVoltageAbsoluteThreshold) {
        SecurityAnalysisParameters.IncreasedViolationsParameters increasedViolationsParameters = new SecurityAnalysisParameters.IncreasedViolationsParameters();
        increasedViolationsParameters.setFlowProportionalThreshold(flowProportionalThreshold);
        increasedViolationsParameters.setLowVoltageAbsoluteThreshold(lowVoltageAbsoluteThreshold);
        increasedViolationsParameters.setLowVoltageProportionalThreshold(lowVoltageProportionalThreshold);
        increasedViolationsParameters.setHighVoltageAbsoluteThreshold(highVoltageAbsoluteThreshold);
        increasedViolationsParameters.setHighVoltageProportionalThreshold(highVoltageProportionalThreshold);
        return increasedViolationsParameters;
    }
}
