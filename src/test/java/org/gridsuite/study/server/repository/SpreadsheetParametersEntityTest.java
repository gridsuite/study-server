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
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadLineBus());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorBus());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBatteryRegulatingTerminal());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testEntityCreationWithBuilder() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadBranchOperationalLimitGroup(true)
            .spreadsheetLoadLineOperationalLimitGroup(true)
            .spreadsheetLoadLineBus(true)
            .spreadsheetLoadTwtOperationalLimitGroup(true)
            .spreadsheetLoadGeneratorRegulatingTerminal(true)
            .spreadsheetLoadGeneratorBus(true)
            .spreadsheetLoadBatteryRegulatingTerminal(true)
            .spreadsheetLoadBusNetworkComponents(true)
            .build();
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineBus());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorBus());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBatteryRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testToDto() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadBranchOperationalLimitGroup(true)
            .spreadsheetLoadLineOperationalLimitGroup(true)
            .spreadsheetLoadLineBus(true)
            .spreadsheetLoadTwtOperationalLimitGroup(true)
            .spreadsheetLoadGeneratorRegulatingTerminal(true)
            .spreadsheetLoadGeneratorBus(true)
            .spreadsheetLoadBatteryRegulatingTerminal(true)
            .spreadsheetLoadBusNetworkComponents(true)
            .build();

        SpreadsheetParameters spreadsheetParameters = spreadsheetParametersEntity.toDto();
        assertTrue(spreadsheetParameters.getBranch().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getLine().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getLine().getBus());
        assertTrue(spreadsheetParameters.getTwt().getOperationalLimitsGroups());
        assertTrue(spreadsheetParameters.getGenerator().getRegulatingTerminal());
        assertTrue(spreadsheetParameters.getGenerator().getBus());
        assertTrue(spreadsheetParameters.getBattery().getRegulatingTerminal());
        assertTrue(spreadsheetParameters.getBus().getNetworkComponents());
    }

    @Test
    void testUpdatePartial() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder().build();
        SpreadsheetParameters newParameters = SpreadsheetParameters.builder()
            .branch(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().regulatingTerminal(true).build())
            .battery(SpreadsheetParameters.RegulatingEquipmentSpreadsheetParameters.builder().regulatingTerminal(true).build())
            .build();

        spreadsheetParametersEntity.update(newParameters);
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadLineBus());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBatteryRegulatingTerminal());
        assertFalse(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
    }

    @Test
    void testUpdateFull() {
        SpreadsheetParametersEntity spreadsheetParametersEntity = SpreadsheetParametersEntity.builder().build();
        SpreadsheetParameters newParameters = SpreadsheetParameters.builder()
            .branch(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .line(SpreadsheetParameters.LineSpreadsheetParameters.builder().operationalLimitsGroups(true).bus(true).build())
            .twt(SpreadsheetParameters.BranchSpreadsheetParameters.builder().operationalLimitsGroups(true).build())
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().regulatingTerminal(true).bus(true).build())
            .battery(SpreadsheetParameters.RegulatingEquipmentSpreadsheetParameters.builder().regulatingTerminal(true).build())
            .bus(SpreadsheetParameters.BusSpreadsheetParameters.builder().networkComponents(true).build())
            .build();

        spreadsheetParametersEntity.update(newParameters);
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBranchOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadLineBus());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadTwtOperationalLimitGroup());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBatteryRegulatingTerminal());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadBusNetworkComponents());
        assertTrue(spreadsheetParametersEntity.isSpreadsheetLoadGeneratorBus());
    }

    @Test
    void testGeneratorBusPartialUpdates() {
        SpreadsheetParametersEntity entity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadGeneratorRegulatingTerminal(true)
            .build();
        SpreadsheetParameters enableBus = SpreadsheetParameters.builder()
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().bus(true).build())
            .build();
        assertTrue(entity.update(enableBus));
        assertTrue(entity.isSpreadsheetLoadGeneratorBus());
        assertTrue(entity.isSpreadsheetLoadGeneratorRegulatingTerminal());
        assertFalse(entity.update(enableBus));

        SpreadsheetParameters disableRegulatingTerminal = SpreadsheetParameters.builder()
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().regulatingTerminal(false).build())
            .build();
        assertTrue(entity.update(disableRegulatingTerminal));
        assertTrue(entity.isSpreadsheetLoadGeneratorBus());
        assertFalse(entity.isSpreadsheetLoadGeneratorRegulatingTerminal());

        SpreadsheetParameters disableBus = SpreadsheetParameters.builder()
            .generator(SpreadsheetParameters.GeneratorSpreadsheetParameters.builder().bus(false).build())
            .build();
        assertTrue(entity.update(disableBus));
        assertFalse(entity.isSpreadsheetLoadGeneratorBus());
        assertFalse(entity.update(disableBus));
    }

    @Test
    void testLineBusPartialUpdates() {
        SpreadsheetParametersEntity entity = SpreadsheetParametersEntity.builder()
            .spreadsheetLoadLineOperationalLimitGroup(true)
            .build();
        SpreadsheetParameters enableBus = SpreadsheetParameters.builder()
            .line(SpreadsheetParameters.LineSpreadsheetParameters.builder().bus(true).build())
            .build();
        assertTrue(entity.update(enableBus));
        assertTrue(entity.isSpreadsheetLoadLineBus());
        assertTrue(entity.isSpreadsheetLoadLineOperationalLimitGroup());
        assertFalse(entity.update(enableBus));

        SpreadsheetParameters disableOperationalLimitsGroups = SpreadsheetParameters.builder()
            .line(SpreadsheetParameters.LineSpreadsheetParameters.builder().operationalLimitsGroups(false).build())
            .build();
        assertTrue(entity.update(disableOperationalLimitsGroups));
        assertTrue(entity.isSpreadsheetLoadLineBus());
        assertFalse(entity.isSpreadsheetLoadLineOperationalLimitGroup());

        SpreadsheetParameters disableBus = SpreadsheetParameters.builder()
            .line(SpreadsheetParameters.LineSpreadsheetParameters.builder().bus(false).build())
            .build();
        assertTrue(entity.update(disableBus));
        assertFalse(entity.isSpreadsheetLoadLineBus());
        assertFalse(entity.update(disableBus));
    }
}
