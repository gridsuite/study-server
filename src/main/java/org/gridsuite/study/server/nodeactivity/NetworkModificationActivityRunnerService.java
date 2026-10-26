/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.repository.nodeactivity.BatchActionRepository;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.StudyService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationService;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.gridsuite.study.server.nodeactivity.NodeActivityType.BUILD;
import static org.gridsuite.study.server.nodeactivity.NodeActivityType.EDIT_MODIFICATIONS;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Service
public class NetworkModificationActivityRunnerService extends AbstractNodeActivityRunnerService {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(StudyService.class);

    private final NetworkModificationService networkModificationService;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final BatchActionRepository batchActionRepository;

    public NetworkModificationActivityRunnerService(NodeActivityService nodeActivityService,
                                                    NetworkModificationService networkModificationService,
                                                    NetworkModificationTreeService networkModificationTreeService,
                                                    BatchActionRepository batchActionRepository) {
        super(nodeActivityService);
        this.networkModificationService = networkModificationService;
        this.networkModificationTreeService = networkModificationTreeService;
        this.batchActionRepository = batchActionRepository;
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

    public boolean runInvalidateNodeTreeBatch(UUID studyUuid, UUID nodeUuid, boolean newBatch) {
        try {
            runWith(EDIT_MODIFICATIONS, studyUuid, null, List.of(nodeUuid),
                () -> networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid));
        } catch (StudyException e) {
            if (!e.isNodeActivityConflict()) {
                throw e;
            }
            if (newBatch) {
                batchActionRepository.save(new BatchActionEntity(nodeUuid, studyUuid, BatchActionType.INVALIDATE_NODE_TREE, Instant.now()));
            }
            return false;
        }
        return true;
    }

    @Override
    public void removeActivities(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        super.removeActivities(studyUuid, rootNetworkUuid, nodeUuids);

        runMostRecentBatch();
    }

    private void runMostRecentBatch() {
        // Get most recent batch
        // TODO use a query with oldest instant
        BatchActionEntity mostRecentBatch = batchActionRepository.findAll().stream()
            .max(Comparator.comparing(BatchActionEntity::getCreatedAt))
            .orElse(null);
        if (mostRecentBatch == null) {
            return;
        }

        // Run batch
        boolean runOk = false;
        switch (mostRecentBatch.getType()) {
            case INVALIDATE_NODE_TREE:
                runOk = runInvalidateNodeTreeBatch(mostRecentBatch.getStudyId(), mostRecentBatch.getNodeId(), false);
                break;
            default:
                LOGGER.error("Unsupported batch node action type : {}", mostRecentBatch.getType());
        }

        // Remove batch
        if (runOk) {
            batchActionRepository.deleteById(mostRecentBatch.getId());
        }
    }
}
