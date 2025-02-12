/**
 * Copyright (c) 2022 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@NoArgsConstructor
@Getter
@Setter
public class InvalidateNodeInfos {
    private UUID networkUuid;

    private List<UUID> reportUuids = new ArrayList<>();

    private List<String> variantIds = new ArrayList<>();

    private List<UUID> loadFlowResultUuids = new ArrayList<>();

    private List<UUID> securityAnalysisResultUuids = new ArrayList<>();

    private List<UUID> sensitivityAnalysisResultUuids = new ArrayList<>();
    private List<UUID> nonEvacuatedEnergyResultUuids = new ArrayList<>();

    private List<UUID> shortCircuitAnalysisResultUuids = new ArrayList<>();
    private List<UUID> oneBusShortCircuitAnalysisResultUuids = new ArrayList<>();

    private List<UUID> voltageInitResultUuids = new ArrayList<>();

    private List<UUID> dynamicSimulationResultUuids = new ArrayList<>();
    private List<UUID> dynamicSecurityAnalysisResultUuids = new ArrayList<>();

    private List<UUID> stateEstimationResultUuids = new ArrayList<>();

    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
    }

    public void addVariantId(String variantId) {
        variantIds.add(variantId);
    }

    public void addLoadFlowResultUuid(UUID loadFlowResultUuid) {
        getLoadFlowResultUuids().add(loadFlowResultUuid);
    }

    public void addSecurityAnalysisResultUuid(UUID securityAnalysisResultUuid) {
        securityAnalysisResultUuids.add(securityAnalysisResultUuid);
    }

    public void addSensitivityAnalysisResultUuid(UUID sensitivityAnalysisResultUuid) {
        sensitivityAnalysisResultUuids.add(sensitivityAnalysisResultUuid);
    }

    public void addNonEvacuatedEnergyResultUuid(UUID nonEvacuatedEnergyResultUuid) {
        nonEvacuatedEnergyResultUuids.add(nonEvacuatedEnergyResultUuid);
    }

    public void addShortCircuitAnalysisResultUuid(UUID shortCircuitAnalysisResultUuid) {
        shortCircuitAnalysisResultUuids.add(shortCircuitAnalysisResultUuid);
    }

    public void addOneBusShortCircuitAnalysisResultUuid(UUID oneBusShortCircuitAnalysisResultUuid) {
        oneBusShortCircuitAnalysisResultUuids.add(oneBusShortCircuitAnalysisResultUuid);
    }

    public void addVoltageInitResultUuid(UUID voltageInitResultUuid) {
        voltageInitResultUuids.add(voltageInitResultUuid);
    }

    public void addDynamicSimulationResultUuid(UUID dynamicSimulationResultUuid) {
        dynamicSimulationResultUuids.add(dynamicSimulationResultUuid);
    }

    public void addDynamicSecurityAnalysisResultUuid(UUID dynamicSecurityAnalysisResultUuid) {
        dynamicSecurityAnalysisResultUuids.add(dynamicSecurityAnalysisResultUuid);
    }

    public void addStateEstimationResultUuid(UUID stateEstimationResultUuid) {
        stateEstimationResultUuids.add(stateEstimationResultUuid);
    }
}
