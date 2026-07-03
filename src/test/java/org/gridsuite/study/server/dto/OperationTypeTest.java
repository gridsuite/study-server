/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationTypeTest {

    @Test
    void shouldMapFromComputationTypeCorrectly() {
        assertEquals(OperationType.LOAD_FLOW, OperationType.mapFromComputationType(ComputationType.LOAD_FLOW));
        assertEquals(OperationType.SECURITY, OperationType.mapFromComputationType(ComputationType.SECURITY_ANALYSIS));
        assertEquals(OperationType.SENSITIVITY, OperationType.mapFromComputationType(ComputationType.SENSITIVITY_ANALYSIS));
        assertEquals(OperationType.VOLTAGE_INIT, OperationType.mapFromComputationType(ComputationType.VOLTAGE_INITIALIZATION));
        assertEquals(OperationType.DYNAMIC_SIMULATION, OperationType.mapFromComputationType(ComputationType.DYNAMIC_SIMULATION));
        assertEquals(OperationType.DYNAMIC_SECURITY, OperationType.mapFromComputationType(ComputationType.DYNAMIC_SECURITY_ANALYSIS));
        assertEquals(OperationType.DYNAMIC_MARGIN, OperationType.mapFromComputationType(ComputationType.DYNAMIC_MARGIN_CALCULATION));
        assertEquals(OperationType.STATE_ESTIMATION, OperationType.mapFromComputationType(ComputationType.STATE_ESTIMATION));
        assertEquals(OperationType.PCC_MIN, OperationType.mapFromComputationType(ComputationType.PCC_MIN));
        assertEquals(OperationType.SHORT_CIRCUIT, OperationType.mapFromComputationType(ComputationType.SHORT_CIRCUIT));
        assertEquals(OperationType.SHORT_CIRCUIT, OperationType.mapFromComputationType(ComputationType.SHORT_CIRCUIT_ONE_BUS));
    }
}
