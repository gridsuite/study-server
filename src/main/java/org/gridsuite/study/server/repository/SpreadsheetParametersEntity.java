/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.Builder.Default;
import org.gridsuite.study.server.dto.SpreadsheetParameters;
import org.gridsuite.study.server.dto.SpreadsheetParameters.BranchSpreadsheetParameters;
import org.gridsuite.study.server.dto.SpreadsheetParameters.RegulatingEquipmentSpreadsheetParameters;

@Embeddable
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class SpreadsheetParametersEntity {
    @Column(name = "sp_load_branch_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadBranchOperationalLimitGroup = false;

    @Column(name = "sp_load_line_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadLineOperationalLimitGroup = false;

    @Column(name = "sp_load_twt_olg", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadTwtOperationalLimitGroup = false;

    @Column(name = "sp_load_generator_rt", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadGeneratorRegulatingTerminal = false;

    @Column(name = "sp_load_battery_rt", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadBatteryRegulatingTerminal = false;

    @Column(name = "sp_load_bus_nc", nullable = false, columnDefinition = "boolean default false")
    @Default
    private boolean spreadsheetLoadBusNetworkComponents = false;

    public SpreadsheetParameters toDto() {
        return new SpreadsheetParameters(
            new BranchSpreadsheetParameters(this.spreadsheetLoadBranchOperationalLimitGroup),
            new BranchSpreadsheetParameters(this.spreadsheetLoadLineOperationalLimitGroup),
            new BranchSpreadsheetParameters(this.spreadsheetLoadTwtOperationalLimitGroup),
            new RegulatingEquipmentSpreadsheetParameters(this.spreadsheetLoadGeneratorRegulatingTerminal),
            new RegulatingEquipmentSpreadsheetParameters(this.spreadsheetLoadBatteryRegulatingTerminal),
            new SpreadsheetParameters.BusSpreadsheetParameters(this.spreadsheetLoadBusNetworkComponents)
        );
    }

    /**
     * @return {@code true} if the update has modified the parameters, {@code false} otherwise.
     */
    public boolean update(@NonNull final SpreadsheetParameters dto) {
        boolean modified = false;
        final BranchSpreadsheetParameters branchParams = dto.getBranch();
        if (branchParams != null) {
            if (branchParams.getOperationalLimitsGroups() != null && this.spreadsheetLoadBranchOperationalLimitGroup != branchParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoadBranchOperationalLimitGroup = branchParams.getOperationalLimitsGroups();
            }
        }
        final BranchSpreadsheetParameters lineParams = dto.getLine();
        if (lineParams != null) {
            if (lineParams.getOperationalLimitsGroups() != null && this.spreadsheetLoadLineOperationalLimitGroup != lineParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoadLineOperationalLimitGroup = lineParams.getOperationalLimitsGroups();
            }
        }
        final BranchSpreadsheetParameters twtParams = dto.getTwt();
        if (twtParams != null) {
            if (twtParams.getOperationalLimitsGroups() != null && this.spreadsheetLoadTwtOperationalLimitGroup != twtParams.getOperationalLimitsGroups()) {
                modified = true;
                this.spreadsheetLoadTwtOperationalLimitGroup = twtParams.getOperationalLimitsGroups();
            }
        }
        final RegulatingEquipmentSpreadsheetParameters generatorParams = dto.getGenerator();
        if (generatorParams != null) {
            if (generatorParams.getRegulatingTerminal() != null && this.spreadsheetLoadGeneratorRegulatingTerminal != generatorParams.getRegulatingTerminal()) {
                modified = true;
                this.spreadsheetLoadGeneratorRegulatingTerminal = generatorParams.getRegulatingTerminal();
            }
        }
        final RegulatingEquipmentSpreadsheetParameters batteryParams = dto.getBattery();
        if (batteryParams != null) {
            if (batteryParams.getRegulatingTerminal() != null && this.spreadsheetLoadBatteryRegulatingTerminal != batteryParams.getRegulatingTerminal()) {
                modified = true;
                this.spreadsheetLoadBatteryRegulatingTerminal = batteryParams.getRegulatingTerminal();
            }
        }
        final SpreadsheetParameters.BusSpreadsheetParameters busParams = dto.getBus();
        if (busParams != null) {
            if (busParams.getNetworkComponents() != null && this.spreadsheetLoadBusNetworkComponents != busParams.getNetworkComponents()) {
                modified = true;
                this.spreadsheetLoadBusNetworkComponents = busParams.getNetworkComponents();
            }
        }
        return modified;
    }
}
