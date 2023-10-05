/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.elasticsearch.EquipmentInfosService;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.StudyIndexationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupervisionService.class);

    private ReportService reportService;

    private LoadFlowService loadFlowService;

    private DynamicSimulationService dynamicSimulationService;

    private SecurityAnalysisService securityAnalysisService;

    private SensitivityAnalysisService sensitivityAnalysisService;

    private ShortCircuitService shortCircuitService;

    private VoltageInitService voltageInitService;

    private final EquipmentInfosService equipmentInfosService;

    private NotificationService notificationService;

    private final StudyRepository studyRepository;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    public SupervisionService(StudyRepository studyRepository, NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository, ReportService reportService, LoadFlowService loadFlowService, DynamicSimulationService dynamicSimulationService, SecurityAnalysisService securityAnalysisService, SensitivityAnalysisService sensitivityAnalysisService, ShortCircuitService shortCircuitService, VoltageInitService voltageInitService, EquipmentInfosService equipmentInfosService, NotificationService notificationService) {
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.studyRepository = studyRepository;
        this.reportService = reportService;
        this.loadFlowService = loadFlowService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
        this.equipmentInfosService = equipmentInfosService;
        this.notificationService = notificationService;
    }

    @Transactional
    public Integer deleteComputationResults(ComputationType computationType, boolean dryRun) {
        switch (computationType) {
            case LOAD_FLOW:
                return dryRun ? loadFlowService.getLoadFlowResultsCount() : deleteLoadflowResults();
            case DYNAMIC_SIMULATION:
                return dryRun ? dynamicSimulationService.getResultsCount() : deleteDynamicSimulationResults();
            case SECURITY_ANALYSIS:
                return dryRun ? securityAnalysisService.getSecurityAnalysisResultsCount() : deleteSecurityAnalysisResults();
            case SENSITIVITY_ANALYSIS:
                return dryRun ? sensitivityAnalysisService.getSensitivityAnalysisResultsCount() : deleteSensitivityAnalysisResults();
            case SHORT_CIRCUIT:
                return dryRun ? shortCircuitService.getShortCircuitResultsCount() : deleteShortcircuitResults();
            case VOLTAGE_INITIALIZATION:
                return dryRun ? voltageInitService.getVoltageInitResultsCount() : deleteVoltageInitResults();
            default:
                throw new StudyException(ELEMENT_NOT_FOUND);
        }
    }

    private void updateStudiesIndexationStatus(StudyIndexationStatus indexationStatus) {
        // findAll loads all entities from the database, so performance and memory might be an issue for big database.
        // I thought those updates could be batched but batch_size and all aren't activated in study-server by now.
        // Finally I thought that for around maybe ~100 studies today, it's ok this way and should be fine up to tens of thousands of studies
        List<StudyEntity> studies = studyRepository.findAll();
        studies.forEach(s -> s.setIndexationStatus(indexationStatus));
        studyRepository.saveAll(studies);
        // This will send a lot of notifications, but it's necessary to update indexation status for openned studies
        studies.forEach(s -> notificationService.emitStudyIndexationStatusChanged(s.getId(), indexationStatus));
    }

    @Transactional
    public Long deleteAllStudiesIndexedEquipments(boolean dryRun) {
        Long nbIndexesToDelete = equipmentInfosService.getEquipmentInfosCount() + equipmentInfosService.getTombstonedEquipmentInfosCount();
        if (!dryRun) {
            AtomicReference<Long> startTime = new AtomicReference<>();
            startTime.set(System.nanoTime());
            equipmentInfosService.deleteAll();
            updateStudiesIndexationStatus(StudyIndexationStatus.NOT_INDEXED);
            LOGGER.trace("Indexed equipments deletion for all studies : {} seconds", TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }
        return nbIndexesToDelete;
    }

    private Integer deleteLoadflowResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        nodes.stream().forEach(node -> node.setLoadFlowResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.LOAD_FLOW.subReporterKey, nodes);
        reportService.deleteTreeReports(subreportToDelete);
        loadFlowService.deleteLoadFlowResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.LOAD_FLOW, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Integer deleteDynamicSimulationResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        nodes.stream().forEach(node -> node.setShortCircuitAnalysisResultUuid(null));
        //TODO Add logs deletion once they are added
        dynamicSimulationService.deleteResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.DYNAMIC_SIMULATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Integer deleteSecurityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setSecurityAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SECURITY_ANALYSIS.subReporterKey, nodes);
        reportService.deleteTreeReports(subreportToDelete);
        securityAnalysisService.deleteSecurityAnalysisResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.SECURITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Integer deleteSensitivityAnalysisResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setSensitivityAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SENSITIVITY_ANALYSIS.subReporterKey, nodes);
        reportService.deleteTreeReports(subreportToDelete);
        sensitivityAnalysisService.deleteSensitivityAnalysisResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.SENSITIVITY_ANALYSIS, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Integer deleteShortcircuitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setShortCircuitAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SHORT_CIRCUIT.subReporterKey, nodes);
        reportService.deleteTreeReports(subreportToDelete);
        shortCircuitService.deleteShortCircuitAnalysisResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.SHORT_CIRCUIT, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Integer deleteVoltageInitResults() {
        AtomicReference<Long> startTime = new AtomicReference<>();
        startTime.set(System.nanoTime());
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByVoltageInitResultUuidNotNull();
        nodes.stream().forEach(node -> node.setVoltageInitResultUuid(null));
        //TODO Add logs deletion once they are added
        voltageInitService.deleteVoltageInitResults();
        LOGGER.trace("{} results deletion for all studies : {} seconds", ComputationType.VOLTAGE_INITIALIZATION, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        return nodes.size();
    }

    private Map<UUID, String> formatSubreportMap(String subReporterKey, List<NetworkModificationNodeInfoEntity> nodes) {
        return nodes.stream().collect(Collectors.toMap(
            node -> node.getReportUuid(),
            node -> node.getId() + "@" + subReporterKey)
        );
    }
}

