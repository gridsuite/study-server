package org.gridsuite.study.server.dto;

import lombok.Getter;
import org.gridsuite.study.server.notification.NotificationService;

@Getter
public enum ComputationType {
    LOAD_FLOW("Load flow", "loadFlowResultUuid",
            NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT,
            NotificationService.UPDATE_TYPE_LOADFLOW_FAILED),
    SECURITY_ANALYSIS("Security analysis", "securityAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT,
            NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED),
    SENSITIVITY_ANALYSIS("Sensitivity analysis", "sensitivityAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT,
            NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED),
    SHORT_CIRCUIT("Short circuit analysis", "shortCircuitAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT,
            NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED),
    VOLTAGE_INITIALIZATION("Voltage init", "voltageInitResultUuid",
            NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT,
            NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED),
    DYNAMIC_SIMULATION("Dynamic simulation", "dynamicSimulationResultUuid",
            NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT,
            NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED),
    DYNAMIC_SECURITY_ANALYSIS("Dynamic security analysis", "dynamicSecurityAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_STATUS, NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_RESULT,
            NotificationService.UPDATE_TYPE_DYNAMIC_SECURITY_ANALYSIS_FAILED),
    SHORT_CIRCUIT_ONE_BUS("One bus Short circuit analysis", "oneBusShortCircuitAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT,
            NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED),
    STATE_ESTIMATION("State estimation", "stateEstimationResultUuid",
        NotificationService.UPDATE_TYPE_STATE_ESTIMATION_STATUS, NotificationService.UPDATE_TYPE_STATE_ESTIMATION_RESULT,
        NotificationService.UPDATE_TYPE_STATE_ESTIMATION_FAILED);

    private final String label; // used for logs
    private final String resultUuidLabel;
    private final String updateStatusType;
    private final String updateResultType;
    private final String updateFailedType;

    ComputationType(String label, String resultUuidLabel, String updateStatusType, String updateResultType, String updateFailedType) {
        this.label = label;
        this.resultUuidLabel = resultUuidLabel;
        this.updateStatusType = updateStatusType;
        this.updateResultType = updateResultType;
        this.updateFailedType = updateFailedType;
    }
}
