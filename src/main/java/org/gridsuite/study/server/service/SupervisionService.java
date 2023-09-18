/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.gridsuite.study.server.utils.ComputationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Service
public class SupervisionService {
    private ReportService reportService;

    private LoadFlowService loadFlowService;

    private DynamicSimulationService dynamicSimulationService;

    private SecurityAnalysisService securityAnalysisService;

    private SensitivityAnalysisService sensitivityAnalysisService;

    private ShortCircuitService shortCircuitService;

    private VoltageInitService voltageInitService;

    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;

    public SupervisionService(NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository, ReportService reportService, LoadFlowService loadFlowService, DynamicSimulationService dynamicSimulationService, SecurityAnalysisService securityAnalysisService, SensitivityAnalysisService sensitivityAnalysisService, ShortCircuitService shortCircuitService, VoltageInitService voltageInitService) {
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.reportService = reportService;
        this.loadFlowService = loadFlowService;
        this.dynamicSimulationService = dynamicSimulationService;
        this.securityAnalysisService = securityAnalysisService;
        this.sensitivityAnalysisService = sensitivityAnalysisService;
        this.shortCircuitService = shortCircuitService;
        this.voltageInitService = voltageInitService;
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

        }
        return null;
    }

    public Integer deleteLoadflowResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByLoadFlowResultUuidNotNull();
        nodes.stream().forEach(node -> node.setLoadFlowResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.LOAD_FLOW.subReporterKey, nodes);
        reportService.deleteTreereports(subreportToDelete);
        loadFlowService.deleteLoadFlowResults();
        return nodes.size();
    }

    public Integer deleteDynamicSimulationResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByDynamicSimulationResultUuidNotNull();
        nodes.stream().forEach(node -> node.setShortCircuitAnalysisResultUuid(null));
        //TODO Add logs deletion once they are added
        dynamicSimulationService.deleteResults();
        return nodes.size();
    }

    public Integer deleteSecurityAnalysisResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySecurityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setSecurityAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SECURITY_ANALYSIS.subReporterKey, nodes);
        reportService.deleteTreereports(subreportToDelete);
        securityAnalysisService.deleteSecurityAnalysisResults();
        return nodes.size();
    }

    public Integer deleteSensitivityAnalysisResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllBySensitivityAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setSensitivityAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SENSITIVITY_ANALYSIS.subReporterKey, nodes);
        reportService.deleteTreereports(subreportToDelete);
        sensitivityAnalysisService.deleteSensitivityAnalysisResults();
        return nodes.size();
    }

    public Integer deleteShortcircuitResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByShortCircuitAnalysisResultUuidNotNull();
        nodes.stream().forEach(node -> node.setShortCircuitAnalysisResultUuid(null));
        Map<UUID, String> subreportToDelete = formatSubreportMap(ComputationType.SHORT_CIRCUIT.subReporterKey, nodes);
        reportService.deleteTreereports(subreportToDelete);
        shortCircuitService.deleteShortCircuitAnalysisResults();
        return nodes.size();
    }

    public Integer deleteVoltageInitResults() {
        List<NetworkModificationNodeInfoEntity> nodes = networkModificationNodeInfoRepository.findAllByVoltageInitResultUuidNotNull();
        nodes.stream().forEach(node -> node.setVoltageInitResultUuid(null));
        //TODO Add logs deletion once they are added
        voltageInitService.deleteVoltageInitResults();
        return nodes.size();
    }

    private Map<UUID, String> formatSubreportMap(String subReporterKey, List<NetworkModificationNodeInfoEntity> nodes) {
        return nodes.stream().map(node -> {
            String reportKey = node.getId() + "@" + subReporterKey;
            return Map.entry(node.getReportUuid(), reportKey);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}

