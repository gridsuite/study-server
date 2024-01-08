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
import org.gridsuite.study.server.RemoteServicesProperties;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.NodeReceiver;
import org.gridsuite.study.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisCsvFileInfos;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityFactorsIdsByGroup;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisInputData;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisParametersInfos;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;
import org.gridsuite.study.server.repository.sensianalysis.*;
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
import static org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisInputData.*;
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
                                       SensitivityAnalysisInputData sensitivityAnalysisParameters,
                                       String userId) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/run-and-save")
            .queryParam("reportUuid", reportUuid.toString())
            .queryParam("reporterId", nodeUuid.toString())
            .queryParam("reportType", StudyService.ReportType.SENSITIVITY_ANALYSIS.reportKey);
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

    public byte[] exportSensitivityResultsAsCsv(UUID nodeUuid, SensitivityAnalysisCsvFileInfos sensitivityAnalysisCsvFileInfos) {
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, "results", resultUuidOpt.get().toString(), "csv-bytes")
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "sensitivity_results.csv");

        HttpEntity<SensitivityAnalysisCsvFileInfos> httpEntity = new HttpEntity<>(sensitivityAnalysisCsvFileInfos, headers);
        return restTemplate.exchange(uri, HttpMethod.POST, httpEntity, byte[].class).getBody();

    }

    public String getSensitivityResultsFilterOptions(UUID nodeUuid, String selector) {
        String options;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getSensitivityAnalysisResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
                .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, "results", resultUuidOpt.get().toString(), "filter-options")
                .queryParam("selector", selector).build().encode().toUri();
        try {
            options = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(SENSITIVITY_ANALYSIS_NOT_FOUND);
            } else {
                throw handleHttpError(e, SENSITIVITY_ANALYSIS_ERROR);
            }
        }
        return options;
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

    public void deleteSensitivityAnalysisResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/results")
                .toUriString();
            restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getSensitivityAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/supervision/results-count").toUriString();
        return restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, Integer.class);
    }

    public void assertSensitivityAnalysisNotRunning(UUID nodeUuid) {
        String sas = getSensitivityAnalysisStatus(nodeUuid);
        if (SensitivityAnalysisStatus.RUNNING.name().equals(sas)) {
            throw new StudyException(SENSITIVITY_ANALYSIS_RUNNING);
        }
    }

    public static SensitivityAnalysisParametersEntity toEntity(SensitivityAnalysisParametersInfos parameters) {
        Objects.requireNonNull(parameters);
        List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSet = new ArrayList<>();

        if (parameters.getSensitivityInjectionsSet() != null) {
            for (SensitivityInjectionsSet sensitivityInjectionSet : parameters.getSensitivityInjectionsSet()) {
                SensitivityFactorWithDistribTypeEntity entity = new SensitivityFactorWithDistribTypeEntity();
                entity.setDistributionType(sensitivityInjectionSet.getDistributionType());
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionSet.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionSet.getInjections()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjectionSet.getContingencies()));
                entity.setActivated(sensitivityInjectionSet.isActivated());
                sensitivityInjectionsSet.add(entity);
            }
        }
        List<SensitivityFactorForInjectionEntity> sensitivityInjections = new ArrayList<>();

        if (parameters.getSensitivityInjection() != null) {
            for (SensitivityAnalysisInputData.SensitivityInjection sensitivityInjection : parameters.getSensitivityInjection()) {
                SensitivityFactorForInjectionEntity entity = new SensitivityFactorForInjectionEntity();
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getInjections()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityInjection.getContingencies()));
                entity.setActivated(sensitivityInjection.isActivated());
                sensitivityInjections.add(entity);
            }
        }
        List<SensitivityFactorWithSensiTypeForHvdcEntity> sensitivityHvdcs = new ArrayList<>();

        if (parameters.getSensitivityHVDC() != null) {
            for (SensitivityAnalysisInputData.SensitivityHVDC sensitivityHvdc : parameters.getSensitivityHVDC()) {
                SensitivityFactorWithSensiTypeForHvdcEntity entity = new SensitivityFactorWithSensiTypeForHvdcEntity();
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getHvdcs()));
                entity.setSensitivityType(sensitivityHvdc.getSensitivityType());
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityHvdc.getContingencies()));
                entity.setActivated(sensitivityHvdc.isActivated());
                sensitivityHvdcs.add(entity);
            }
        }
        List<SensitivityFactorWithSensiTypeForPstEntity> sensitivityPsts = new ArrayList<>();

        if (parameters.getSensitivityPST() != null) {
            for (SensitivityAnalysisInputData.SensitivityPST sensitivityPst : parameters.getSensitivityPST()) {
                SensitivityFactorWithSensiTypeForPstEntity entity = new SensitivityFactorWithSensiTypeForPstEntity();
                entity.setSensitivityType(sensitivityPst.getSensitivityType());
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getMonitoredBranches()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getPsts()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityPst.getContingencies()));
                entity.setActivated(sensitivityPst.isActivated());
                sensitivityPsts.add(entity);
            }
        }

        List<SensitivityFactorForNodeEntity> sensitivityNodes = new ArrayList<>();

        if (parameters.getSensitivityNodes() != null) {
            for (SensitivityAnalysisInputData.SensitivityNodes sensitivityNode : parameters.getSensitivityNodes()) {
                SensitivityFactorForNodeEntity entity = new SensitivityFactorForNodeEntity();
                entity.setMonitoredBranch(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getMonitoredVoltageLevels()));
                entity.setInjections(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getEquipmentsInVoltageRegulation()));
                entity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(sensitivityNode.getContingencies()));
                entity.setActivated(sensitivityNode.isActivated());
                sensitivityNodes.add(entity);
            }
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

    public static SensitivityAnalysisParametersInfos fromEntity(SensitivityAnalysisParametersEntity entity) {
        Objects.requireNonNull(entity);

        List<SensitivityInjectionsSet> sensitivityInjectionsSet = new ArrayList<>();
        entity.getSensitivityInjectionsSet().stream().map(sensitivityInjectionSet -> new SensitivityInjectionsSet(
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionSet.getMonitoredBranch()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionSet.getInjections()),
                sensitivityInjectionSet.getDistributionType(),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjectionSet.getContingencies()),
                sensitivityInjectionSet.isActivated()
        )).forEach(sensitivityInjectionsSet::add);

        List<SensitivityAnalysisInputData.SensitivityInjection> sensitivityInjections = new ArrayList<>();
        entity.getSensitivityInjections().stream().map(sensitivityInjection -> new SensitivityInjection(
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getMonitoredBranch()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getInjections()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityInjection.getContingencies()),
                sensitivityInjection.isActivated()
        )).forEach(sensitivityInjections::add);

        List<SensitivityAnalysisInputData.SensitivityHVDC> sensitivityHvdcs = new ArrayList<>();
        entity.getSensitivityHvdc().stream().map(sensitivityHvdc -> new SensitivityHVDC(
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getMonitoredBranch()),
                sensitivityHvdc.getSensitivityType(),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getInjections()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityHvdc.getContingencies()),
                sensitivityHvdc.isActivated()
        )).forEach(sensitivityHvdcs::add);

        List<SensitivityAnalysisInputData.SensitivityPST> sensitivityPsts = new ArrayList<>();
        entity.getSensitivityPST().stream().map(sensitivityPst -> new SensitivityPST(
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getMonitoredBranch()),
                sensitivityPst.getSensitivityType(),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getInjections()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityPst.getContingencies()),
                sensitivityPst.isActivated()
        )).forEach(sensitivityPsts::add);

        List<SensitivityAnalysisInputData.SensitivityNodes> sensitivityNodes = new ArrayList<>();
        entity.getSensitivityNodes().stream().map(sensitivityNode -> new SensitivityNodes(
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getMonitoredBranch()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getInjections()),
                EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(sensitivityNode.getContingencies()),
                sensitivityNode.isActivated()
        )).forEach(sensitivityNodes::add);

        return new SensitivityAnalysisParametersInfos(entity.getFlowFlowSensitivityValueThreshold(), entity.getAngleFlowSensitivityValueThreshold(),
                entity.getFlowVoltageSensitivityValueThreshold(), sensitivityInjectionsSet, sensitivityInjections, sensitivityHvdcs,
                sensitivityPsts, sensitivityNodes);
    }

    public static SensitivityAnalysisParametersInfos getDefaultSensitivityAnalysisParametersValues() {
        return SensitivityAnalysisParametersInfos.builder()
                .flowFlowSensitivityValueThreshold(FLOW_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .angleFlowSensitivityValueThreshold(ANGLE_FLOW_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .flowVoltageSensitivityValueThreshold(FLOW_VOLTAGE_SENSITIVITY_VALUE_THRESHOLD_DEFAULT_VALUE)
                .sensitivityInjectionsSet(List.of())
                .sensitivityInjection(List.of())
                .sensitivityHVDC(List.of())
                .sensitivityPST(List.of())
                .sensitivityNodes(List.of())
                .build();
    }

    public Long getSensitivityAnalysisFactorsCount(UUID networkUuid, SensitivityFactorsIdsByGroup factorsIds, Boolean isInjectionsSet) {
        var uriComponentsBuilder = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/factors-count")
                .queryParam("isInjectionsSet", isInjectionsSet);

        factorsIds.getIds().forEach((key, value) -> uriComponentsBuilder.queryParam(String.format("ids[%s]", key), value));

        var path = uriComponentsBuilder.buildAndExpand(networkUuid).toUriString();

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.GET, null, Long.class).getBody();
    }
}
