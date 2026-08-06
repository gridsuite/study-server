/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NODE_ACTIVITY_CONFLICT;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public final class NodeActivityRules {

    private NodeActivityRules() {
    }

    static void assertNoConflict(List<NodeActivityEntity> runningActivities, NodeActivityEntity requested,
                                 Map<UUID, Set<UUID>> ancestorsByNode) {
        for (NodeActivityEntity running : runningActivities) {
            if (sharesARootNetwork(running, requested)
                    && (isOnTheSameNode(running, requested)
                        || invalidates(running, requested, ancestorsByNode)
                        || invalidates(requested, running, ancestorsByNode))) {
                throw conflict(requested, running);
            }
        }
    }

    private static boolean sharesARootNetwork(NodeActivityEntity one, NodeActivityEntity other) {
        return one.getType().affectsAllRootNetworks() || other.getType().affectsAllRootNetworks()
            || one.getRootNetworkId().equals(other.getRootNetworkId());
    }

    private static boolean isOnTheSameNode(NodeActivityEntity one, NodeActivityEntity other) {
        return one.getNodeId().equals(other.getNodeId());
    }

    private static boolean invalidates(NodeActivityEntity activity, NodeActivityEntity other,
                                       Map<UUID, Set<UUID>> ancestorsByNode) {
        return activity.getType().invalidatesChildren()
            && ancestorsByNode.get(other.getNodeId()).contains(activity.getNodeId());
    }

    private static StudyException conflict(NodeActivityEntity requested, NodeActivityEntity running) {
        return new StudyException(NODE_ACTIVITY_CONFLICT, "%s on node %s refused: %s is running on node %s"
            .formatted(requested.getType(), requested.getNodeId(), running.getType(), running.getNodeId()));
    }
}
