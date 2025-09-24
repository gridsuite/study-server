/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import org.gridsuite.study.server.dto.SpreadsheetParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpreadsheetParametersEntityTest {

    @Test
    void testEntityCreationWithDefaultBuilder() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder().build();
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testEntityCreationWithBuilder() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadBranchOperationalLimitGroup(true)
            .spreadsheetLoadLineOperationalLimitGroup(true)
            .spreadsheetLoadTwtOperationalLimitGroup(true)
            .spreadsheetLoadGeneratorRegulatingTerminal(true)
            .spreadsheetLoadBusNetworkComponents(true)
            .build();
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testToDto() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadBranchOperationalLimitGroup(true)
            .spreadsheetLoadLineOperationalLimitGroup(true)
            .spreadsheetLoadTwtOperationalLimitGroup(true)
            .spreadsheetLoadGeneratorRegulatingTerminal(true)
            .spreadsheetLoadBusNetworkComponents(true)
            .build();

        SpreadsheetParameters spreadsheetParameters = spreadsheetParametersEntity.toDto();
        assertTrue(spreadsheetParameters.getBranch().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getLine().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getTwt().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getGenerator().getRegulatingTerminal());
        assertTrue(spreadsheetParameters.getBus().getNetworkComponents());
    }

    @Test
    void testUpdatePartial() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder().build();
        SpreadsheetParameters newParameters = SpreadsheetParameters.builder()
            .branch(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().regulatingTerminal(true).build())
            .build();

        spreadsheetParametersEntity.update(newParameters);
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testUpdateFull() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder().build();
        SpreadsheetParameters newParameters = SpreadsheetParameters.builder()
            .branch(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .line(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .twt(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().regulatingTerminal(true).build())
            .bus(SpreadsheetParameters.BusSpreadsheetParameters.builder().networkComponents(true).build())
            .build();

        spreadsheetParametersEntity.update(newParameters);
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }
}
