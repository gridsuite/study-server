/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;

import java.util.Map;
import java.util.UUID;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */
@Builder
@Getter
@Setter
public class RootNetworkNodeInfo {
    private UUID id;

    private String variantId;

    private Map<String, UUID> computationReports;

    private Map<UUID, UUID> modificationReports;

    private UUID shortCircuitAnalysisResultUuid;

    private UUID oneBusShortCircuitAnalysisResultUuid;

    private UUID loadFlowResultUuid;

    private Boolean loadFlowWithRatioTapChangers;

    private UUID voltageInitResultUuid;

    private UUID securityAnalysisResultUuid;

    private UUID sensitivityAnalysisResultUuid;

    private UUID dynamicSimulationResultUuid;

    private UUID dynamicSecurityAnalysisResultUuid;

    private UUID stateEstimationResultUuid;

    private UUID pccMinResultUuid;

    private NodeBuildStatus nodeBuildStatus;

    public RootNetworkNodeInfoEntity toEntity() {
        return RootNetworkNodeInfoEntity.builder()
            .id(id)
            .variantId(variantId)
            .computationReports(computationReports)
            .modificationReports(modificationReports)
            .shortCircuitAnalysisResultUuid(shortCircuitAnalysisResultUuid)
            .oneBusShortCircuitAnalysisResultUuid(oneBusShortCircuitAnalysisResultUuid)
            .loadFlowResultUuid(loadFlowResultUuid)
            .loadFlowWithRatioTapChangers(loadFlowWithRatioTapChangers)
            .voltageInitResultUuid(voltageInitResultUuid)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .dynamicSimulationResultUuid(dynamicSimulationResultUuid)
            .dynamicSecurityAnalysisResultUuid(dynamicSecurityAnalysisResultUuid)
            .stateEstimationResultUuid(stateEstimationResultUuid)
            .pccMinResultUuid(pccMinResultUuid)
            .nodeBuildStatus(nodeBuildStatus.toEntity())
            .build();
    }
}
