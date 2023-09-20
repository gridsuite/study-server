package org.gridsuite.study.server.dto;

public enum ComputationType {
    LOAD_FLOW("LoadFlow"),
    SECURITY_ANALYSIS("SecurityAnalysis"),
    SENSITIVITY_ANALYSIS("SensitivityAnalysis"),
    SHORT_CIRCUIT("ShortCircuitAnalysis"),
    VOLTAGE_INITIALIZATION(""),
    DYNAMIC_SIMULATION("");

    public final String subReporterKey;

    ComputationType(String subReporterKey) {
        this.subReporterKey = subReporterKey;
    }
}
