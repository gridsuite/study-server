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
import org.gridsuite.study.server.dto.SensitivityAnalysisStatus;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyContingencies;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyGeneratorLimitByType;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsLimit;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyInputData;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranches;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyStageDefinition;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyParametersInfos;
import org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyStagesSelection;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyContingenciesEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsLimitByTypeEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyGeneratorsLimitEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyMonitoredBranchesEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyParametersEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyStageDefinitionEntity;
import org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy.NonEvacuatedEnergyStagesSelectionEntity;
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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.StudyConstants.DELIMITER;
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

    private static final String PROVIDER_DEFAULT_VALUE = "OpenLoadFlow";

    private String sensitivityAnalysisServerBaseUri;

    @Autowired
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final NetworkModificationTreeService networkModificationTreeService;

    @Autowired
    NonEvacuatedEnergyService(RemoteServicesProperties remoteServicesProperties,
                              NetworkModificationTreeService networkModificationTreeService,
                              ObjectMapper objectMapper) {
        this.sensitivityAnalysisServerBaseUri = remoteServicesProperties.getServiceUri("sensitivity-analysis-server");
        this.networkModificationTreeService = networkModificationTreeService;
        this.objectMapper = objectMapper;
    }

    public void setSensitivityAnalysisServerBaseUri(String sensitivityAnalysisServerBaseUri) {
        this.sensitivityAnalysisServerBaseUri = sensitivityAnalysisServerBaseUri + DELIMITER;
    }

    public void assertNonEvacuatedEnergyNotRunning(UUID nodeUuid) {
        String nonEvacuatedEnergyStatus = getNonEvacuatedEnergyStatus(nodeUuid);
        if (SensitivityAnalysisStatus.RUNNING.name().equals(nonEvacuatedEnergyStatus)) {
            throw new StudyException(NON_EVACUATED_ENERGY_RUNNING);
        }
    }

    public UUID runNonEvacuatedEnergy(UUID nodeUuid, UUID networkUuid,
                                      String variantId,
                                      UUID reportUuid,
                                      NonEvacuatedEnergyInputData nonEvacuatedEnergyInputData) {
        String receiver;
        try {
            receiver = URLEncoder.encode(objectMapper.writeValueAsString(new NodeReceiver(nodeUuid)), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
        var uriComponentsBuilder = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/networks/{networkUuid}/non-evacuated-energy")
            .queryParam("reportUuid", reportUuid.toString())
            .queryParam("reporterId", nodeUuid.toString());
        if (!StringUtils.isBlank(variantId)) {
            uriComponentsBuilder.queryParam(QUERY_PARAM_VARIANT_ID, variantId);
        }
        var path = uriComponentsBuilder
            .queryParam(QUERY_PARAM_RECEIVER, receiver)
            .buildAndExpand(networkUuid).toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<NonEvacuatedEnergyInputData> httpEntity = new HttpEntity<>(nonEvacuatedEnergyInputData, headers);

        return restTemplate.exchange(sensitivityAnalysisServerBaseUri + path, HttpMethod.POST, httpEntity, UUID.class).getBody();
    }

    public String getNonEvacuatedEnergyResult(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getNonEvacuatedEnergyResultUuid(nodeUuid);
        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        // initializing from uri string (not from path string) allows build() to escape selector content
        URI uri = UriComponentsBuilder.fromUriString(sensitivityAnalysisServerBaseUri)
            .pathSegment(SENSITIVITY_ANALYSIS_API_VERSION, "non-evacuated-energy-results", resultUuidOpt.get().toString())
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

    public String getNonEvacuatedEnergyStatus(UUID nodeUuid) {
        String result;
        Optional<UUID> resultUuidOpt = networkModificationTreeService.getNonEvacuatedEnergyResultUuid(nodeUuid);

        if (resultUuidOpt.isEmpty()) {
            return null;
        }

        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy-results/{resultUuid}/status")
            .buildAndExpand(resultUuidOpt.get()).toUriString();
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

    public void stopNonEvacuatedEnergy(UUID studyUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);

        Optional<UUID> resultUuidOpt = networkModificationTreeService.getNonEvacuatedEnergyResultUuid(nodeUuid);
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
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy-results/{resultUuid}/stop")
            .queryParam(QUERY_PARAM_RECEIVER, receiver).buildAndExpand(resultUuidOpt.get()).toUriString();

        restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
    }

    public void invalidateNonEvacuatedEnergyStatus(List<UUID> uuids) {
        if (!uuids.isEmpty()) {
            String path = UriComponentsBuilder
                .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy-results/invalidate-status")
                .queryParam(RESULT_UUID, uuids).build().toUriString();

            restTemplate.put(sensitivityAnalysisServerBaseUri + path, Void.class);
        }
    }

    public void deleteNonEvacuatedEnergyResult(UUID uuid) {
        String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy-results/{resultUuid}")
            .buildAndExpand(uuid)
            .toUriString();

        restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
    }

    public void deleteNonEvacuatedEnergyResults() {
        try {
            String path = UriComponentsBuilder.fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/non-evacuated-energy-results")
                .toUriString();
            restTemplate.delete(sensitivityAnalysisServerBaseUri + path);
        } catch (HttpStatusCodeException e) {
            throw handleHttpError(e, DELETE_COMPUTATION_RESULTS_FAILED);
        }
    }

    public Integer getNonEvacuatedEnergyAnalysisResultsCount() {
        String path = UriComponentsBuilder
            .fromPath(DELIMITER + SENSITIVITY_ANALYSIS_API_VERSION + "/supervision/non-evacuated-energy-results-count").toUriString();
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
                stagesSelectionEntity.setStageDefinitionIndex(stagesSelection.getStagesDefinitonIndex());
                stagesSelectionEntities.add(stagesSelectionEntity);
            }
        }

        NonEvacuatedEnergyGeneratorsLimitEntity generatorsLimitEntity = new NonEvacuatedEnergyGeneratorsLimitEntity();
        if (parameters.getGeneratorsLimit() != null) {
            generatorsLimitEntity.setSensitivityThreshold(parameters.getGeneratorsLimit().getSensitivityThreshold());
            for (NonEvacuatedEnergyGeneratorLimitByType generatorLimitByType : parameters.getGeneratorsLimit().getGenerators()) {
                NonEvacuatedEnergyGeneratorsLimitByTypeEntity generatorsLimitByTypeEntity = new NonEvacuatedEnergyGeneratorsLimitByTypeEntity();
                generatorsLimitByTypeEntity.setActivated(generatorLimitByType.isActivated());
                generatorsLimitByTypeEntity.setEnergySource(generatorLimitByType.getEnergySource());
                generatorsLimitByTypeEntity.setGenerators(EquipmentsContainerEmbeddable.toEmbeddableContainerEquipments(generatorLimitByType.getGenerators()));
                generatorsLimitEntity.addGenerators(generatorsLimitByTypeEntity);
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

        return new NonEvacuatedEnergyParametersEntity(null, parameters.getProvider(), stageDefinitionEntities, stagesSelectionEntities,
            generatorsLimitEntity, monitoredBranchesEntities, contingenciesEntities);
    }

    public static NonEvacuatedEnergyParametersInfos fromEntity(NonEvacuatedEnergyParametersEntity entity) {
        Objects.requireNonNull(entity);

        List<NonEvacuatedEnergyStageDefinition> stageDefinitionParam = new ArrayList<>();
        entity.getStagesDefinition().stream().map(stageDefinitionEntity ->
                new NonEvacuatedEnergyStageDefinition(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(stageDefinitionEntity.getGenerators()),
                    stageDefinitionEntity.getEnergySource(), stageDefinitionEntity.getPMaxPercents())
        ).collect(Collectors.toList()).forEach(stageDefinitionParam::add);

        List<NonEvacuatedEnergyStagesSelection> stagesSelectionParam = new ArrayList<>();
        for (NonEvacuatedEnergyStagesSelectionEntity stagesSelectionEntity : entity.getStagesSelection()) {
            NonEvacuatedEnergyStagesSelection stagesSelection = new NonEvacuatedEnergyStagesSelection();
            stagesSelection.setName(stagesSelectionEntity.getName());
            stagesSelection.setActivated(stagesSelectionEntity.isActivated());
            stagesSelection.setPMaxPercentsIndex(stagesSelectionEntity.getPMaxPercentsIndex());
            stagesSelection.setStagesDefinitonIndex(stagesSelectionEntity.getStageDefinitionIndex());
            stagesSelectionParam.add(stagesSelection);
        }

        NonEvacuatedEnergyGeneratorsLimit generatorsLimitParam = new NonEvacuatedEnergyGeneratorsLimit(entity.getGeneratorsLimit().getSensitivityThreshold(),
            entity.getGeneratorsLimit().getGeneratorsByType().stream().map(generatorsLimitByTypeEntity ->
                new NonEvacuatedEnergyGeneratorLimitByType(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(generatorsLimitByTypeEntity.getGenerators()),
                    generatorsLimitByTypeEntity.getEnergySource(), generatorsLimitByTypeEntity.isActivated())).toList());

        List<NonEvacuatedEnergyMonitoredBranches> monitoredBranchesParam = new ArrayList<>();
        entity.getMonitoredBranches().stream().map(monitoredBranchesEntity ->
            new NonEvacuatedEnergyMonitoredBranches(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(monitoredBranchesEntity.getMonitoredBranches()),
                monitoredBranchesEntity.isActivated(), monitoredBranchesEntity.isIstN(), monitoredBranchesEntity.getLimitNameN(), monitoredBranchesEntity.getNCoefficient(),
                monitoredBranchesEntity.isIstNm1(), monitoredBranchesEntity.getLimitNameNm1(), monitoredBranchesEntity.getNm1Coefficient())
        ).forEach(monitoredBranchesParam::add);

        List<NonEvacuatedEnergyContingencies> contingenciesParam = new ArrayList<>();
        entity.getContingencies().stream().map(contingenciesEntity ->
            new NonEvacuatedEnergyContingencies(EquipmentsContainerEmbeddable.fromEmbeddableContainerEquipments(contingenciesEntity.getContingencies()), contingenciesEntity.isActivated())
        ).forEach(contingenciesParam::add);

        return new NonEvacuatedEnergyParametersInfos(entity.getProvider(), stageDefinitionParam, stagesSelectionParam, generatorsLimitParam,
            monitoredBranchesParam, contingenciesParam);
    }

    public static NonEvacuatedEnergyParametersInfos getDefaultNonEvacuatedEnergyParametersValues() {
        return NonEvacuatedEnergyParametersInfos.builder()
                .provider(PROVIDER_DEFAULT_VALUE)
                .stagesDefinition(List.of())
                .stagesSelection(List.of())
                .generatorsLimit(new NonEvacuatedEnergyGeneratorsLimit(0., List.of()))
                .monitoredBranches(List.of())
                .contingencies(List.of())
                .build();
    }
}
