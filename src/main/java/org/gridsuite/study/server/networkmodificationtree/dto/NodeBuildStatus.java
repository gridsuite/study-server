/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class NodeBuildStatus {
    /**
     * The global build status represents the state of all modifications from the root node to the current node (included) on the network
     */
    private BuildStatus globalBuildStatus;

    /**
     * The local build status represents the state of this node own modifications on the network
     */
    private BuildStatus localBuildStatus;

    @JsonIgnore
    public boolean isBuilt() {
        return globalBuildStatus.isBuilt() || localBuildStatus.isBuilt();
    }

    public static NodeBuildStatus from(BuildStatus buildStatus) {
        return NodeBuildStatus.builder()
                .localBuildStatus(buildStatus)
                .globalBuildStatus(buildStatus)
                .build();
    }

    public static NodeBuildStatus from(BuildStatus localBuildStatus, BuildStatus globalBuildStatus) {
        return NodeBuildStatus.builder()
                .localBuildStatus(localBuildStatus)
                .globalBuildStatus(globalBuildStatus)
                .build();
    }

    public static NodeBuildStatus from(NetworkModificationResult.ApplicationStatus localApplicationStatus, NetworkModificationResult.ApplicationStatus globalApplicationStatus) {
        return NodeBuildStatus.builder()
                .localBuildStatus(BuildStatus.from(localApplicationStatus))
                .globalBuildStatus(BuildStatus.from(globalApplicationStatus))
                .build();
    }
}
