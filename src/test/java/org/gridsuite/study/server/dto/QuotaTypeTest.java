/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Ghiles Abdellah {@literal <ghiles.abdellah at rte-france.com>}
 */
class QuotaTypeTest {

    @Test
    void shouldMapFromComputationTypeCorrectly() {
        assertEquals(QuotaType.LOAD_FLOW, QuotaType.mapFromComputationType(ComputationType.LOAD_FLOW));
        assertEquals(QuotaType.SECURITY_ANALYSIS, QuotaType.mapFromComputationType(ComputationType.SECURITY_ANALYSIS));
        assertEquals(QuotaType.SENSITIVITY_ANALYSIS, QuotaType.mapFromComputationType(ComputationType.SENSITIVITY_ANALYSIS));
        assertEquals(QuotaType.VOLTAGE_INITIALIZATION, QuotaType.mapFromComputationType(ComputationType.VOLTAGE_INITIALIZATION));
        assertEquals(QuotaType.DYNAMIC_SIMULATION, QuotaType.mapFromComputationType(ComputationType.DYNAMIC_SIMULATION));
        assertEquals(QuotaType.DYNAMIC_SECURITY_ANALYSIS, QuotaType.mapFromComputationType(ComputationType.DYNAMIC_SECURITY_ANALYSIS));
        assertEquals(QuotaType.DYNAMIC_MARGIN_CALCULATION, QuotaType.mapFromComputationType(ComputationType.DYNAMIC_MARGIN_CALCULATION));
        assertEquals(QuotaType.STATE_ESTIMATION, QuotaType.mapFromComputationType(ComputationType.STATE_ESTIMATION));
        assertEquals(QuotaType.PCC_MIN, QuotaType.mapFromComputationType(ComputationType.PCC_MIN));
        assertEquals(QuotaType.SHORT_CIRCUIT, QuotaType.mapFromComputationType(ComputationType.SHORT_CIRCUIT));
        assertEquals(QuotaType.SHORT_CIRCUIT, QuotaType.mapFromComputationType(ComputationType.SHORT_CIRCUIT_ONE_BUS));
    }
}
