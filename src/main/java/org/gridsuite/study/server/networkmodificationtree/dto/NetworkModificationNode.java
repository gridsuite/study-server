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

    private UUID voltageInitResultUuid;

    private UUID securityAnalysisResultUuid;

    private UUID sensitivityAnalysisResultUuid;

    private UUID dynamicSimulationResultUuid;

    private NodeBuildStatus nodeBuildStatus;

    @Override
    public NodeType getType() {
        return NodeType.NETWORK_MODIFICATION;
    }
}
