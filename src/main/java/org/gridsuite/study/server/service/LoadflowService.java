/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.LoadFlowParametersInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.LoadFlowSpecificParameterInfos;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.ComponentResultEmbeddable;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.LoadFlowResultEntity;
import org.gridsuite.study.server.repository.LoadFlowSpecificParameterEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.powsybl.iidm.network.Country;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.loadflow.LoadFlowResultImpl;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.LOADFLOW_ERROR;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Service
public class LoadflowService {
    private String loadFlowServerBaseUri;

    @Autowired
    NotificationService notificationService;

    private final NetworkService networkStoreService;

    NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    public LoadflowService(
            @Value("${gridsuite.services.loadflow-server.base-uri:http://loadflow-server/}") String loadFlowServerBaseUri,
            NetworkModificationTreeService networkModificationTreeService,
            NetworkService networkStoreService) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public void runLoadFlow(UUID studyUuid, UUID nodeUuid, LoadFlowParametersInfos loadflowParameters, String provider) {
        try {
            LoadFlowResult result;

            UUID networkUuid = networkStoreService.getNetworkUuid(studyUuid);
            String variantId = getVariantId(nodeUuid);
            UUID reportUuid = getReportUuid(nodeUuid);

            var uriComponentsBuilder = UriComponentsBuilder
                    .fromPath(DELIMITER + LOADFLOW_API_VERSION + "/networks/{networkUuid}/run")
                    .queryParam("reportId", reportUuid.toString())
                    .queryParam("reportName", nodeUuid.toString());
            if (!provider.isEmpty()) {
                uriComponentsBuilder.queryParam("provider", provider);
            }
            if (!StringUtils.isBlank(variantId)) {
                uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
            }
            var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<LoadFlowParametersInfos> httpEntity = new HttpEntity<>(loadflowParameters, headers);

            setLoadFlowRunning(studyUuid, nodeUuid);
            ResponseEntity<LoadFlowResult> resp = restTemplate.exchange(loadFlowServerBaseUri + path, HttpMethod.PUT,
                    httpEntity, LoadFlowResult.class);
            result = resp.getBody();
            updateLoadFlowResultAndStatus(nodeUuid, result, computeLoadFlowStatus(result), false);
        } catch (Exception e) {
            updateLoadFlowResultAndStatus(nodeUuid, null, LoadFlowStatus.DIVERGED, false);
            throw new StudyException(LOADFLOW_ERROR, e.getMessage());
        } finally {
            notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW);
        }
    }

    public void setLoadFlowRunning(UUID studyUuid, UUID nodeUuid) {
        updateLoadFlowStatus(nodeUuid, LoadFlowStatus.RUNNING);
        notificationService.emitStudyChanged(studyUuid, nodeUuid, NotificationService.UPDATE_TYPE_LOADFLOW_STATUS);
    }

    void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        networkModificationTreeService.updateLoadFlowStatus(nodeUuid, loadFlowStatus);
    }

    private LoadFlowStatus computeLoadFlowStatus(LoadFlowResult result) {
        return result.getComponentResults().stream()
                .filter(cr -> cr.getConnectedComponentNum() == 0 && cr.getSynchronousComponentNum() == 0
                        && cr.getStatus() == LoadFlowResult.ComponentResult.Status.CONVERGED)
                .collect(Collectors.toList()).isEmpty() ? LoadFlowStatus.DIVERGED : LoadFlowStatus.CONVERGED;
    }

    private void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult,
            LoadFlowStatus loadFlowStatus, boolean updateChildren) {
        networkModificationTreeService.updateLoadFlowResultAndStatus(nodeUuid, loadFlowResult, loadFlowStatus,
                updateChildren);
    }

    private String getVariantId(UUID nodeUuid) {
        return networkModificationTreeService.getVariantId(nodeUuid);
    }

    private UUID getReportUuid(UUID nodeUuid) {
        return networkModificationTreeService.getReportUuid(nodeUuid);
    }

    public static LoadFlowResult.ComponentResult fromEntity(ComponentResultEmbeddable entity) {
        Objects.requireNonNull(entity);
        return new LoadFlowResultImpl.ComponentResultImpl(entity.getConnectedComponentNum(),
                entity.getSynchronousComponentNum(),
                entity.getStatus(),
                entity.getIterationCount(),
                entity.getSlackBusId(),
                entity.getSlackBusActivePowerMismatch(),
                entity.getDistributedActivePower());
    }

    public static LoadFlowResult fromEntity(LoadFlowResultEntity entity) {
        LoadFlowResult result = null;
        if (entity != null) {
            // This is a workaround to prepare the componentResultEmbeddables which will be
            // used later in the webflux pipeline
            // The goal is to avoid LazyInitializationException
            @SuppressWarnings("unused")
            int ignoreSize = entity.getComponentResults().size();
            @SuppressWarnings("unused")
            int ignoreSize2 = entity.getMetrics().size();

            result = new LoadFlowResultImpl(entity.isOk(),
                    entity.getMetrics(),
                    entity.getLogs(),
                    entity.getComponentResults().stream().map(LoadflowService::fromEntity)
                            .collect(Collectors.toList()));
        }
        return result;
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

    public static ComponentResultEmbeddable toEntity(LoadFlowResult.ComponentResult componentResult) {
        Objects.requireNonNull(componentResult);
        return new ComponentResultEmbeddable(componentResult.getConnectedComponentNum(),
                componentResult.getSynchronousComponentNum(),
                componentResult.getStatus(),
                componentResult.getIterationCount(),
                componentResult.getSlackBusId(),
                componentResult.getSlackBusActivePowerMismatch(),
                componentResult.getDistributedActivePower());
    }

    public static LoadFlowResultEntity toEntity(LoadFlowResult result) {
        return result != null
                ? new LoadFlowResultEntity(result.isOk(),
                        result.getMetrics(),
                        result.getLogs(),
                        result.getComponentResults().stream().map(LoadflowService::toEntity)
                                .collect(Collectors.toList()))
                : null;
    }

    public void setLoadFlowServerBaseUri(String loadFlowServerBaseUri) {
        this.loadFlowServerBaseUri = loadFlowServerBaseUri;
    }
}
