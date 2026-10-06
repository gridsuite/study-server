/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.gridsuite.study.server.nodeactivity.NodeActivityType.BUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_MODIFICATIONS;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityRunnerService {

    private final NodeActivityService nodeActivityService;
    private final NetworkModificationService networkModificationService;

    public NodeActivityRunnerService(NodeActivityService nodeActivityService, NetworkModificationService networkModificationService) {
        this.nodeActivityService = nodeActivityService;
        this.networkModificationService = networkModificationService;
    }

    public void runWith(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                        List<UUID> nodeUuids, Runnable action) {
        runWith(type, studyUuid, rootNetworkUuid, nodeUuids, asSupplier(action));
    }

    public void runWith(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        runWith(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWith(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Supplier<T> action) {
        return runWith(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWith(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
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

    public <T> T runWithNetworkModification(UUID studyUuid, UUID nodeUuid, String userId, Runnable action) {
        return runWithNetworkModification(studyUuid, nodeUuid, nodeUuid, userId, action);
    }

    public <T> T runWithNetworkModification(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Runnable action) {
        Map<UUID, Set<UUID>> rootNetworkUuidsByNodeBuilt = networkModificationService.getSecurityNodesToRebuild(studyUuid, node1Uuid, node2Uuid);

        T result = runWith(EDIT_MODIFICATIONS, studyUuid,
            Stream.of(node1Uuid, node2Uuid).distinct().toList(), asSupplier(action));

        rebuildSecurityNodesIfNeeded(studyUuid, rootNetworkUuidsByNodeBuilt, userId);

        return result;
    }

    private void rebuildSecurityNodesIfNeeded(UUID studyUuid, Map<UUID, Set<UUID>> nodesToRebuild, String userId) {
        nodesToRebuild.forEach((nodeUuid, rootNetworkUuids) ->
            rootNetworkUuids.forEach(rootNetworkUuid -> {
                if (!networkModificationService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt()) {
                    runWith(BUILD, studyUuid, rootNetworkUuid, List.of(nodeUuid),
                        () -> networkModificationService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId));
                }
            })
        );
    }

    private static <T> Supplier<T> asSupplier(Runnable action) {
        return () -> {
            action.run();
            return null;
        };
    }
}
