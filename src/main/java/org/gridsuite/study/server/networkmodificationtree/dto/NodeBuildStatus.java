/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode
public class NodeBuildStatus {

    public NodeBuildStatus(BuildStatus buildStatus) {
        this.localBuildStatus = buildStatus;
        this.globalBuildStatus = buildStatus;
    }

    /**
     * The global build status represents the state of all modifications from the root node to the current node (included) on the network
     */
    BuildStatus globalBuildStatus;

    /**
     * The local build status represents the state of this node own modifications on the network
     */
    BuildStatus localBuildStatus;

    @JsonIgnore
    public boolean isBuilt() {
        return globalBuildStatus.isBuilt() || localBuildStatus.isBuilt();
    }
}
