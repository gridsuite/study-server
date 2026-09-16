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
public enum QuotaType {
    CASES,
    BUILD,
    LOAD_FLOW,
    SECURITY_ANALYSIS,
    SENSITIVITY_ANALYSIS,
    SHORT_CIRCUIT,
    VOLTAGE_INITIALIZATION,
    PCC_MIN,
    ASYMMETRICAL_LOAD,
    STATE_ESTIMATION,
    BALANCE_ADJUSTMENT,
    DYNAMIC_SIMULATION,
    DYNAMIC_SECURITY_ANALYSIS,
    DYNAMIC_MARGIN_CALCULATION;

    public static QuotaType mapFromComputationType(ComputationType computationType) {
        return switch (computationType) {
            case LOAD_FLOW -> QuotaType.LOAD_FLOW;
            case SECURITY_ANALYSIS -> QuotaType.SECURITY_ANALYSIS;
            case SENSITIVITY_ANALYSIS -> QuotaType.SENSITIVITY_ANALYSIS;
            case VOLTAGE_INITIALIZATION -> QuotaType.VOLTAGE_INITIALIZATION;
            case DYNAMIC_SIMULATION -> QuotaType.DYNAMIC_SIMULATION;
            case DYNAMIC_SECURITY_ANALYSIS -> QuotaType.DYNAMIC_SECURITY_ANALYSIS;
            case DYNAMIC_MARGIN_CALCULATION -> QuotaType.DYNAMIC_MARGIN_CALCULATION;
            case STATE_ESTIMATION -> QuotaType.STATE_ESTIMATION;
            case PCC_MIN -> QuotaType.PCC_MIN;
            case SHORT_CIRCUIT, SHORT_CIRCUIT_ONE_BUS -> QuotaType.SHORT_CIRCUIT;
            case ASYMMETRICAL_LOAD -> QuotaType.ASYMMETRICAL_LOAD;
        };
    }
}
