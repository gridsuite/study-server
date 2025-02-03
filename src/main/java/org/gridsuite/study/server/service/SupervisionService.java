/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final NetworkModificationTreeService networkModificationTreeService;

    private final ReportService reportService;

    private final LoadFlowService loadFlowService;

    private final DynamicSimulationService dynamicSimulationService;

    private final SecurityAnalysisService securityAnalysisService;

    private final SensitivityAnalysisService sensitivityAnalysisService;

    private final NonEvacuatedEnergyService nonEvacuatedEnergyService;

    private final ShortCircuitService shortCircuitService;

    private final VoltageInitService voltageInitService;

    private final EquipmentInfosService equipmentInfosService;

    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    private final RootNetworkService rootNetworkService;

    private final StateEstimationService stateEstimationService;

    public SupervisionService(StudyService studyService,
                              NetworkModificationTreeService networkModificationTreeService,
                              RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository,
                              RootNetworkService rootNetworkService,
                              ReportService reportService,
                              LoadFlowService loadFlowService,
                              DynamicSimulationService dynamicSimulationService,
                              SecurityAnalysisService securityAnalysisService,
                              SensitivityAnalysisService sensitivityAnalysisService,
                              NonEvacuatedEnergyService nonEvacuatedEnergyService,
                              ShortCircuitService shortCircuitService,
                              VoltageInitService voltageInitService,
                              EquipmentInfosService equipmentInfosService,
                              StateEstimationService stateEstimationService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
        this.rootNetworkService = rootNetworkService;
        this.reportService = reportService;
        this.loadFlowService = loadFlowService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.nonEvacuatedEnergyService = nonEvacuatedEnergyService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
        this.equipmentInfosService = equipmentInfosService;
        this.stateEstimationService = stateEstimationService;
    }

    @Transactional
    public Integer deleteComputationResults(ComputationType computationType, boolean dryRun) {
        return switch (computationType) {
            case LOAD_FLOW -> dryRun ? loadFlowService.getLoadFlowResultsCount() : deleteLoadflowResults();
            case DYNAMIC_SIMULATION ->
                    dryRun ? dynamicSimulationService.getResultsCount() : deleteDynamicSimulationResults();
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

    public long getStudyIndexedEquipmentsCount(UUID networkUUID) {
        return equipmentInfosService.getEquipmentInfosCount(networkUUID);
    }

    public long getStudyIndexedTombstonedEquipmentsCount(UUID networkUUID) {
        return equipmentInfosService.getTombstonedEquipmentInfosCount(networkUUID);
    }

    public long getIndexedEquipmentsCount() {
        return equipmentInfosService.getEquipmentInfosCount();
    }

    public long getIndexedTombstonedEquipmentsCount() {
        return equipmentInfosService.getTombstonedEquipmentInfosCount();
    }

    @Transactional
    public Long deleteStudyIndexedEquipmentsAndTombstoned(UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        AtomicReference<Long> nbIndexesToDelete = new AtomicReference<>(0L);

        studyService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity -> {
            UUID networkUUID = rootNetworkService.getNetworkUuid(rootNetworkEntity.getId());
            nbIndexesToDelete.updateAndGet(v -> v + getStudyIndexedEquipmentsCount(networkUUID) + getStudyIndexedTombstonedEquipmentsCount(networkUUID));
            equipmentInfosService.deleteAllByNetworkUuid(networkUUID);
        });

        studyService.updateStudyIndexationStatus(studyUuid, StudyIndexationStatus.NOT_INDEXED);

        LOGGER.trace("Indexed equipments deletion for study \"{}\": {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nbIndexesToDelete.get();
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
        loadFlowService.deleteLoadFlowResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.LOAD_FLOW, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    private Integer deleteDynamicSimulationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<RootNetworkNodeInfoEntity> rootNetworkNodeStatusEntities = rootNetworkNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        rootNetworkNodeStatusEntities.forEach(rootNetworkNodeStatus -> rootNetworkNodeStatus.setDynamicSimulationResultUuid(null));
        //TODO Add logs deletion once they are added
        dynamicSimulationService.deleteResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.DYNAMIC_SIMULATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
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
        securityAnalysisService.deleteSecurityAnalysisResults();
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
        sensitivityAnalysisService.deleteSensitivityAnalysisResults();
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
        nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults();
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
        shortCircuitService.deleteShortCircuitAnalysisResults();
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
        voltageInitService.deleteVoltageInitResults();
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
        stateEstimationService.deleteStateEstimationResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.STATE_ESTIMATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return rootNetworkNodeInfoEntities.size();
    }

    @Transactional
    public void invalidateAllNodesBuilds(UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        UUID rootNodeUuid = networkModificationTreeService.getStudyRootNodeUuid(studyUuid);
        //TODO: to parallelize ?
        studyService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity ->
            studyService.invalidateBuild(studyUuid, rootNodeUuid, rootNetworkEntity.getId(), false, false, true)
        );

        LOGGER.trace("Nodes builds deletion for study {} in : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }
}

