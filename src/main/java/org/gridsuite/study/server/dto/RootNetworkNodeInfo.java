package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;

import java.util.Map;
import java.util.UUID;

@Builder
@Getter
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

    private UUID stateEstimationResultUuid;

    private NodeBuildStatus nodeBuildStatus;
}
