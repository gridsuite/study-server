/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode(callSuper = true)
public class NetworkModificationNode extends AbstractNode {
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // Only for tests. Need to replace by @JsonIgnore when all tests are rewritten without the variantID to identify a test in the MockWebServer
    private UUID modificationGroupUuid;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // Only for tests. Need to replace by @JsonIgnore when all tests are rewritten without the variantID to identify a test in the MockWebServer
    private String variantId;

    @Builder.Default
    private Set<UUID> modificationsToExclude = new HashSet<>();

    private UUID loadFlowResultUuid;

    private UUID shortCircuitAnalysisResultUuid;

    private UUID oneBusShortCircuitAnalysisResultUuid;

    private UUID voltageInitResultUuid;

    private UUID securityAnalysisResultUuid;

    private UUID sensitivityAnalysisResultUuid;

    private UUID nonEvacuatedEnergyResultUuid;

    private UUID dynamicSimulationResultUuid;

    private UUID stateEstimationResultUuid;

    private NodeBuildStatus nodeBuildStatus;

    //TODO: temporary, used to keep nodeDTO identical since we don't export timepoints in APIs yet, once timepoints are exported, result uuid won't be stored in nodeDto
    public static NetworkModificationNode completeDtoFromTimePointNodeInfo(NetworkModificationNode networkModificationNode, TimePointNodeInfoEntity timePointNodeInfoEntity) {
        networkModificationNode.setModificationsToExclude(timePointNodeInfoEntity.getModificationsToExclude());
        networkModificationNode.setLoadFlowResultUuid(timePointNodeInfoEntity.getLoadFlowResultUuid());
        networkModificationNode.setShortCircuitAnalysisResultUuid(timePointNodeInfoEntity.getShortCircuitAnalysisResultUuid());
        networkModificationNode.setOneBusShortCircuitAnalysisResultUuid(timePointNodeInfoEntity.getOneBusShortCircuitAnalysisResultUuid());
        networkModificationNode.setVoltageInitResultUuid(timePointNodeInfoEntity.getVoltageInitResultUuid());
        networkModificationNode.setSecurityAnalysisResultUuid(timePointNodeInfoEntity.getSecurityAnalysisResultUuid());
        networkModificationNode.setSensitivityAnalysisResultUuid(timePointNodeInfoEntity.getSensitivityAnalysisResultUuid());
        networkModificationNode.setNonEvacuatedEnergyResultUuid(timePointNodeInfoEntity.getNonEvacuatedEnergyResultUuid());
        networkModificationNode.setDynamicSimulationResultUuid(timePointNodeInfoEntity.getDynamicSimulationResultUuid());
        networkModificationNode.setStateEstimationResultUuid(timePointNodeInfoEntity.getStateEstimationResultUuid());
        networkModificationNode.setNodeBuildStatus(timePointNodeInfoEntity.getNodeBuildStatus().toDto());
        networkModificationNode.setReportUuid(timePointNodeInfoEntity.getReportUuid());
        return networkModificationNode;
    }

    @Override
    public NodeType getType() {
        return NodeType.NETWORK_MODIFICATION;
    }
}
