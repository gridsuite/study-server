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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@NoArgsConstructor
public class InvalidateNodeInfos {
    @Getter
    @Setter
    private UUID networkUuid;

    private Set<UUID> nodeUuids = new HashSet<>();
    private Set<UUID> groupUuids = new HashSet<>();

    private Set<UUID> reportUuids = new HashSet<>();
    private Set<String> variantIds = new HashSet<>();

    private Set<UUID> loadFlowResultUuids = new HashSet<>();
    private Set<UUID> securityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> sensitivityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> shortCircuitAnalysisResultUuids = new HashSet<>();
    private Set<UUID> oneBusShortCircuitAnalysisResultUuids = new HashSet<>();
    private Set<UUID> voltageInitResultUuids = new HashSet<>();
    private Set<UUID> stateEstimationResultUuids = new HashSet<>();
    private Set<UUID> pccMinResultUuids = new HashSet<>();
    private Set<UUID> dynamicSimulationResultUuids = new HashSet<>();
    private Set<UUID> dynamicSecurityAnalysisResultUuids = new HashSet<>();
    private Set<UUID> dynamicMarginCalculationResultUuids = new HashSet<>();

    public List<UUID> getNodeUuids() {
        return nodeUuids.stream().toList();
    }

    public List<UUID> getGroupUuids() {
        return groupUuids.stream().toList();
    }

    public List<UUID> getReportUuids() {
        return reportUuids.stream().toList();
    }

    public List<String> getVariantIds() {
        return variantIds.stream().toList();
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

    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
    }

    public void addVariantId(String variantId) {
        variantIds.add(variantId);
    }

    public void addLoadFlowResultUuid(UUID loadFlowResultUuid) {
        loadFlowResultUuids.add(loadFlowResultUuid);
    }

    public void addSecurityAnalysisResultUuid(UUID securityAnalysisResultUuid) {
        securityAnalysisResultUuids.add(securityAnalysisResultUuid);
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

    public void addDynamicSimulationResultUuid(UUID dynamicSimulationResultUuid) {
        dynamicSimulationResultUuids.add(dynamicSimulationResultUuid);
    }

    public void addDynamicSecurityAnalysisResultUuid(UUID dynamicSecurityAnalysisResultUuid) {
        dynamicSecurityAnalysisResultUuids.add(dynamicSecurityAnalysisResultUuid);
    }

    public void addDynamicMarginCalculationResultUuid(UUID dynamicMarginCalculationResultUuid) {
        dynamicMarginCalculationResultUuids.add(dynamicMarginCalculationResultUuid);
    }

    public void addStateEstimationResultUuid(UUID stateEstimationResultUuid) {
        stateEstimationResultUuids.add(stateEstimationResultUuid);
    }

    public void addPccMinResultUuid(UUID pccMinResultUuid) {
        pccMinResultUuids.add(pccMinResultUuid);
    }

    public void addGroupUuids(List<UUID> groupUuids) {
        this.groupUuids.addAll(groupUuids);
    }

    public void addNodeUuid(UUID nodeUuid) {
        this.nodeUuids.add(nodeUuid);
    }

    public void add(InvalidateNodeInfos invalidateNodeInfos) {
        nodeUuids.addAll(invalidateNodeInfos.getNodeUuids());
        groupUuids.addAll(invalidateNodeInfos.getGroupUuids());

        reportUuids.addAll(invalidateNodeInfos.getReportUuids());
        variantIds.addAll(invalidateNodeInfos.getVariantIds());

        loadFlowResultUuids.addAll(invalidateNodeInfos.getLoadFlowResultUuids());
        securityAnalysisResultUuids.addAll(invalidateNodeInfos.getSecurityAnalysisResultUuids());
        sensitivityAnalysisResultUuids.addAll(invalidateNodeInfos.getSensitivityAnalysisResultUuids());
        shortCircuitAnalysisResultUuids.addAll(invalidateNodeInfos.getShortCircuitAnalysisResultUuids());
        oneBusShortCircuitAnalysisResultUuids.addAll(invalidateNodeInfos.getOneBusShortCircuitAnalysisResultUuids());
        voltageInitResultUuids.addAll(invalidateNodeInfos.getVoltageInitResultUuids());
        stateEstimationResultUuids.addAll(invalidateNodeInfos.getStateEstimationResultUuids());
        pccMinResultUuids.addAll(invalidateNodeInfos.getPccMinResultUuids());
        dynamicSimulationResultUuids.addAll(invalidateNodeInfos.getDynamicSimulationResultUuids());
        dynamicSecurityAnalysisResultUuids.addAll(invalidateNodeInfos.getDynamicSecurityAnalysisResultUuids());
        dynamicMarginCalculationResultUuids.addAll(invalidateNodeInfos.getDynamicMarginCalculationResultUuids());
    }
}
