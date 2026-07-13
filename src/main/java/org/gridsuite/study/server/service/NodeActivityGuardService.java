/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeActivityCheckScope;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeActivityStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityGuardService {
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public NodeActivityGuardService(RootNetworkNodeInfoService rootNetworkNodeInfoService, NetworkModificationTreeService networkModificationTreeService) {
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    private List<UUID> resolveCheckSet(List<UUID> nodeUuids, NodeActivityCheckScope checkScope) {
        return switch (checkScope) {
            case SELF -> nodeUuids;
            case BRANCH -> networkModificationTreeService.getNodeBranchUuids(nodeUuids);
            case ANCESTORS -> networkModificationTreeService.getNodeAncestorUuids(nodeUuids);
        };
    }

    public List<UUID> acquireActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeActivityCheckScope checkScope, NodeActivityStatus activity) {
        if (nodeUuids.isEmpty()) {
            return List.of();
        }
        List<UUID> checkSetUuids = resolveCheckSet(nodeUuids, checkScope);
        List<UUID> acquired = new ArrayList<>();
        try {
            for (UUID rootNetworkUuid : rootNetworkUuids) {
                rootNetworkNodeInfoService.setNodeActivity(studyUuid, rootNetworkUuid, nodeUuids, checkSetUuids, activity);
                acquired.add(rootNetworkUuid);
            }
            return acquired;
        } catch (StudyException e) {
            acquired.forEach(rootNetworkUuid -> rootNetworkNodeInfoService.clearNodeActivity(studyUuid, rootNetworkUuid, nodeUuids));
            throw e;
        }
    }

    public void releaseActivity(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        rootNetworkUuids.forEach(rootNetworkUuid -> rootNetworkNodeInfoService.clearNodeActivity(studyUuid, rootNetworkUuid, nodeUuids));
    }

    public <T> T runGuarded(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeActivityCheckScope checkScope, NodeActivityStatus activity, Supplier<T> action) {
        List<UUID> acquired = acquireActivity(studyUuid, rootNetworkUuids, nodeUuids, checkScope, activity);
        try {
            return action.get();
        } finally {
            releaseActivity(studyUuid, acquired, nodeUuids);
        }
    }

    public void runGuarded(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeActivityCheckScope checkScope, NodeActivityStatus activity, Runnable action) {
        runGuarded(studyUuid, rootNetworkUuids, nodeUuids, checkScope, activity, () -> {
            action.run();
            return null;
        });
    }

    public <T> T runGuardedAsync(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeActivityCheckScope checkScope, NodeActivityStatus activity, Supplier<T> action) {
        List<UUID> acquired = acquireActivity(studyUuid, rootNetworkUuids, nodeUuids, checkScope, activity);
        try {
            return action.get();
        } catch (Exception e) {
            releaseActivity(studyUuid, acquired, nodeUuids);
            throw e;
        }
    }

    public void runGuardedAsync(UUID studyUuid, List<UUID> rootNetworkUuids, List<UUID> nodeUuids, NodeActivityCheckScope checkScope, NodeActivityStatus activity, Runnable action) {
        runGuardedAsync(studyUuid, rootNetworkUuids, nodeUuids, checkScope, activity, () -> {
            action.run();
            return null;
        });
    }

    public <T> T runGuardedComputation(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, Supplier<T> action) {
        return runGuardedAsync(studyUuid, List.of(rootNetworkUuid), List.of(nodeUuid), NodeActivityCheckScope.ANCESTORS, NodeActivityStatus.COMPUTATION_RUNNING, action);
    }

    public void runGuardedComputation(UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid, Runnable action) {
        runGuardedAsync(studyUuid, List.of(rootNetworkUuid), List.of(nodeUuid), NodeActivityCheckScope.ANCESTORS, NodeActivityStatus.COMPUTATION_RUNNING, action);
    }
}
