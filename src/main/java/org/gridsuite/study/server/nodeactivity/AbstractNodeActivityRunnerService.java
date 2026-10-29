/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import lombok.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public abstract class AbstractNodeActivityRunnerService {

    private final BatchActionRunnerService batchActionRunnerService;
    protected final NodeActivityService nodeActivityService;

    protected AbstractNodeActivityRunnerService(BatchActionRunnerService batchActionRunnerService, NodeActivityService nodeActivityService) {
        this.batchActionRunnerService = batchActionRunnerService;
        this.nodeActivityService = nodeActivityService;
    }

    public void runWith(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids, Runnable action) {
        runWith(type, studyUuid, rootNetworkUuid, nodeUuids, asSupplier(action));
    }

    public <T> T runWith(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Supplier<T> action) {
        return runWith(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWith(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids, Supplier<T> action) {
        UUID activityRootNetworkUuid = type.affectsAllRootNetworks() ? null : rootNetworkUuid;
        addNodeActivities(type, studyUuid, activityRootNetworkUuid, nodeUuids);
        boolean succeeded = false;
        try {
            T result = action.get();
            succeeded = true;
            return result;
        } finally {
            if (!succeeded || type.isSynchronous()) {
                removeActivities(studyUuid, activityRootNetworkUuid, nodeUuids);
            }
        }
    }

    public void addNodeActivities(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        nodeActivityService.addNodeActivities(type, studyUuid, rootNetworkUuid, nodeUuids);
    }

    public void removeActivities(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        nodeActivityService.removeActivities(studyUuid, rootNetworkUuid, nodeUuids);

        batchActionRunnerService.runLastPendingAction(this);
    }

    protected static <T> Supplier<T> asSupplier(Runnable action) {
        return () -> {
            action.run();
            return null;
        };
    }

    public void runBatchAction(@NonNull BatchActionType type, @NonNull UUID studyId, @NonNull UUID nodeId) {
        batchActionRunnerService.runAction(this, new BatchActionEntity(nodeId, studyId, type, Instant.now()));
    }
}
