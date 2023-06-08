package org.gridsuite.study.server.networkmodificationtree.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode
public class NodeBuildStatus {

    public NodeBuildStatus(BuildStatus buildStatus) {
        this.buildStatusLocal = buildStatus;
        this.buildStatusGlobal = buildStatus;
    }

    /**
     * The global build status represents the state of all modifications from the root node to the current node (included) on the network
     */
    BuildStatus buildStatusGlobal;

    /**
     * The local build status represents the state of this node own modifications on the network
     */
    BuildStatus buildStatusLocal;

    public boolean isBuilt() {
        return buildStatusGlobal.isBuilt() && buildStatusLocal.isBuilt();
    }
}
