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
public class InvalidateNodeInfos extends NodeInfos {
    @Getter
    @Setter
    private UUID networkUuid;

    private Set<UUID> nodeUuids = new HashSet<>();
    private Set<UUID> groupUuids = new HashSet<>();

    private Set<UUID> reportUuids = new HashSet<>();
    private Set<String> variantIds = new HashSet<>();

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

    public void addVariantId(String variantId) {
        variantIds.add(variantId);
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
