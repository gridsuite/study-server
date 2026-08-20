/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public final class NodeActivityRules {

    private NodeActivityRules() {
    }

    static Optional<NodeActivityEntity> findActivityConflict(List<NodeActivityEntity> runningActivities,
                                                             NodeActivityEntity requested,
                                                             Map<UUID, Set<UUID>> ancestorsByNode) {
        return runningActivities.stream()
            .filter(
                running -> hasCommonRootNetwork(running, requested)
                && (isSameNode(running, requested)
                    || invalidates(running, requested, ancestorsByNode)
                    || invalidates(requested, running, ancestorsByNode)))
            .findFirst();
    }

    private static boolean hasCommonRootNetwork(NodeActivityEntity one, NodeActivityEntity other) {
        return one.getType().affectsAllRootNetworks() || other.getType().affectsAllRootNetworks()
            || one.getRootNetworkId().equals(other.getRootNetworkId());
    }

    private static boolean isSameNode(NodeActivityEntity one, NodeActivityEntity other) {
        return one.getNodeId().equals(other.getNodeId());
    }

    private static boolean invalidates(NodeActivityEntity activity, NodeActivityEntity other,
                                       Map<UUID, Set<UUID>> ancestorsByNode) {
        return activity.getType().invalidatesChildren()
            && ancestorsByNode.getOrDefault(other.getNodeId(), Set.of()).contains(activity.getNodeId());
    }
}
