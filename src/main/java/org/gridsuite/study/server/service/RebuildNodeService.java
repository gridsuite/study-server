/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.gridsuite.study.server.dto.modification.NetworkModificationMetadata;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class RebuildNodeService {
    private final StudyService studyService;
    private final NetworkModificationTreeService networkModificationTreeService;

    public RebuildNodeService(StudyService studyService, NetworkModificationTreeService networkModificationTreeService) {
        this.studyService = studyService;
        this.networkModificationTreeService = networkModificationTreeService;
    }

    public void createNetworkModification(UUID studyUuid, UUID nodeUuid, String modificationAttributes, String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> handleCreateNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId));
    }

    private void handleCreateNetworkModification(UUID studyUuid, UUID nodeUuid, String modificationAttributes, String userId) {
        studyService.invalidateNodeTreeWithLF(studyUuid, nodeUuid);
        try {
            studyService.createNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, nodeUuid);
        }
    }

    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModification(studyUuid, updateModificationAttributes, nodeUuid, modificationUuid, userId));
    }

    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> studyService.stashNetworkModifications(studyUuid, nodeUuid, modificationsUuids, userId));
    }

    public void updateNetworkModificationsMetadata(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId, NetworkModificationMetadata metadata) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModificationsMetadata(studyUuid, nodeUuid, modificationsUuids, userId, metadata));
    }

    public void updateNetworkModificationsActivation(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Set<UUID> modificationsUuids, String userId, boolean activated) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> studyService.updateNetworkModificationsActivationInRootNetwork(studyUuid, nodeUuid, rootNetworkUuid, modificationsUuids, userId, activated));
    }

    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> studyService.restoreNetworkModifications(studyUuid, nodeUuid, modificationsUuids, userId));
    }

    public void moveNetworkModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid, String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> handleMoveNetworkModification(studyUuid, nodeUuid, modificationUuid, beforeUuid, userId));
    }

    private void handleMoveNetworkModification(UUID studyUuid, UUID nodeUuid, UUID modificationUuid, UUID beforeUuid, String userId) {
        studyService.invalidateNodeTreeWhenMoveModification(studyUuid, nodeUuid);
        try {
            studyService.moveNetworkModifications(studyUuid, nodeUuid, nodeUuid, List.of(modificationUuid), beforeUuid, false, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, nodeUuid);
        }
    }

    public void moveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        handleRebuildNode(studyUuid, targetNodeUuid, originNodeUuid, userId,
            () -> handleMoveNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsToCopyUuidList, userId));
    }

    private void handleMoveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        boolean isTargetInDifferentNodeTree = studyService.invalidateNodeTreeWhenMoveModifications(studyUuid, targetNodeUuid, originNodeUuid);
        try {
            studyService.moveNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsToCopyUuidList, null, isTargetInDifferentNodeTree, userId);
        } finally {
            studyService.unblockNodeTree(studyUuid, originNodeUuid);
            if (isTargetInDifferentNodeTree) {
                studyService.unblockNodeTree(studyUuid, targetNodeUuid);
            }
        }
    }

    private void handleRebuildNode(UUID studyUuid, UUID nodeUuid, String userId, Runnable action) {
        handleRebuildNode(studyUuid, nodeUuid, nodeUuid, userId, action);
    }

    private void handleRebuildNode(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Runnable action) {
        // if node 1 and 2 are in the same "subtree", rebuild only the highest one - otherwise, rebuild both
        List<UUID> nodesToReBuild = networkModificationTreeService.getHighestNodeUuids(node1Uuid, node2Uuid).stream()
            .filter(Predicate.not(networkModificationTreeService::isRootOrConstructionNode)).toList();

        if (nodesToReBuild.isEmpty()) {
            action.run();
            return;
        }

        Map<UUID, Set<UUID>> rootNetworkUuidsByNodeBuilt = nodesToReBuild.stream().collect(Collectors.toMap(
            nodeUuid -> nodeUuid,
            nodeUuid -> getRootNetworkWhereNodeIsBuilt(studyUuid, nodeUuid)
        ));

        action.run();

        rootNetworkUuidsByNodeBuilt.forEach((nodeUuid, rootNetworkUuids) ->
            rootNetworkUuids.stream().forEach(rootNetworkUuid ->
                    studyService.buildNode(
                        studyUuid,
                        nodeUuid,
                        rootNetworkUuid,
                        userId
                    )
                )
        );
    }

    private Set<UUID> getRootNetworkWhereNodeIsBuilt(UUID studyUuid, UUID nodeUuid) {
        return studyService.getNodeBuildStatusByRootNetwork(studyUuid, nodeUuid).entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
