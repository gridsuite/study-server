/**
 * Copyright (c) 2026 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;

import org.gridsuite.study.server.dto.NodeInfos;
import org.gridsuite.study.server.service.dynamicmargincalculation.DynamicMarginCalculationService;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class RemoteNodeInfosDeletionService {

    private final StudyServerExecutionService studyServerExecutionService;
    private final ReportService reportService;
    private final LoadFlowService loadFlowService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final DynamicSimulationService dynamicSimulationService;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    private final DynamicMarginCalculationService dynamicMarginCalculationService;
    private final StateEstimationService stateEstimationService;
    private final PccMinService pccMinService;

    public CompletableFuture<Void> delete(NodeInfos infos) {

        return CompletableFuture.allOf(
                studyServerExecutionService.runAsync(() -> reportService.deleteReports(infos.getReportUuids())),
                studyServerExecutionService.runAsync(() -> loadFlowService.deleteLoadFlowResults(infos.getLoadFlowResultUuids())),
                studyServerExecutionService.runAsync(() -> securityAnalysisService.deleteSecurityAnalysisResults(infos.getSecurityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> sensitivityAnalysisService.deleteSensitivityAnalysisResults(infos.getSensitivityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(infos.getShortCircuitAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> shortCircuitService.deleteShortCircuitAnalysisResults(infos.getOneBusShortCircuitAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> voltageInitService.deleteVoltageInitResults(infos.getVoltageInitResultUuids())),
                studyServerExecutionService.runAsync(() -> dynamicSimulationService.deleteResults(infos.getDynamicSimulationResultUuids())),
                studyServerExecutionService.runAsync(() -> dynamicSecurityAnalysisService.deleteResults(infos.getDynamicSecurityAnalysisResultUuids())),
                studyServerExecutionService.runAsync(() -> dynamicMarginCalculationService.deleteResults(infos.getDynamicMarginCalculationResultUuids())),
                studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(infos.getStateEstimationResultUuids())),
                studyServerExecutionService.runAsync(() -> pccMinService.deletePccMinResults(infos.getPccMinResultUuids()))
        );
    }
}
