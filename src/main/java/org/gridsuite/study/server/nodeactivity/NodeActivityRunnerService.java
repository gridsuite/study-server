/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityRunnerService {

    private final NodeActivityService nodeActivityService;

    public NodeActivityRunnerService(NodeActivityService nodeActivityService) {
        this.nodeActivityService = nodeActivityService;
    }

    public void runWithNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                    List<UUID> nodeUuids, Runnable action) {
        runWithNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids, asSupplier(action));
    }

    public void runWithNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        runWithNodeActivity(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWithNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Supplier<T> action) {
        return runWithNodeActivity(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWithNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                     List<UUID> nodeUuids, Supplier<T> action) {
        UUID activityRootNetworkUuid = type.affectsAllRootNetworks() ? null : rootNetworkUuid;
        nodeActivityService.addNodeActivities(type, studyUuid, activityRootNetworkUuid, nodeUuids);
        boolean succeeded = false;
        try {
            T result = action.get();
            succeeded = true;
            return result;
        } finally {
            if (!succeeded || type.isSynchronous()) {
                nodeActivityService.removeActivities(studyUuid, activityRootNetworkUuid, nodeUuids);
            }
        }
    }

    private static <T> Supplier<T> asSupplier(Runnable action) {
        return () -> {
            action.run();
            return null;
        };
    }
}
