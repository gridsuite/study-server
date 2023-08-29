/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SensitivityAnalysisInputData;
import org.gridsuite.study.server.dto.sensianalysis.*;
import org.gridsuite.study.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.study.server.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.gridsuite.study.server.StudyConstants.*;
import static org.gridsuite.study.server.StudyException.Type.*;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class SensitivityAnalysisService {

    static final String RESULT_UUID = "resultUuid";

    private String sensitivityAnalysisServerBaseUri;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final NetworkModificationTreeService networkModificationTreeService;
    private static final double FLOW_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE = 0.0;
    private static final double FLOW_VOLTAGE_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE = 0.0;
    private static final double ANGLE_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE = 0.0;

    @Autowired
    SensitivityAnalysisService(RemoteServicesProperties remoteServicesProperties,
                               NetworkModificationTreeService networkModificationTreeService,
                               ObjectMapper objectMapper) {
        this.sensitivityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("sensitivity-analysis-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public void setSensitivityAnalysisServerBaseUri(String sensitivityAnalysisServerBaseUri) {
        this.sensitivityAnalysisServerBaseUri = sensitivityAnalysisServerBaseUri + DELIMITER;
    }

    public UUID runSensitivityAnalysis(UUID nodeUuid, UUID networkUuid,
                                       String variantId,
                                       UUID reportUuid,
                                       String provider,
                                       String userId,
                                       SensitivityAnalysisInputData sensitivityAnalysisParameters) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
            .queryParam("reportUuid", reportUuid.toString())
            .queryParam("reporterId", nodeUuid.toString());
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<SensitivityAnalysisInputData> httpEntity = new HttpEntity<>(sensitivityAnalysisParameters, headers);

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getSensitivityAnalysisResult(UUID nodeUuid, String selector) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
            .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, "results", resultUuidOpt.get().toString())
            .queryParam("selector", selector).build().encode().toUri();
        try {
            result = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SENSITIVITY_ANALYSIS_ERROR);
            }
        }
        return result;
    }

    public String getSensitivityAnalysisStatus(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/status")
            .buildAndExpand(resultUuidOpt.get()).toUriString();
        try {
            result = restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public void stopSensitivityAnalysis(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
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
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
    }

    public void invalidateSensitivityAnalysisStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void deleteSensitivityAnalysisResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
    }

    public void assertSensitivityAnalysisNotRunning(UUID nodeUuid) {
        String sas = getSensitivityAnalysisStatus(nodeUuid);
        if (SensitivityAnalysisStatus.RUNNING.name().equals(sas)) {
            throw new StudyException(SENSITIVITY_ANALYSIS_RUNNING);
        }
    }

    public static SensitivityAnalysisParametersEntity toEntity(SensibilityAnalysisParametersInfos parameters) {
        Objects.requireNonNull(parameters);
        List<SensitivityAnalysisParametersInjectionsSetEntity> sensitivityInjectionsSet = new ArrayList<>();
        if (parameters.getSensitivityInjectionsSet() != null) {
            parameters.getSensitivityInjectionsSet().forEach(sensitivityInjectionSet ->
                    sensitivityInjectionsSet.add(new SensitivityAnalysisParametersInjectionsSetEntity(null,
                            (SensitivityAnalysisInputData.DistributionType) sensitivityInjectionSet.getDistributionType(),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjectionSet.getMonitoredBranches()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjectionSet.getInjections()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjectionSet.getContingencies())))
            );
        }
        List<SensitivityAnalysisParametersInjectionsEntity> sensitivityInjections = new ArrayList<>();
        if (parameters.getSensitivityInjection() != null) {
            parameters.getSensitivityInjection().forEach(sensitivityInjection ->
                    sensitivityInjections.add(new SensitivityAnalysisParametersInjectionsEntity(null,
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjection.getMonitoredBranches()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjection.getInjections()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityInjection.getContingencies())))
            );
        }
        List<SensitivityAnalysisParametersHvdcEntity> sensitivityHvdcs = new ArrayList<>();
        if (parameters.getSensitivityHVDC() != null) {
            parameters.getSensitivityHVDC().forEach(sensitivityHvdc ->
                    sensitivityHvdcs.add(new SensitivityAnalysisParametersHvdcEntity(null,
                            sensitivityHvdc.getSensitivityType(),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityHvdc.getMonitoredBranches()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityHvdc.getHvdcs()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityHvdc.getContingencies())))
            );
        }
        List<SensitivityAnalysisParametersPstEntity> sensitivityPsts = new ArrayList<>();
        if (parameters.getSensitivityPST() != null) {
            parameters.getSensitivityPST().forEach(sensitivityPst ->
                    sensitivityPsts.add(new SensitivityAnalysisParametersPstEntity(null,
                            sensitivityPst.getSensitivityType(),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityPst.getMonitoredBranches()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityPst.getPsts()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityPst.getContingencies())))
            );
        }
        List<SensitivityAnalysisParametersNodesEntity> sensitivityNodes = new ArrayList<>();
        if (parameters.getSensitivityNodes() != null) {
            parameters.getSensitivityNodes().forEach(sensitivityNode ->
                    sensitivityNodes.add(new SensitivityAnalysisParametersNodesEntity(null,
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityNode.getMonitoredVoltageLevels()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityNode.getEquipmentsInVoltageRegulation()),
                            FilterEquipmentsEmbeddable.toEmbeddableFilterEquipments(sensitivityNode.getContingencies())))
            );
        }

        return new SensitivityAnalysisParametersEntity(null, parameters.getFlowFlowSensitivityValueThreshold(),
                parameters.getAngleFlowSensitivityValueThreshold(),
                parameters.getFlowVoltageSensitivityValueThreshold(),
                sensitivityInjectionsSet,
                sensitivityInjections,
                sensitivityHvdcs,
                sensitivityPsts,
                sensitivityNodes
                );
    }

    public static SensibilityAnalysisParametersInfos fromEntity(SensitivityAnalysisParametersEntity entity) {
        Objects.requireNonNull(entity);

        List<SensibilityAnalysisInjectionsSetParameterInfos> sensitivityInjectionsSet = new ArrayList<>();
        entity.getSensitivityInjectionsSet().forEach(sensitivityInjectionSet ->
                sensitivityInjectionsSet.add(new SensibilityAnalysisInjectionsSetParameterInfos(
                        (SensitivityAnalysisInputData.DistributionType) sensitivityInjectionSet.getDistributionType(),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjectionSet.getMonitoredBranches()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjectionSet.getInjections()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjectionSet.getContingencies()))));
        List<SensibilityAnalysisInjectionsParameterInfos> sensitivityInjections = new ArrayList<>();
        entity.getSensitivityInjections().forEach(sensitivityInjection ->
                sensitivityInjections.add(new SensibilityAnalysisInjectionsParameterInfos(
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjection.getMonitoredBranches()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjection.getInjections()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityInjection.getContingencies()))));

        List<SensibilityAnalysisHvdcParameterInfos> sensitivityHvdcs = new ArrayList<>();
        entity.getSensitivityHVDC().forEach(sensitivityHvdc ->
                sensitivityHvdcs.add(new SensibilityAnalysisHvdcParameterInfos(
                        (SensitivityAnalysisInputData.SensitivityType) sensitivityHvdc.getSensitivityType(),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityHvdc.getMonitoredBranches()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityHvdc.getHvdcs()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityHvdc.getContingencies()))));
        List<SensibilityAnalysisPtsParameterInfos> sensitivityPsts = new ArrayList<>();
        entity.getSensitivityPST().forEach(sensitivityPst ->
                sensitivityPsts.add(new SensibilityAnalysisPtsParameterInfos(
                        (SensitivityAnalysisInputData.SensitivityType) sensitivityPst.getSensitivityType(),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityPst.getMonitoredBranches()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityPst.getPsts()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityPst.getContingencies()))));
        List<SensibilityAnalysisNodesParameterInfos> sensitivityNodes = new ArrayList<>();
        entity.getSensitivityNodes().forEach(sensitivityNode ->
                sensitivityNodes.add(new SensibilityAnalysisNodesParameterInfos(
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityNode.getMonitoredVoltageLevels()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityNode.getEquipmentsInVoltageRegulation()),
                        FilterEquipmentsEmbeddable.fromEmbeddableFilterEquipments(sensitivityNode.getContingencies()))));

        return new SensibilityAnalysisParametersInfos(entity.getFlowFlowSensitivityValueThreshold(), entity.getAngleFlowSensitivityValueThreshold(),
                entity.getFlowVoltageSensitivityValueThreshold(), sensitivityInjectionsSet, sensitivityInjections, sensitivityHvdcs,
                sensitivityPsts, sensitivityNodes);
    }

    public static SensibilityAnalysisParametersInfos getDefaultSensitivityAnalysisParametersValues() {
        return SensibilityAnalysisParametersInfos.builder()
                .flowFlowSensitivityValueThreshold(FLOW_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .angleFlowSensitivityValueThreshold(ANGLE_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .flowVoltageSensitivityValueThreshold(FLOW_VOLTAGE_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .build();
    }
}
