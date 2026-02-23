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

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */

@NoArgsConstructor
@Getter
@Setter
public class DeleteNodeInfos {
    private List<UUID> removedNodeUuids = new ArrayList<>();

    private List<UUID> modificationGroupUuids = new ArrayList<>();

    private Map<UUID, List<String>> variantIds = new HashMap<>();

    private Set<UUID> reportUuids = new HashSet<>();
    private Set<UUID> loadFlowResultUuids = new HashSet<>();
    private Set<UUID> securityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> sensitivityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> shortCircuitAnalysisResultUuids = new HashSet<>();
    private Set<UUID> oneBusShortCircuitAnalysisResultUuids = new HashSet<>();
    private Set<UUID> voltageInitResultUuids = new HashSet<>();
    private Set<UUID> dynamicSimulationResultUuids = new HashSet<>();
    private Set<UUID> dynamicSecurityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> dynamicMarginCalculationResultUuids = new HashSet<>();
    private Set<UUID> stateEstimationResultUuids = new HashSet<>();
    private Set<UUID> pccMinResultUuids = new HashSet<>();

    public List<UUID> getReportUuids() {
        return reportUuids.stream().toList();
    }

    public List<UUID> getLoadFlowResultUuids() {
        return loadFlowResultUuids.stream().toList();
    }

    public List<UUID> getSecurityAnalysisResultUuids() {
        return securityAnalysisResultUuids.stream().toList();
    }

    public List<UUID> getSensitivityAnalysisResultUuids() {
        return sensitivityAnalysisResultUuids.stream().toList();
    }

    public List<UUID> getShortCircuitAnalysisResultUuids() {
        return shortCircuitAnalysisResultUuids.stream().toList();
    }

    public List<UUID> getOneBusShortCircuitAnalysisResultUuids() {
        return oneBusShortCircuitAnalysisResultUuids.stream().toList();
    }

    public List<UUID> getVoltageInitResultUuids() {
        return voltageInitResultUuids.stream().toList();
    }

    public List<UUID> getStateEstimationResultUuids() {
        return stateEstimationResultUuids.stream().toList();
    }

    public List<UUID> getPccMinResultUuids() {
        return pccMinResultUuids.stream().toList();
    }

    public List<UUID> getDynamicSimulationResultUuids() {
        return dynamicSimulationResultUuids.stream().toList();
    }

    public List<UUID> getDynamicSecurityAnalysisResultUuids() {
        return dynamicSecurityAnalysisResultUuids.stream().toList();
    }

    public List<UUID> getDynamicMarginCalculationResultUuids() {
        return dynamicMarginCalculationResultUuids.stream().toList();
    }

    public void addRemovedNodeUuid(UUID removedNodeUuid) {
        removedNodeUuids.add(removedNodeUuid);
    }

    public void addModificationGroupUuid(UUID modificationGroupUuid) {
        modificationGroupUuids.add(modificationGroupUuid);
    }

    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
    }

    public void addVariantId(UUID networkUuid, String variantId) {
        variantIds.getOrDefault(networkUuid, new ArrayList<>()).add(variantId);
    }

    public void addLoadFlowResultUuid(UUID loadFlowResultUuid) {
        loadFlowResultUuids.add(loadFlowResultUuid);
    }

    public void addSecurityAnalysisResultUuid(UUID securityAnalysisResultUuid) {
        securityAnalysisResultUuids.add(securityAnalysisResultUuid);
    }

    public void addDynamicSimulationResultUuid(UUID dynamicSimulationResultUuid) {
        dynamicSimulationResultUuids.add(dynamicSimulationResultUuid);
    }

    public void addDynamicSecurityAnalysisResultUuid(UUID dynamicSecurityAnalysisResultUuid) {
        dynamicSecurityAnalysisResultUuids.add(dynamicSecurityAnalysisResultUuid);
    }

    public void addDynamicMarginCalculationResultUuid(UUID dynamicMarginCalculationResultUuid) {
        dynamicMarginCalculationResultUuids.add(dynamicMarginCalculationResultUuid);
    }

    public void addSensitivityAnalysisResultUuid(UUID sensitivityAnalysisResultUuid) {
        sensitivityAnalysisResultUuids.add(sensitivityAnalysisResultUuid);
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

    public void addStateEstimationResultUuid(UUID stateEstimationResultUuid) {
        stateEstimationResultUuids.add(stateEstimationResultUuid);
    }

    public void addPccMinResultUuid(UUID pccMinResultUuid) {
        pccMinResultUuids.add(pccMinResultUuid);
    }

}
