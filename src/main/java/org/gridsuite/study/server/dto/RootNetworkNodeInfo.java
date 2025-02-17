package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;

import java.util.Map;
import java.util.UUID;

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

    private UUID voltageInitResultUuid;

    private UUID securityAnalysisResultUuid;

    private UUID sensitivityAnalysisResultUuid;

    private UUID nonEvacuatedEnergyResultUuid;

    private UUID dynamicSimulationResultUuid;

    private UUID dynamicSecurityAnalysisResultUuid;

    private UUID stateEstimationResultUuid;

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
            .voltageInitResultUuid(voltageInitResultUuid)
            .securityAnalysisResultUuid(securityAnalysisResultUuid)
            .sensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid)
            .nonEvacuatedEnergyResultUuid(nonEvacuatedEnergyResultUuid)
            .dynamicSimulationResultUuid(dynamicSimulationResultUuid)
            .dynamicSecurityAnalysisResultUuid(dynamicSecurityAnalysisResultUuid)
            .stateEstimationResultUuid(stateEstimationResultUuid)
            .nodeBuildStatus(nodeBuildStatus.toEntity())
            .build();
    }
}
