/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
public enum OperationType {
    CASES,
    BUILD,
    LOAD_FLOW,
    SECURITY,
    SENSITIVITY,
    SHORT_CIRCUIT,
    VOLTAGE_INIT,
    PCC_MIN,
    STATE_ESTIMATION,
    BALANCE_ADJUSTEMENT,
    DYNAMIC_SIMULATION,
    DYNAMIC_SECURITY,
    DYNAMIC_MARGIN;

    public static OperationType mapFromComputationType(ComputationType computationType) {
        return switch (computationType) {
            case LOAD_FLOW -> OperationType.LOAD_FLOW;
            case SECURITY_ANALYSIS -> OperationType.SECURITY;
            case SENSITIVITY_ANALYSIS -> OperationType.SENSITIVITY;
            case VOLTAGE_INITIALIZATION -> OperationType.VOLTAGE_INIT;
            case DYNAMIC_SIMULATION -> OperationType.DYNAMIC_SIMULATION;
            case DYNAMIC_SECURITY_ANALYSIS -> OperationType.DYNAMIC_SECURITY;
            case DYNAMIC_MARGIN_CALCULATION -> OperationType.DYNAMIC_MARGIN;
            case STATE_ESTIMATION -> OperationType.STATE_ESTIMATION;
            case PCC_MIN -> OperationType.PCC_MIN;
            case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS -> OperationType.SHORT_CIRCUIT;
        };
    }
}
