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
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */
@NoArgsConstructor
public class InvalidateNodeInfos extends NodeInfos {
    @Getter
    @Setter
    private UUID networkUuid;

    private Set<UUID> nodeUuids = new HashSet<>();
    private Set<UUID> groupUuids = new HashSet<>();
    private Set<String> variantIds = new HashSet<>();

    public List<UUID> getNodeUuids() {
        return new ArrayList<>(nodeUuids);
    }

    public List<UUID> getGroupUuids() {
        return new ArrayList<>(groupUuids);
    }

    public void addNodeUuid(UUID nodeUuid) {
        nodeUuids.add(nodeUuid);
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

    public void add(InvalidateNodeInfos infos) {
        nodeUuids.addAll(infos.getNodeUuids());
        groupUuids.addAll(infos.getGroupUuids());

        reportUuids.addAll(infos.getReportUuids());
        variantIds.addAll(infos.getVariantIds());

        loadFlowResultUuids.addAll(infos.getLoadFlowResultUuids());
        securityAnalysisResultUuids.addAll(infos.getSecurityAnalysisResultUuids());
        sensitivityAnalysisResultUuids.addAll(infos.getSensitivityAnalysisResultUuids());
        shortCircuitAnalysisResultUuids.addAll(infos.getShortCircuitAnalysisResultUuids());
        oneBusShortCircuitAnalysisResultUuids.addAll(infos.getOneBusShortCircuitAnalysisResultUuids());
        voltageInitResultUuids.addAll(infos.getVoltageInitResultUuids());
        stateEstimationResultUuids.addAll(infos.getStateEstimationResultUuids());
        pccMinResultUuids.addAll(infos.getPccMinResultUuids());
        dynamicSimulationResultUuids.addAll(infos.getDynamicSimulationResultUuids());
        dynamicSecurityAnalysisResultUuids.addAll(infos.getDynamicSecurityAnalysisResultUuids());
    }
}
