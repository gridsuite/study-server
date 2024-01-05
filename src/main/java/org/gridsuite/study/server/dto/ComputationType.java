package org.gridsuite.study.server.dto;

import lombok.Getter;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType;

import static org.gridsuite.study.server.service.shortcircuit.ShortcircuitAnalysisType.ONE_BUS;

@Getter
public enum ComputationType {
    LOAD_FLOW("LoadFlow", "Load flow", "loadFlowResultUuid",
            NotificationService.UPDATE_TYPE_LOADFLOW_STATUS, NotificationService.UPDATE_TYPE_LOADFLOW_RESULT,
            NotificationService.UPDATE_TYPE_LOADFLOW_FAILED),
    SECURITY_ANALYSIS("SecurityAnalysis", "Security analysis", "securityAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_STATUS, NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_RESULT,
            NotificationService.UPDATE_TYPE_SECURITY_ANALYSIS_FAILED),
    SENSITIVITY_ANALYSIS("SensitivityAnalysis", "Sensitivity analysis", "sensitivityAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS, NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT,
            NotificationService.UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED),
    SHORT_CIRCUIT("ShortCircuitAnalysis", "Short circuit analysis", "shortCircuitAnalysisResultUuid",
            NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS, NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT,
            NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED, ONE_BUS),
    VOLTAGE_INITIALIZATION("", "Voltage init", "voltageInitResultUuid",
            NotificationService.UPDATE_TYPE_VOLTAGE_INIT_STATUS, NotificationService.UPDATE_TYPE_VOLTAGE_INIT_RESULT,
            NotificationService.UPDATE_TYPE_VOLTAGE_INIT_FAILED),
    DYNAMIC_SIMULATION("", "Dynamic simulation", "dynamicSimulationResultUuid",
            NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS, NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT,
            NotificationService.UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED);

    public final String subReporterKey;
    private final String label; // used for logs
    private String resultUuidLabel;
    private ShortcircuitAnalysisType oneBus; // only useful for SHORT_CIRCUIT
    private String updateStatusType;
    private String updateResultType;
    private String updateFailedType;

    public void setBusMode(ShortcircuitAnalysisType oneBus) {
        this.oneBus = oneBus;
        this.resultUuidLabel = this.oneBus == ONE_BUS ? "oneBusShortCircuitAnalysisResultUuid" : "shortCircuitAnalysisResultUuid";
        this.updateStatusType = this.oneBus == ONE_BUS ? NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS : NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_STATUS;
        this.updateResultType = this.oneBus == ONE_BUS ? NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT : NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_RESULT;
        this.updateFailedType = this.oneBus == ONE_BUS ? NotificationService.UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED : NotificationService.UPDATE_TYPE_SHORT_CIRCUIT_FAILED;
    }

    ComputationType(String subReporterKey, String label, String resultUuidLabel, String updateStatusType, String updateResultType, String updateFailedType) {
        this.subReporterKey = subReporterKey;
        this.label = label;
        this.resultUuidLabel = resultUuidLabel;
        this.updateStatusType = updateStatusType;
        this.updateResultType = updateResultType;
        this.updateFailedType = updateFailedType;
    }

    ComputationType(String subReporterKey, String label, String resultUuidLabel, String updateStatusType,
                    String updateResultType, String updateFailedType, ShortcircuitAnalysisType oneBus) {
        this(subReporterKey, label, resultUuidLabel, updateStatusType, updateResultType, updateFailedType);
        this.oneBus = oneBus;
    }
}
