package org.gridsuite.study.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.*;

/**
 * Common base class for DTOs that track analysis results, reports, and variants.
 */

@NoArgsConstructor
@Getter
@Setter
@SuperBuilder
public class NodeInfos {

    protected List<UUID> reportUuids = new ArrayList<>();
    protected List<UUID> loadFlowResultUuids = new ArrayList<>();
    protected List<UUID> securityAnalysisResultUuids = new ArrayList<>();
    protected List<UUID> sensitivityAnalysisResultUuids = new ArrayList<>();
    protected List<UUID> shortCircuitAnalysisResultUuids = new ArrayList<>();
    protected List<UUID> oneBusShortCircuitAnalysisResultUuids = new ArrayList<>();
    protected List<UUID> voltageInitResultUuids = new ArrayList<>();
    protected List<UUID> stateEstimationResultUuids = new ArrayList<>();
    protected List<UUID> pccMinResultUuids = new ArrayList<>();
    protected List<UUID> dynamicSimulationResultUuids = new ArrayList<>();
    protected List<UUID> dynamicSecurityAnalysisResultUuids = new ArrayList<>();

    public List<UUID> getReportUuids() {
        return new ArrayList<>(reportUuids);
    }

    public List<UUID> getLoadFlowResultUuids() {
        return new ArrayList<>(loadFlowResultUuids);
    }

    public List<UUID> getSecurityAnalysisResultUuids() {
        return new ArrayList<>(securityAnalysisResultUuids);
    }

    public List<UUID> getSensitivityAnalysisResultUuids() {
        return new ArrayList<>(sensitivityAnalysisResultUuids);
    }

    public List<UUID> getShortCircuitAnalysisResultUuids() {
        return new ArrayList<>(shortCircuitAnalysisResultUuids);
    }

    public List<UUID> getOneBusShortCircuitAnalysisResultUuids() {
        return new ArrayList<>(oneBusShortCircuitAnalysisResultUuids);
    }

    public List<UUID> getVoltageInitResultUuids() {
        return new ArrayList<>(voltageInitResultUuids);
    }

    public List<UUID> getStateEstimationResultUuids() {
        return new ArrayList<>(stateEstimationResultUuids);
    }

    public List<UUID> getPccMinResultUuids() {
        return new ArrayList<>(pccMinResultUuids);
    }

    public List<UUID> getDynamicSimulationResultUuids() {
        return new ArrayList<>(dynamicSimulationResultUuids);
    }

    public List<UUID> getDynamicSecurityAnalysisResultUuids() {
        return new ArrayList<>(dynamicSecurityAnalysisResultUuids);
    }

    // Adders
    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
    }

    public void addLoadFlowResultUuid(UUID uuid) {
        loadFlowResultUuids.add(uuid);
    }

    public void addSecurityAnalysisResultUuid(UUID uuid) {
        securityAnalysisResultUuids.add(uuid);
    }

    public void addSensitivityAnalysisResultUuid(UUID uuid) {
        sensitivityAnalysisResultUuids.add(uuid);
    }

    public void addShortCircuitAnalysisResultUuid(UUID uuid) {
        shortCircuitAnalysisResultUuids.add(uuid);
    }

    public void addOneBusShortCircuitAnalysisResultUuid(UUID uuid) {
        oneBusShortCircuitAnalysisResultUuids.add(uuid);
    }

    public void addVoltageInitResultUuid(UUID uuid) {
        voltageInitResultUuids.add(uuid);
    }

    public void addStateEstimationResultUuid(UUID uuid) {
        stateEstimationResultUuids.add(uuid);
    }

    public void addPccMinResultUuid(UUID uuid) {
        pccMinResultUuids.add(uuid);
    }

    public void addDynamicSimulationResultUuid(UUID uuid) {
        dynamicSimulationResultUuids.add(uuid);
    }

    public void addDynamicSecurityAnalysisResultUuid(UUID uuid) {
        dynamicSecurityAnalysisResultUuids.add(uuid);
    }
}
