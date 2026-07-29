/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.LocalActivityStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeCheckScope;
import org.gridsuite.study.server.networkmodificationtree.dto.SharedActivityStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NODE_ACTIVITY_CONFLICT;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityGuardService {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final NotificationService notificationService;

    public NodeActivityGuardService(RootNetworkNodeInfoService rootNetworkNodeInfoService, NetworkModificationTreeService networkModificationTreeService,
                                     NotificationService notificationService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.notificationService = notificationService;
    }

    private record CheckSet(List<UUID> localActivityCheckUuids, List<UUID> sharedActivityCheckUuids) {
    }

    private CheckSet resolveCheckSet(List<UUID> nodeUuids, NodeCheckScope scope, List<UUID> ancestors) {
        return switch (scope) {
            case SELF -> new CheckSet(nodeUuids, nodeUuids);
            case ANCESTORS -> new CheckSet(List.of(), Stream.concat(nodeUuids.stream(), ancestors.stream()).distinct().toList());
            case BRANCH -> {
                List<UUID> branch = Stream.concat(nodeUuids.stream(), networkModificationTreeService.getAllChildrenUuids(nodeUuids).stream()).distinct().toList();
                yield new CheckSet(branch, Stream.concat(branch.stream(), ancestors.stream()).distinct().toList());
            }
        };
    }

    private static Supplier<Void> asSupplier(Runnable action) {
        return () -> {
            action.run();
            return null;
        };
    }

    // Shared: study-wide, one value per node
    public <T> T runWithSharedActivity(UUID studyUuid, List<UUID> nodeUuids, NodeCheckScope scope, SharedActivityStatus reason, Supplier<T> action) {
        acquireSharedActivity(studyUuid, nodeUuids, scope, reason);
        try {
            return action.get();
        } finally {
            releaseSharedActivity(studyUuid, nodeUuids);
        }
    }

    public void runWithSharedActivity(UUID studyUuid, List<UUID> nodeUuids, NodeCheckScope scope, SharedActivityStatus reason, Runnable action) {
        runWithSharedActivity(studyUuid, nodeUuids, scope, reason, asSupplier(action));
    }

    private void acquireSharedActivity(UUID studyUuid, List<UUID> nodeUuids, NodeCheckScope scope, SharedActivityStatus reason) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        List<UUID> ancestors = scope == NodeCheckScope.SELF ? List.of() : networkModificationTreeService.getNodeAncestorUuids(nodeUuids);
        CheckSet checkSet = resolveCheckSet(nodeUuids, scope, ancestors);
        int updated = networkModificationTreeService.acquireSharedActivity(nodeUuids, checkSet.localActivityCheckUuids(), checkSet.sharedActivityCheckUuids(), reason);
        if (updated != nodeUuids.size()) {
            throw new StudyException(NODE_ACTIVITY_CONFLICT, "Another action is in progress on this node !");
        }
        notificationService.emitSharedActivityUpdated(studyUuid, nodeUuids);
    }

    private void releaseSharedActivity(UUID studyUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        networkModificationTreeService.releaseSharedActivity(nodeUuids);
        notificationService.emitSharedActivityUpdated(studyUuid, nodeUuids);
    }

    // Local: build/computation state, scoped to one root network
    public List<UUID> acquireLocalActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeCheckScope scope, LocalActivityStatus activity) {
        if (nodeUuids.isEmpty()) {
            return List.of();
        }

        boolean needsAncestors = scope != NodeCheckScope.SELF || activity == LocalActivityStatus.BUILDING;
        List<UUID> ancestors = needsAncestors ? networkModificationTreeService.getNodeAncestorUuids(nodeUuids) : List.of();
        CheckSet checkSet = resolveCheckSet(nodeUuids, scope, ancestors);

        List<UUID> securityAncestorCheckUuids = activity == LocalActivityStatus.BUILDING ? ancestors : List.of();
        List<UUID> acquired = new ArrayList<>();
        try {
            for (UUID rootNetworkUuid : rootNetworkUuids) {
                rootNetworkNodeInfoService.acquireActivity(studyUuid, rootNetworkUuid, nodeUuids, checkSet.localActivityCheckUuids(), checkSet.sharedActivityCheckUuids(),
                    securityAncestorCheckUuids, activity);
                acquired.add(rootNetworkUuid);
            }
            return acquired;
        } catch (StudyException e) {
            releaseLocalActivity(studyUuid, acquired, nodeUuids);
            throw e;
        }
    }

    public void releaseLocalActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        rootNetworkUuids.forEach(rootNetworkUuid -> rootNetworkNodeInfoService.releaseActivity(studyUuid, rootNetworkUuid, nodeUuids));
    }

    public <T> T runWithLocalActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeCheckScope scope, LocalActivityStatus activity, Supplier<T> action) {
        List<UUID> acquired = acquireLocalActivity(studyUuid, rootNetworkUuids, nodeUuids, scope, activity);
        try {
            return action.get();
        } finally {
            releaseLocalActivity(studyUuid, acquired, nodeUuids);
        }
    }

    public void runWithLocalActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeCheckScope scope, LocalActivityStatus activity, Runnable action) {
        runWithLocalActivity(studyUuid, rootNetworkUuids, nodeUuids, scope, activity, asSupplier(action));
    }

    // Async variant: release only if dispatch itself fails, the caller releases later,
    // when the remote result (build/computation) actually completes.
    public <T> T runWithLocalActivityAsync(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeCheckScope scope,
                                            LocalActivityStatus activity, Supplier<T> action) {
        List<UUID> acquired = acquireLocalActivity(studyUuid, rootNetworkUuids, nodeUuids, scope, activity);
        try {
            return action.get();
        } catch (Exception e) {
            releaseLocalActivity(studyUuid, acquired, nodeUuids);
            throw e;
        }
    }

    public void runWithLocalActivityAsync(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeCheckScope scope, LocalActivityStatus activity, Runnable action) {
        runWithLocalActivityAsync(studyUuid, rootNetworkUuids, nodeUuids, scope, activity, asSupplier(action));
    }

    public <T> T runComputation(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, Supplier<T> action) {
        return runWithLocalActivityAsync(studyUuid, List.of(rootNetworkUuid), List.of(nodeUuid), NodeCheckScope.ANCESTORS, LocalActivityStatus.COMPUTATION_RUNNING, action);
    }

    public void runComputation(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, Runnable action) {
        runWithLocalActivityAsync(studyUuid, List.of(rootNetworkUuid), List.of(nodeUuid), NodeCheckScope.ANCESTORS, LocalActivityStatus.COMPUTATION_RUNNING, action);
    }
}
