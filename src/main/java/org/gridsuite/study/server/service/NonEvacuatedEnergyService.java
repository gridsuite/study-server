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
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyContingencies;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorCappingsByType;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappings;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranches;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStageDefinition;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyParametersInfos;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStagesSelection;
import org.gridsuite.study.server.dto.nonevacuatedenergy.NonEvacuatedEnergyStatus;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyContingenciesEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappingsByTypeEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsCappingsEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranchesEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyStageDefinitionEntity;
import org.gridsuite.study.server.repository.nonevacuatedenergy.NonEvacuatedEnergyStagesSelectionEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
import static org.gridsuite.study.server.StudyConstants.HEADER_USER_ID;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_RECEIVER;
import static org.gridsuite.study.server.StudyConstants.QUERY_PARAM_VARIANT_ID;
import static org.gridsuite.study.server.StudyConstants.SENSITIVITY_ANALYSIS_API_VERSION;
import static org.gridsuite.study.server.StudyException.Type.DELETE_COMPUTATION_RESULTS_FAILED;
import static org.gridsuite.study.server.StudyException.Type.NON_EVACUATED_ENERGY_ERROR;
import static org.gridsuite.study.server.StudyException.Type.NON_EVACUATED_ENERGY_NOT_FOUND;
import static org.gridsuite.study.server.StudyException.Type.NON_EVACUATED_ENERGY_RUNNING;
import static org.gridsuite.study.server.utils.StudyUtils.handleHttpError;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Service
public class NonEvacuatedEnergyService {

    static final String RESULT_UUID = "resultUuid";

    private String sensitivityAnalysisServerBaseUri;

    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Autowired
    NonEvacuatedEnergyService(RemoteServicesProperties remoteServicesProperties,
                              ObjectMapper objectMapper,
                              RestTemplate restTemplate) {
        this.sensitivityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("sensitivity-analysis-server");
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public void setSensitivityAnalysisServerBaseUri(String sensitivityAnalysisServerBaseUri) {
        this.sensitivityAnalysisServerBaseUri = sensitivityAnalysisServerBaseUri + DELIMITER;
    }

    public void assertNonEvacuatedEnergyNotRunning(UUID resultUuid) {
        String nonEvacuatedEnergyStatus = getNonEvacuatedEnergyStatus(resultUuid);
        if (NonEvacuatedEnergyStatus.RUNNING.name().equals(nonEvacuatedEnergyStatus)) {
            throw new StudyException(NON_EVACUATED_ENERGY_RUNNING);
        }
    }

    public UUID runNonEvacuatedEnergy(UUID nodeUuid, UUID rootNetworkUuid, UUID networkUuid,
                                      String variantId,
                                      UUID reportUuid,
                                      String provider,
                                      NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData,
                                      UUID loadFlowParametersUuid,
                                      String userId) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/non-evacuated-energy/run-and-save")
            .queryParam("reportUuid", reportUuid.toString())
            .queryParam("reporterId", nodeUuid.toString())
            .queryParam("reportType", StudyService.ReportType.NON_EVACUATED_ENERGY_ANALYSIS.reportKey);
        if (!provider.isEmpty()) {
            uriComponentsBuilder.queryParam("provider", provider);
        }
        if (loadFlowParametersUuid != null) {
            uriComponentsBuilder.queryParam("loadFlowParametersUuid", loadFlowParametersUuid);
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

        HttpEntity<NonEvacuatedEnergyInputData> httpEntity = new HttpEntity<>(nonEvacuatedEnergyInputData, headers);

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getNonEvacuatedEnergyResult(UUID resultUuid) {
        String result;
        if (resultUuid == null) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
            .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, "non-evacuated-energy", "results", resultUuid.toString())
            .build().encode().toUri();
        try {
            result = restTemplate.getForObject(uri, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NON_EVACUATED_ENERGY_NOT_FOUND);
            } else {
                throw handleHttpError(e, NON_EVACUATED_ENERGY_ERROR);
            }
        }
        return result;
    }

