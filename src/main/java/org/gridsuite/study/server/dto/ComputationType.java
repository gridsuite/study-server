package org.gridsuite.study.server.dto;

public enum ComputationType {
    LOAD_FLOW("LoadFlow", "Loadflow"),
    SECURITY_ANALYSIS("SecurityAnalysis", "Security analysis"),
    SENSITIVITY_ANALYSIS("SensitivityAnalysis", "Sensitivity analysis"),
    SHORT_CIRCUIT("ShortCircuitAnalysis", "Short circuit analysis"),
    VOLTAGE_INITIALIZATION("", "Voltage init"),
    DYNAMIC_SIMULATION("","Dynamic simulation");

    public final String subReporterKey;
    public final String label;

    ComputationType(String subReporterKey, String label) {
        this.subReporterKey = subReporterKey;
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
