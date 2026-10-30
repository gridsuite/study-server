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
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class NetworkModificationActivityRunnerService extends AbstractNodeActivityRunnerService {

    private final NetworkModificationService networkModificationService;

    public NetworkModificationActivityRunnerService(BatchActionRunnerService batchActionRunnerService,
                                                    NodeActivityService nodeActivityService,
                                                    NetworkModificationService networkModificationService) {
        super(batchActionRunnerService, nodeActivityService);
        this.networkModificationService = networkModificationService;
    }

    public <T> T runWith(UUID studyUuid, UUID nodeUuid, String userId, Runnable action) {
        return runWith(studyUuid, nodeUuid, nodeUuid, userId, asSupplier(action));
    }

    public <T> T runWith(UUID studyUuid, UUID nodeUuid, String userId, Supplier<T> action) {
        return runWith(studyUuid, nodeUuid, nodeUuid, userId, action);
    }

    public <T> T runWith(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Runnable action) {
        return runWith(studyUuid, node1Uuid, node2Uuid, userId, asSupplier(action));
    }

    private <T> T runWith(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Supplier<T> action) {
        Map<UUID, Set<UUID>> rootNetworkUuidsByNodeBuilt = networkModificationService.getSecurityNodesToRebuild(studyUuid, node1Uuid, node2Uuid);

        T result = runWith(EDIT_MODIFICATIONS, studyUuid,
            Stream.of(node1Uuid, node2Uuid).distinct().toList(), action);

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
}
