package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@SuperBuilder
@Getter
@NoArgsConstructor
@Setter
public class TimePointNetworkModificationNode extends AbstractTimePointNode {

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
}
