/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.*;
import org.gridsuite.study.server.dto.elasticsearch.EquipmentInfos;
import org.gridsuite.study.server.dto.elasticsearch.TombstonedEquipmentInfos;
import org.gridsuite.study.server.dto.supervision.SupervisionStudyInfos;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.elasticsearch.StudyInfosService;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionService.class);
    private static final String DELETION_LOG_MESSAGE = "{} results deletion for all studies : {} seconds";

    private final StudyService studyService;

    private final StudyRepository studyRepository;

    private final NetworkModificationTreeService networkModificationTreeService;

    private final ReportService reportService;

    private final LoadFlowService loadFlowService;

    private final DynamicSimulationService dynamicSimulationService;

    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;

    private final SecurityAnalysisService securityAnalysisService;

    private final SensitivityAnalysisService sensitivityAnalysisService;

    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;

    private final ShortCircuitService shortCircuitService;

    private final VoltageInitService voltageInitService;

    private final EquipmentInfosService equipmentInfosService;

    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    private final StateEstimationService stateEstimationService;

    private final ElasticsearchOperations elasticsearchOperations;

    private final StudyInfosService studyInfosService;
    private final RootNetworkService rootNetworkService;

    public SupervisionService(StudyService studyService,
                              NetworkModificationTreeService networkModificationTreeService,
                              RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository,
                              ReportService reportService,
                              LoadFlowService loadFlowService,
                              DynamicSimulationService dynamicSimulationService,
                              DynamicSecurityAnalysisService dynamicSecurityAnalysisService,
                              SecurityAnalysisService securityAnalysisService,
                              SensitivityAnalysisService sensitivityAnalysisService,
                              NonEvacuatedEnergyService nonEvacuatedEnergyService,
                              ShortCircuitService shortCircuitService,
                              VoltageInitService voltageInitService,
                              EquipmentInfosService equipmentInfosService,
                              StateEstimationService stateEstimationService,
                              ElasticsearchOperations elasticsearchOperations,
                              StudyInfosService studyInfosService,
                              RootNetworkService rootNetworkService,
                              StudyRepository studyRepository
                              ) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
        this.reportService = reportService;
        this.loadFlowService = loadFlowService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.dynamicSecurityAnalysisService = dynamicSecurityAnalysisService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
        this.equipmentInfosService = equipmentInfosService;
        this.stateEstimationService = stateEstimationService;
        this.elasticsearchOperations = elasticsearchOperations;
        this.studyInfosService = studyInfosService;
        this.rootNetworkService = rootNetworkService;
        this.studyRepository = studyRepository;
    }

    @Transactional
    public Integer deleteComputationResults(ComputationType computationType, boolean dryRun) {
        return switch (computationType) {
            case LOAD_FLOW -> dryRun ? loadFlowService.getLoadFlowResultsCount() : deleteLoadflowResults();
            case DYNAMIC_SIMULATION ->
                    dryRun ? dynamicSimulationService.getResultsCount() : deleteDynamicSimulationResults();
            case DYNAMIC_SECURITY_ANALYSIS ->
                    dryRun ? dynamicSecurityAnalysisService.getResultsCount() : deleteDynamicSecurityAnalysisResults();
            case SECURITY_ANALYSIS ->
                    dryRun ? securityAnalysisService.getSecurityAnalysisResultsCount() : deleteSecurityAnalysisResults();
            case SENSITIVITY_ANALYSIS ->
                    dryRun ? sensitivityAnalysisService.getSensitivityAnalysisResultsCount() : deleteSensitivityAnalysisResults();
            case NON_EVACUATED_ENERGY_ANALYSIS ->
                    dryRun ? nonEvacuatedEnergyService.getNonEvacuatedEnergyAnalysisResultsCount() : deleteNonEvacuatedEnergyAnalysisResults();
            case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS ->
                    dryRun ? shortCircuitService.getShortCircuitResultsCount() : deleteShortcircuitResults();
            case VOLTAGE_INITIALIZATION ->
                    dryRun ? voltageInitService.getVoltageInitResultsCount() : deleteVoltageInitResults();
            case STATE_ESTIMATION ->
                dryRun ? stateEstimationService.getStateEstimationResultsCount() : deleteStateEstimationResults();
            default -> throw new StudyException(ELEMENT_NOT_FOUND);
        };
    }

    @Transactional(readOnly = true)
    public List<SupervisionStudyInfos> getStudies() {
        return studyRepository.findAll().stream()
                .map(SupervisionService::toSupervisionStudyInfosDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UUID> getAllRootNetworksUuids() {
        return rootNetworkService.getAllRootNetworkUuids();
    }

    private static SupervisionStudyInfos toSupervisionStudyInfosDto(StudyEntity entity) {
        return SupervisionStudyInfos.builder()
                .id(entity.getId())
                .rootNetworkInfos(
                        entity.getRootNetworks().stream().map(rootNetworkEntity -> RootNetworkInfos.builder()
                                .id(rootNetworkEntity.getId())
                                .networkInfos(
                                        new NetworkInfos(rootNetworkEntity.getNetworkUuid(), rootNetworkEntity.getNetworkId())
                                ).build()
                            ).toList())
                .caseUuids(entity.getRootNetworks().stream().map(RootNetworkEntity::getCaseUuid).toList())
                .build();
    }

    public long getStudyIndexedEquipmentsCount(UUID networkUUID) {
        return equipmentInfosService.getEquipmentInfosCount(networkUUID);
    }

    public long getStudyIndexedTombstonedEquipmentsCount(UUID networkUUID) {
        return equipmentInfosService.getTombstonedEquipmentInfosCount(networkUUID);
    }

    public long getIndexedStudiesCount() {
        return studyInfosService.getStudyInfosCount();
    }

    public long getIndexedEquipmentsCount() {
        return equipmentInfosService.getEquipmentInfosCount();
    }

    public long getIndexedTombstonedEquipmentsCount() {
        return equipmentInfosService.getTombstonedEquipmentInfosCount();
    }

    private Integer deleteLoadflowResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
            rootNetworkNodeInfo.setLoadFlowResultUuid(null);
            reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.LOAD_FLOW.name()));
            rootNetworkNodeInfo.getComputationReports().remove(ComputationType.LOAD_FLOW.name());
        });
        reportService.deleteReports(reportsToDelete);
        loadFlowService.deleteAllLoadFlowResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.LOAD_FLOW, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteDynamicSimulationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeStatusEntities = rootNetworkNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        rootNetworkNodeStatusEntities.forEach(rootNetworkNodeStatus -> rootNetworkNodeStatus.setDynamicSimulationResultUuid(null));
        dynamicSimulationService.deleteAllResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.DYNAMIC_SIMULATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeStatusEntities.size();
    }

    private Integer deleteDynamicSecurityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeStatusEntities = rootNetworkNodeInfoRepository.findAllByDynamicSecurityAnalysisResultUuidNotNull();
        rootNetworkNodeStatusEntities.forEach(rootNetworkNodeStatus -> rootNetworkNodeStatus.setDynamicSecurityAnalysisResultUuid(null));
        dynamicSecurityAnalysisService.deleteAllResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.DYNAMIC_SECURITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeStatusEntities.size();
    }

    private Integer deleteSecurityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
            rootNetworkNodeInfo.setSecurityAnalysisResultUuid(null);
            reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.SECURITY_ANALYSIS.name()));
            rootNetworkNodeInfo.getComputationReports().remove(ComputationType.SECURITY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        securityAnalysisService.deleteAllSecurityAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SECURITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteSensitivityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
            rootNetworkNodeInfo.setSensitivityAnalysisResultUuid(null);
            reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.SENSITIVITY_ANALYSIS.name()));
            rootNetworkNodeInfo.getComputationReports().remove(ComputationType.SENSITIVITY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        sensitivityAnalysisService.deleteAllSensitivityAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SENSITIVITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteNonEvacuatedEnergyAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByNonEvacuatedEnergyResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
            rootNetworkNodeInfo.setNonEvacuatedEnergyResultUuid(null);
            reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS.name()));
            rootNetworkNodeInfo.getComputationReports().remove(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        nonEvacuatedEnergyService.deleteAllNonEvacuatedEnergyResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteShortcircuitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        // Reset result uuid and remove logs, for all-buses computations, then for 1-bus ones
        List<RootNetworkNodeInfoEntity> allBusesrootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull();
        if (!allBusesrootNetworkNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            allBusesrootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
                rootNetworkNodeInfo.setShortCircuitAnalysisResultUuid(null);
                reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.SHORT_CIRCUIT.name()));
                rootNetworkNodeInfo.getComputationReports().remove(ComputationType.SHORT_CIRCUIT.name());
            });
            reportService.deleteReports(reportsToDelete);
        }

        List<RootNetworkNodeInfoEntity> oneBusrootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByOneBusShortCircuitAnalysisResultUuidNotNull();
        if (!oneBusrootNetworkNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            oneBusrootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
                rootNetworkNodeInfo.setOneBusShortCircuitAnalysisResultUuid(null);
                reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.SHORT_CIRCUIT_ONE_BUS.name()));
                rootNetworkNodeInfo.getComputationReports().remove(ComputationType.SHORT_CIRCUIT_ONE_BUS.name());
            });
            reportService.deleteReports(reportsToDelete);
        }

        // Then delete all results (1-bus and all-buses), cause short-circuit-server cannot make the difference
        shortCircuitService.deleteAllShortCircuitAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SHORT_CIRCUIT, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        // return distinct processed time point node info count
        return (int) Stream.concat(allBusesrootNetworkNodeInfoEntities.stream(), oneBusrootNetworkNodeInfoEntities.stream())
                .map(RootNetworkNodeInfoEntity::getId)
                .distinct()
                .count();
    }

    private Integer deleteVoltageInitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByVoltageInitResultUuidNotNull();
        if (!rootNetworkNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
                rootNetworkNodeInfo.setVoltageInitResultUuid(null);
                reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.VOLTAGE_INITIALIZATION.name()));
                rootNetworkNodeInfo.getComputationReports().remove(ComputationType.VOLTAGE_INITIALIZATION.name());
            });
            reportService.deleteReports(reportsToDelete);
        }
        voltageInitService.deleteAllVoltageInitResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.VOLTAGE_INITIALIZATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteStateEstimationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeInfoEntities = rootNetworkNodeInfoRepository.findAllByStateEstimationResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        rootNetworkNodeInfoEntities.forEach(rootNetworkNodeInfo -> {
            rootNetworkNodeInfo.setStateEstimationResultUuid(null);
            reportsToDelete.add(rootNetworkNodeInfo.getComputationReports().get(ComputationType.STATE_ESTIMATION.name()));
            rootNetworkNodeInfo.getComputationReports().remove(ComputationType.STATE_ESTIMATION.name());
        });
        reportService.deleteReports(reportsToDelete);
        stateEstimationService.deleteAllStateEstimationResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.STATE_ESTIMATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    @Transactional
    public void unbuildAllNodes(UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        //TODO: to parallelize ?
        studyService.getExistingBasicRootNetworkInfos(studyUuid).forEach(rootNetwork ->
            studyService.invalidateNodeTree(studyUuid, rootNodeUuid, rootNetwork.rootNetworkUuid())
        );

        LOGGER.trace("Nodes builds deletion for study {} in : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }

    @Transactional
    public void recreateStudyIndices() {
        recreateIndex(CreatedStudyBasicInfos.class);
        recreateIndex(EquipmentInfos.class);
        recreateIndex(TombstonedEquipmentInfos.class);

        studyService.getStudies().forEach(study -> {
            List<RootNetworkInfos> rootNetworkInfos = rootNetworkService.getRootNetworkInfosWithLinksInfos(study.getId());
            for (RootNetworkInfos rootNetworkInfo : rootNetworkInfos) {
                studyService.updateRootNetworkIndexationStatus(study.getId(), rootNetworkInfo.getId(), RootNetworkIndexationStatus.NOT_INDEXED);
            }
        });
    }

    private void recreateIndex(Class<?> indexClass) {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(indexClass);
        boolean isDeleted = indexOperations.delete();
        if (!isDeleted) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete ElasticSearch index for: " + indexClass.getSimpleName());
        }

        boolean isCreated = indexOperations.createWithMapping();
        if (!isCreated) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create ElasticSearch index for: " + indexClass.getSimpleName());
        }
    }
}

