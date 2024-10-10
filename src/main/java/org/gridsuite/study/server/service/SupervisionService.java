/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;
import org.gridsuite.study.server.repository.timepoint.TimePointNodeInfoRepository;
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
import static org.gridsuite.study.server.StudyException.Type.STUDY_NOT_FOUND;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionService.class);
    private static final String DELETION_LOG_MESSAGE = "{} results deletion for all studies : {} seconds";

    private final NetworkService networkStoreService;

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

    private final TimePointNodeInfoRepository timePointNodeInfoRepository;

    private final StateEstimationService stateEstimationService;

    private final StudyRepository studyRepository;

    public SupervisionService(StudyService studyService,
                              NetworkModificationTreeService networkModificationTreeService,
                              NetworkService networkStoreService,
                              TimePointNodeInfoRepository timePointNodeInfoRepository,
                              ReportService reportService,
                              LoadFlowService loadFlowService,
                              DynamicSimulationService dynamicSimulationService,
                              SecurityAnalysisService securityAnalysisService,
                              SensitivityAnalysisService sensitivityAnalysisService,
                              NonEvacuatedEnergyService nonEvacuatedEnergyService,
                              ShortCircuitService shortCircuitService,
                              VoltageInitService voltageInitService,
                              EquipmentInfosService equipmentInfosService,
                              StateEstimationService stateEstimationService,
                              StudyRepository studyRepository) {
        this.networkStoreService = networkStoreService;
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.timePointNodeInfoRepository = timePointNodeInfoRepository;
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
        this.studyRepository = studyRepository;
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

        UUID networkUUID = networkStoreService.getNetworkUuid(studyUuid);
        Long nbIndexesToDelete = getStudyIndexedEquipmentsCount(networkUUID) + getStudyIndexedTombstonedEquipmentsCount(networkUUID);
        equipmentInfosService.deleteAllByNetworkUuid(networkUUID);
        studyService.updateStudyIndexationStatus(studyUuid, StudyIndexationStatus.NOT_INDEXED);

        LOGGER.trace("Indexed equipments deletion for study \"{}\": {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nbIndexesToDelete;
    }

    private Integer deleteLoadflowResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
            timePointNodeInfo.setLoadFlowResultUuid(null);
            reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.LOAD_FLOW.name()));
            timePointNodeInfo.getComputationReports().remove(ComputationType.LOAD_FLOW.name());
        });
        reportService.deleteReports(reportsToDelete);
        loadFlowService.deleteLoadFlowResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.LOAD_FLOW, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return timePointNodeInfoEntities.size();
    }

    private Integer deleteDynamicSimulationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeStatusEntities = timePointNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        timePointNodeStatusEntities.forEach(timePointNodeStatus -> timePointNodeStatus.setDynamicSimulationResultUuid(null));
        //TODO Add logs deletion once they are added
        dynamicSimulationService.deleteResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.DYNAMIC_SIMULATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return timePointNodeStatusEntities.size();
    }

    private Integer deleteSecurityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
            timePointNodeInfo.setSecurityAnalysisResultUuid(null);
            reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.SECURITY_ANALYSIS.name()));
            timePointNodeInfo.getComputationReports().remove(ComputationType.SECURITY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        securityAnalysisService.deleteSecurityAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SECURITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return timePointNodeInfoEntities.size();
    }

    private Integer deleteSensitivityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
            timePointNodeInfo.setSensitivityAnalysisResultUuid(null);
            reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.SENSITIVITY_ANALYSIS.name()));
            timePointNodeInfo.getComputationReports().remove(ComputationType.SENSITIVITY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        sensitivityAnalysisService.deleteSensitivityAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SENSITIVITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        return timePointNodeInfoEntities.size();
    }

    private Integer deleteNonEvacuatedEnergyAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());

        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllByNonEvacuatedEnergyResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
            timePointNodeInfo.setNonEvacuatedEnergyResultUuid(null);
            reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS.name()));
            timePointNodeInfo.getComputationReports().remove(ComputationType.NON_EVACUATED_ENERGY_ANALYSIS.name());
        });
        reportService.deleteReports(reportsToDelete);
        nonEvacuatedEnergyService.deleteNonEvacuatedEnergyResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.NON_EVACUATED_ENERGY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));

        return timePointNodeInfoEntities.size();
    }

    private Integer deleteShortcircuitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        // Reset result uuid and remove logs, for all-buses computations, then for 1-bus ones
        List<TimePointNodeInfoEntity> allBusesTimePointNodeInfoEntities = timePointNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull();
        if (!allBusesTimePointNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            allBusesTimePointNodeInfoEntities.forEach(timePointNodeInfo -> {
                timePointNodeInfo.setShortCircuitAnalysisResultUuid(null);
                reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.SHORT_CIRCUIT.name()));
                timePointNodeInfo.getComputationReports().remove(ComputationType.SHORT_CIRCUIT.name());
            });
            reportService.deleteReports(reportsToDelete);
        }

        List<TimePointNodeInfoEntity> oneBusTimePointNodeInfoEntities = timePointNodeInfoRepository.findAllByOneBusShortCircuitAnalysisResultUuidNotNull();
        if (!oneBusTimePointNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            oneBusTimePointNodeInfoEntities.forEach(timePointNodeInfo -> {
                timePointNodeInfo.setOneBusShortCircuitAnalysisResultUuid(null);
                reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.SHORT_CIRCUIT_ONE_BUS.name()));
                timePointNodeInfo.getComputationReports().remove(ComputationType.SHORT_CIRCUIT_ONE_BUS.name());
            });
            reportService.deleteReports(reportsToDelete);
        }

        // Then delete all results (1-bus and all-buses), cause short-circuit-server cannot make the difference
        shortCircuitService.deleteShortCircuitAnalysisResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.SHORT_CIRCUIT, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        // return distinct processed time point node info count
        return (int) Stream.concat(allBusesTimePointNodeInfoEntities.stream(), oneBusTimePointNodeInfoEntities.stream())
                .map(TimePointNodeInfoEntity::getId)
                .distinct()
                .count();
    }

    private Integer deleteVoltageInitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllByVoltageInitResultUuidNotNull();
        if (!timePointNodeInfoEntities.isEmpty()) {
            List<UUID> reportsToDelete = new ArrayList<>();
            timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
                timePointNodeInfo.setVoltageInitResultUuid(null);
                reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.VOLTAGE_INITIALIZATION.name()));
                timePointNodeInfo.getComputationReports().remove(ComputationType.VOLTAGE_INITIALIZATION.name());
            });
            reportService.deleteReports(reportsToDelete);
        }
        voltageInitService.deleteVoltageInitResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.VOLTAGE_INITIALIZATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return timePointNodeInfoEntities.size();
    }

    private Integer deleteStateEstimationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<TimePointNodeInfoEntity> timePointNodeInfoEntities = timePointNodeInfoRepository.findAllByStateEstimationResultUuidNotNull();
        List<UUID> reportsToDelete = new ArrayList<>();
        timePointNodeInfoEntities.forEach(timePointNodeInfo -> {
            timePointNodeInfo.setStateEstimationResultUuid(null);
            reportsToDelete.add(timePointNodeInfo.getComputationReports().get(ComputationType.STATE_ESTIMATION.name()));
            timePointNodeInfo.getComputationReports().remove(ComputationType.STATE_ESTIMATION.name());
        });
        reportService.deleteReports(reportsToDelete);
        stateEstimationService.deleteStateEstimationResults();
        LOGGER.trace(DELETION_LOG_MESSAGE, ComputationType.STATE_ESTIMATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return timePointNodeInfoEntities.size();
    }

    @Transactional
    public void invalidateAllNodesBuilds(UUID studyUuid) {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        RootNode rootNode = networkModificationTreeService.getStudyTree(studyUuid);
        TimePointEntity timePointEntity = studyRepository.findById(studyUuid).map(StudyEntity::getFirstTimepoint).orElseThrow(() -> new StudyException(STUDY_NOT_FOUND));
        studyService.invalidateBuild(studyUuid, rootNode.getId(), timePointEntity.getId(), false, false, true);
        LOGGER.trace("Nodes builds deletion for study {} in : {} seconds", studyUuid, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
    }
}