    public String getNonEvacuatedEnergyStatus(UUID resultUuid) {
        String result;

        if (resultUuid == null) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy/results/{resultUuid}/status")
            .buildAndExpand(resultUuid).toUriString();
        try {
            result = restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, String.class);
        } catch (HttpStatusCodeException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                throw new StudyException(NON_EVACUATED_ENERGY_NOT_FOUND);
            }
            throw e;
        }
        return result;
    }

    public void stopNonEvacuatedEnergy(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, UUID resultUuid, String userId) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        if (resultUuid == null) {
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_USER_ID, userId);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid, rootNetworkUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy/results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuid).toUriString();

        restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.PUT, new HttpEntity<>(headers), Void.class);
    }

    public void invalidateNonEvacuatedEnergyStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy/results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void deleteNonEvacuatedEnergyResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy/results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
    }

    public void deleteNonEvacuatedEnergyResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy/results")
                .toUriString();
            restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getNonEvacuatedEnergyAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/supervision/non-evacuated-energy/results-count").toUriString();
        return restTemplate.getForObject(sensitivityAnalysisServerBaseUri + path, Integer.class);
    }

    public static NonEvacuatedEnergyParametersEntity toEntity(NonEvacuatedEnergyParametersInfos parameters) {
        Objects.requireNonNull(parameters);

        List<NonEvacuatedEnergyStageDefinitionEntity> stageDefinitionEntities = new ArrayList<>();
        if (parameters.getStagesDefinition() != null) {
            for (NonEvacuatedEnergyStageDefinition stageDefinition : parameters.getStagesDefinition()) {
                NonEvacuatedEnergyStageDefinitionEntity stageDefinitionEntity = new NonEvacuatedEnergyStageDefinitionEntity();
                stageDefinitionEntity.setEnergySource(stageDefinition.getEnergySource());
                stageDefinitionEntity.setGenerators(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(stageDefinition.getGenerators()));
                stageDefinitionEntity.setPMaxPercents(stageDefinition.getPMaxPercents());
                stageDefinitionEntities.add(stageDefinitionEntity);
            }
        }

        List<NonEvacuatedEnergyStagesSelectionEntity> stagesSelectionEntities = new ArrayList<>();
        if (parameters.getStagesSelection() != null) {
            for (NonEvacuatedEnergyStagesSelection stagesSelection : parameters.getStagesSelection()) {
                NonEvacuatedEnergyStagesSelectionEntity stagesSelectionEntity = new NonEvacuatedEnergyStagesSelectionEntity();
                stagesSelectionEntity.setName(stagesSelection.getName());
                stagesSelectionEntity.setActivated(stagesSelection.isActivated());
                stagesSelectionEntity.setPMaxPercentsIndex(stagesSelection.getPMaxPercentsIndex());
                stagesSelectionEntity.setStageDefinitionIndex(stagesSelection.getStagesDefinitionIndex());
                stagesSelectionEntities.add(stagesSelectionEntity);
            }
        }

        NonEvacuatedEnergyGeneratorsCappingsEntity generatorsCappingsEntity = new NonEvacuatedEnergyGeneratorsCappingsEntity();
        if (parameters.getGeneratorsCappings() != null) {
            generatorsCappingsEntity.setSensitivityThreshold(parameters.getGeneratorsCappings().getSensitivityThreshold());
            for (NonEvacuatedEnergyGeneratorCappingsByType generatorsCappingsByType : parameters.getGeneratorsCappings().getGenerators()) {
                NonEvacuatedEnergyGeneratorsCappingsByTypeEntity generatorsCappingsByTypeEntity = new NonEvacuatedEnergyGeneratorsCappingsByTypeEntity();
                generatorsCappingsByTypeEntity.setActivated(generatorsCappingsByType.isActivated());
                generatorsCappingsByTypeEntity.setEnergySource(generatorsCappingsByType.getEnergySource());
                generatorsCappingsByTypeEntity.setGenerators(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(generatorsCappingsByType.getGenerators()));
                generatorsCappingsEntity.addGenerators(generatorsCappingsByTypeEntity);
            }
        }

        List<NonEvacuatedEnergyMonitoredBranchesEntity> monitoredBranchesEntities = new ArrayList<>();
        if (parameters.getMonitoredBranches() != null) {
            for (NonEvacuatedEnergyMonitoredBranches monitoredBranches : parameters.getMonitoredBranches()) {
                NonEvacuatedEnergyMonitoredBranchesEntity monitoredBranchesEntity = new NonEvacuatedEnergyMonitoredBranchesEntity();
                monitoredBranchesEntity.setActivated(monitoredBranches.isActivated());
                monitoredBranchesEntity.setIstN(monitoredBranches.isIstN());
                monitoredBranchesEntity.setLimitNameN(monitoredBranches.getLimitNameN());
                monitoredBranchesEntity.setNCoefficient(monitoredBranches.getNCoefficient());
                monitoredBranchesEntity.setIstNm1(monitoredBranches.isIstNm1());
                monitoredBranchesEntity.setLimitNameNm1(monitoredBranches.getLimitNameNm1());
                monitoredBranchesEntity.setNm1Coefficient(monitoredBranches.getNm1Coefficient());
                monitoredBranchesEntity.setMonitoredBranches(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(monitoredBranches.getBranches()));
                monitoredBranchesEntities.add(monitoredBranchesEntity);
            }
        }

        List<NonEvacuatedEnergyContingenciesEntity> contingenciesEntities = new ArrayList<>();
        if (parameters.getContingencies() != null) {
            for (NonEvacuatedEnergyContingencies contingencies : parameters.getContingencies()) {
                NonEvacuatedEnergyContingenciesEntity contingenciesEntity = new NonEvacuatedEnergyContingenciesEntity();
                contingenciesEntity.setActivated(contingencies.isActivated());
                contingenciesEntity.setContingencies(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(contingencies.getContingencies()));
                contingenciesEntities.add(contingenciesEntity);
            }
        }

        return new NonEvacuatedEnergyParametersEntity(null, stageDefinitionEntities, stagesSelectionEntities,
            generatorsCappingsEntity, monitoredBranchesEntities, contingenciesEntities);
    }

    public static NonEvacuatedEnergyParametersInfos fromEntity(NonEvacuatedEnergyParametersEntity entity) {
        Objects.requireNonNull(entity);

        List<NonEvacuatedEnergyStageDefinition> stageDefinitionParam = entity.getStagesDefinition().stream().map(stageDefinitionEntity ->
            new NonEvacuatedEnergyStageDefinition(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(stageDefinitionEntity.getGenerators()),
                stageDefinitionEntity.getEnergySource(), stageDefinitionEntity.getPMaxPercents())
        ).toList();

        List<NonEvacuatedEnergyStagesSelection> stagesSelectionParam = entity.getStagesSelection().stream().map(stagesSelectionEntity ->
            new NonEvacuatedEnergyStagesSelection(stagesSelectionEntity.getName(),
                                                  stagesSelectionEntity.getStageDefinitionIndex(),
                                                  stagesSelectionEntity.getPMaxPercentsIndex(),
                                                  stagesSelectionEntity.isActivated())
        ).toList();

        NonEvacuatedEnergyGeneratorsCappings generatorsCappingsParam = new NonEvacuatedEnergyGeneratorsCappings(entity.getGeneratorsCappings().getSensitivityThreshold(),
            entity.getGeneratorsCappings().getGeneratorsCappings().stream().map(generatorsCappingsByTypeEntity ->
                new NonEvacuatedEnergyGeneratorCappingsByType(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(generatorsCappingsByTypeEntity.getGenerators()),
                    generatorsCappingsByTypeEntity.getEnergySource(), generatorsCappingsByTypeEntity.isActivated())).toList());

        List<NonEvacuatedEnergyMonitoredBranches> monitoredBranchesParam = entity.getMonitoredBranches().stream().map(monitoredBranchesEntity ->
            new NonEvacuatedEnergyMonitoredBranches(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(monitoredBranchesEntity.getMonitoredBranches()),
                monitoredBranchesEntity.isActivated(), monitoredBranchesEntity.isIstN(), monitoredBranchesEntity.getLimitNameN(), monitoredBranchesEntity.getNCoefficient(),
                monitoredBranchesEntity.isIstNm1(), monitoredBranchesEntity.getLimitNameNm1(), monitoredBranchesEntity.getNm1Coefficient())
        ).toList();

        List<NonEvacuatedEnergyContingencies> contingenciesParam = entity.getContingencies().stream().map(contingenciesEntity ->
            new NonEvacuatedEnergyContingencies(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(contingenciesEntity.getContingencies()), contingenciesEntity.isActivated())
        ).toList();

        return new NonEvacuatedEnergyParametersInfos(stageDefinitionParam, stagesSelectionParam, generatorsCappingsParam,
            monitoredBranchesParam, contingenciesParam);
    }

    public static NonEvacuatedEnergyParametersInfos getDefaultNonEvacuatedEnergyParametersInfos() {
        return NonEvacuatedEnergyParametersInfos.builder()
                .stagesDefinition(List.of())
                .stagesSelection(List.of())
                .generatorsCappings(new NonEvacuatedEnergyGeneratorsCappings(0., List.of()))
                .monitoredBranches(List.of())
                .contingencies(List.of())
                .build();
    }
}
