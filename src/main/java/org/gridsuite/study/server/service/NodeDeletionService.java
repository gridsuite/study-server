package org.gridsuite.study.server.service;

import lombok.RequiredArgsConstructor;

import org.gridsuite.study.server.dto.NodeInfos;
import org.gridsuite.study.server.service.dynamicsecurityanalysis.DynamicSecurityAnalysisService;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationService;
import org.gridsuite.study.server.service.shortcircuit.ShortCircuitService;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class NodeDeletionService {

    private final StudyServerExecutionService studyServerExecutionService;
    private final ReportService reportService;
    private final LoadFlowService loadFlowService;
    private final SecurityAnalysisService securityAnalysisService;
    private final SensitivityAnalysisService sensitivityAnalysisService;
    private final ShortCircuitService shortCircuitService;
    private final VoltageInitService voltageInitService;
    private final DynamicSimulationService dynamicSimulationService;
    private final DynamicSecurityAnalysisService dynamicSecurityAnalysisService;
    private final StateEstimationService stateEstimationService;
    private final PccMinService pccMinService;

    // /!\ Do not wait completion and do not throw exception
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
            studyServerExecutionService.runAsync(() -> stateEstimationService.deleteStateEstimationResults(infos.getStateEstimationResultUuids())),
            studyServerExecutionService.runAsync(() -> pccMinService.deletePccMinResults(infos.getPccMinResultUuids()))
        );
    }
}
