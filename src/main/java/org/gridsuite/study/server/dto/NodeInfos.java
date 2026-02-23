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
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */

@NoArgsConstructor
@Getter
@Setter
public class NodeInfos {

    protected Set<UUID> reportUuids = new HashSet<>();
    protected Set<UUID> loadFlowResultUuids = new HashSet<>();
    protected Set<UUID> securityAnalysisResultUuids = new HashSet<>();
    protected Set<UUID> sensitivityAnalysisResultUuids = new HashSet<>();
    protected Set<UUID> shortCircuitAnalysisResultUuids = new HashSet<>();
    protected Set<UUID> oneBusShortCircuitAnalysisResultUuids = new HashSet<>();
    protected Set<UUID> voltageInitResultUuids = new HashSet<>();
    protected Set<UUID> dynamicSimulationResultUuids = new HashSet<>();
    protected Set<UUID> dynamicSecurityAnalysisResultUuids = new HashSet<>();
    protected Set<UUID> dynamicMarginCalculationResultUuids = new HashSet<>();
    protected Set<UUID> stateEstimationResultUuids = new HashSet<>();
    protected Set<UUID> pccMinResultUuids = new HashSet<>();

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

    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
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
