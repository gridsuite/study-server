/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.LoadFlowSpecificParameterEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class LoadFlowService {

    static final String RESULT_UUID = "resultUuid";
    static final String RESULTS_UUIDS = "resultsUuids";
    private String loadFlowServerBaseUri;

    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate;

    @Autowired
    public LoadFlowService(RemoteServicesProperties remoteServicesProperties,
                           NetworkModificationTreeService networkModificationTreeService,
                           NetworkService networkStoreService, ObjectMapper objectMapper,
                           NotificationService notificationService,
                           RestTemplate restTemplate) {
        this.loadFlowServerBaseUri = remoteServicesProperties.getServiceUri("loadflow-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.restTemplate = restTemplate;
    }

    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, LoadFlowParametersInfos loadflowParameters, String provider, String userId, Float limitReduction) {
        UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
        String variantId = getVariantId(nodeUuid);
        UUID reportUuid = getReportUuid(nodeUuid);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run-and-save")
                .queryParam(QUERY_PARAM_RECEIVER, receiver)
                .queryParam(QUERY_PARAM_REPORT_UUID, reportUuid.toString())
                .queryParam(QUERY_PARAM_REPORTER_ID, nodeUuid.toString())
                .queryParam(QUERY_PARAM_REPORT_TYPE, StudyService.ReportType.LOADFLOW.reportKey);
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        if (limitReduction != null) {
            uriComponentsBuilder.queryParam("limitReduction", limitReduction);
        }
        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LoadFlowParametersInfos> httpEntity = new HttpEntity<>(loadflowParameters, headers);

        return restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public void deleteLoadFlowResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}")
                .buildAndExpand(uuid)
                .toUriString();

        restTemplate.delete(loadFlowServerBaseUri + path);
    }

    public void deleteLoadFlowResults(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results")
                    .queryParam(RESULTS_UUIDS, uuids).build().toUriString();
            restTemplate.delete(loadFlowServerBaseUri + path, Void.class);
        }
    }

    public void deleteLoadFlowResults() {
        try {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results").toUriString();
            restTemplate.delete(loadFlowServerBaseUri + path, Void.class);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }

    }

    public Integer getLoadFlowResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(loadFlowServerBaseUri + path, Integer.class);
    }

    public String getLoadFlowResultOrStatus(UUID nodeUuid, String suffix) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}" + suffix)
                .buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(loadFlowServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(LOADFLOW_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public String getLoadFlowResult(UUID nodeUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, "");
    }

    public String getLoadFlowStatus(UUID nodeUuid) {
        return getLoadFlowResultOrStatus(nodeUuid, "/status");
    }

    public void stopLoadFlow(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);
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
                .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/stop")
                .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(loadFlowServerBaseUri + path, Void.class);
    }

    public void invalidateLoadFlowStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/invalidate-status")
                    .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(loadFlowServerBaseUri + path, Void.class);
        }
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public static LoadFlowParametersEntity toEntity(LoadFlowParameters parameters, List<LoadFlowSpecificParameterInfos> allLoadFlowSpecificParameters) {
        Objects.requireNonNull(parameters);
        return new LoadFlowParametersEntity(parameters.getVoltageInitMode(),
                parameters.isTransformerVoltageControlOn(),
                parameters.isUseReactiveLimits(),
                parameters.isPhaseShifterRegulationOn(),
                parameters.isTwtSplitShuntAdmittance(),
                parameters.isShuntCompensatorVoltageControlOn(),
                parameters.isReadSlackBus(),
                parameters.isWriteSlackBus(),
                parameters.isDc(),
                parameters.isDistributedSlack(),
                parameters.getBalanceType(),
                parameters.isDcUseTransformerRatio(),
                parameters.getCountriesToBalance().stream().map(Country::toString).collect(Collectors.toSet()),
                parameters.getConnectedComponentMode(),
                parameters.isHvdcAcEmulation(),
                parameters.getDcPowerFactor(),
                LoadFlowSpecificParameterEntity.toLoadFlowSpecificParameters(allLoadFlowSpecificParameters));
    }

    public static LoadFlowParameters fromEntity(LoadFlowParametersEntity entity) {
        Objects.requireNonNull(entity);
        return LoadFlowParameters.load()
                .setVoltageInitMode(entity.getVoltageInitMode())
                .setTransformerVoltageControlOn(entity.isTransformerVoltageControlOn())
                .setUseReactiveLimits(entity.isUseReactiveLimits())
                .setPhaseShifterRegulationOn(entity.isPhaseShifterRegulationOn())
                .setTwtSplitShuntAdmittance(entity.isTwtSplitShuntAdmittance())
                .setShuntCompensatorVoltageControlOn(entity.isShuntCompensatorVoltageControlOn())
                .setReadSlackBus(entity.isReadSlackBus())
                .setWriteSlackBus(entity.isWriteSlackBus())
                .setDc(entity.isDc())
                .setDistributedSlack(entity.isDistributedSlack())
                .setBalanceType(entity.getBalanceType())
                .setDcUseTransformerRatio(entity.isDcUseTransformerRatio())
                .setCountriesToBalance(entity.getCountriesToBalance().stream().map(Country::valueOf).collect(Collectors.toSet()))
                .setConnectedComponentMode(entity.getConnectedComponentMode())
                .setHvdcAcEmulation(entity.isHvdcAcEmulation())
                .setDcPowerFactor(entity.getDcPowerFactor());
    }

    public void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }

    public void assertLoadFlowNotRunning(UUID nodeUuid) {
        String scs = getLoadFlowStatus(nodeUuid);
        if (LoadFlowStatus.RUNNING.name().equals(scs)) {
            throw new StudyException(LOADFLOW_RUNNING);
        }
    }

    public List<LimitViolationInfos> getLimitViolations(UUID nodeUuid, String filters, Sort sort) {
        List<LimitViolationInfos> result = new ArrayList<>();
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getComputationResultUuid(nodeUuid, ComputationType.LOAD_FLOW);

        if (resultUuidOpt.isPresent()) {
            UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromPath(DELIMITER + LOADFLOW_API_VERSION + "/results/{resultUuid}/limit-violations");
            if (filters != null && !filters.isEmpty()) {
                uriComponentsBuilder.queryParam("filters", URLEncoder.encode(filters, StandardCharsets.UTF_8));
            }
            for (Sort.Order order : sort) {
                uriComponentsBuilder.queryParam("sort", order.getProperty() + "," + order.getDirection());
            }
           String path = uriComponentsBuilder.buildAndExpand(resultUuidOpt.get()).toUriString();
            try {
                var responseEntity = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.GET, null, new ParameterizedTypeReference<List<LimitViolationInfos>>() {
                });
                result = responseEntity.getBody();
            } catch (HttpStatusCodeException e) {
                if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                    throw new StudyException(LOADFLOW_NOT_FOUND);
                }
                throw e;
            }
        }
        return result;
    }
}
