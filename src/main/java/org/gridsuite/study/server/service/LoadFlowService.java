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
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.LoadFlowParametersEntity;
import org.gridsuite.study.server.repository.LoadFlowSpecificParameterEntity;
import org.gridsuite.study.server.utils.StudyUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;

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
        this.loadFlowServerBaseUri = StudyUtils.getServiceUri(remoteServicesProperties, "loadflow-server");
        this.networkStoreService = networkStoreService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.restTemplate = restTemplate;
    }

    public UUID runLoadFlow(UUID studyUuid, UUID nodeUuid, LoadFlowParametersInfos loadflowParameters, String provider, String userId) {
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
                .queryParam("reportUuid", reportUuid.toString())
                .queryParam("reporterId", nodeUuid.toString());
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
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

    public String getLoadFlowResultOrStatus(UUID nodeUuid, String suffix) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getLoadFlowResultUuid(nodeUuid);

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

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getLoadFlowResultUuid(nodeUuid);
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
}
