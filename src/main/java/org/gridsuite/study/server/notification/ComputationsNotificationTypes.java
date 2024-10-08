/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.notification;

/**
 * @author Mathieu Deharbe <mathieu.deharbe_externe at rte-france.com
 */
public final class ComputationsNotificationTypes {
    private ComputationsNotificationTypes() {
        // this class should never be constructed
    }

    public static final String UPDATE_TYPE_LOADFLOW_STATUS = "loadflow_status";
    public static final String UPDATE_TYPE_LOADFLOW_RESULT = "loadflowResult";
    public static final String UPDATE_TYPE_LOADFLOW_FAILED = "loadflow_failed";

    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_STATUS = "securityAnalysis_status";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_RESULT = "securityAnalysisResult";
    public static final String UPDATE_TYPE_SECURITY_ANALYSIS_FAILED = "securityAnalysis_failed";

    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_STATUS = "sensitivityAnalysis_status";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_RESULT = "sensitivityAnalysisResult";
    public static final String UPDATE_TYPE_SENSITIVITY_ANALYSIS_FAILED = "sensitivityAnalysis_failed";

    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_STATUS = "nonEvacuatedEnergy_status";
    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_RESULT = "nonEvacuatedEnergyResult";
    public static final String UPDATE_TYPE_NON_EVACUATED_ENERGY_FAILED = "nonEvacuatedEnergy_failed";

    public static final String UPDATE_TYPE_SHORT_CIRCUIT_STATUS = "shortCircuitAnalysis_status";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_RESULT = "shortCircuitAnalysisResult";
    public static final String UPDATE_TYPE_SHORT_CIRCUIT_FAILED = "shortCircuitAnalysis_failed";

    public static final String UPDATE_TYPE_VOLTAGE_INIT_STATUS = "voltageInit_status";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_RESULT = "voltageInitResult";
    public static final String UPDATE_TYPE_VOLTAGE_INIT_FAILED = "voltageInit_failed";

    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_STATUS = "dynamicSimulation_status";
    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_RESULT = "dynamicSimulationResult";
    public static final String UPDATE_TYPE_DYNAMIC_SIMULATION_FAILED = "dynamicSimulation_failed";

    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_STATUS = "oneBusShortCircuitAnalysis_status";
    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_RESULT = "oneBusShortCircuitAnalysisResult";
    public static final String UPDATE_TYPE_ONE_BUS_SHORT_CIRCUIT_FAILED = "oneBusShortCircuitAnalysis_failed";

    public static final String UPDATE_TYPE_STATE_ESTIMATION_STATUS = "stateEstimation_status";
    public static final String UPDATE_TYPE_STATE_ESTIMATION_RESULT = "stateEstimationResult";
    public static final String UPDATE_TYPE_STATE_ESTIMATION_FAILED = "stateEstimation_failed";
}
