/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.StudyConstants.CompositeModificationsActionType;
import org.gridsuite.study.server.dto.modification.NetworkModificationMetadata;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeActivityStatus;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
        studyService.createNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
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
        studyService.moveNetworkModifications(studyUuid, nodeUuid, nodeUuid, List.of(modificationUuid), beforeUuid, false, userId);
    }

    public void moveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        handleRebuildNode(studyUuid, targetNodeUuid, originNodeUuid, userId,
            () -> handleMoveNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsToCopyUuidList, userId));
    }

    public void duplicateNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsUuids, String userId) {
        handleRebuildNode(studyUuid, targetNodeUuid, userId,
            () -> handleDuplicateNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsUuids, userId));
    }

    private void handleDuplicateNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsUuids, String userId) {
        studyService.invalidateNodeTreeWithLF(studyUuid, targetNodeUuid);
        studyService.duplicateNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsUuids, userId);
    }

    public void insertCompositeNetworkModifications(UUID studyUuid, UUID nodeUuid, List<Pair<UUID, String>> compositesInfos, String userId, CompositeModificationsActionType action) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
            () -> handleInsertCompositeNetworkModifications(studyUuid, nodeUuid, compositesInfos, userId, action));
    }

    private void handleInsertCompositeNetworkModifications(UUID studyUuid, UUID nodeUuid, List<Pair<UUID, String>> compositesInfos, String userId, CompositeModificationsActionType action) {
        studyService.invalidateNodeTreeWithLF(studyUuid, nodeUuid);
        studyService.insertCompositeNetworkModifications(studyUuid, nodeUuid, compositesInfos, userId, action);
    }

    public void moveSubModification(
            UUID studyUuid,
            UUID nodeUuid,
            UUID sourceCompositeUuid,
            UUID targetCompositeUuid,
            UUID modificationUuid,
            UUID beforeUuid,
            String userId) {
        handleRebuildNode(studyUuid, nodeUuid, userId,
                () -> handleMoveNetworkSubmodification(
                        studyUuid, nodeUuid,
                        sourceCompositeUuid, targetCompositeUuid,
                        modificationUuid, beforeUuid, userId));
    }

    public UUID assembleModificationsIntoComposite(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        return handleRebuildNodeWithReturn(
                studyUuid,
                nodeUuid,
                userId,
                () -> {
                    studyService.invalidateNodeTreeWhenMoveModification(studyUuid, nodeUuid);
                    return studyService.assembleModificationsIntoComposite(studyUuid, nodeUuid, modificationsUuids, userId);
                });
    }

    private void handleMoveNetworkSubmodification(@NonNull UUID studyUuid,
                                                  @NonNull UUID nodeUuid,
                                                  UUID sourceCompositeUuid,
                                                  UUID targetCompositeUuid,
                                                  @NonNull UUID modificationUuid,
                                                  UUID beforeUuid,
                                                  String userId) {
        studyService.invalidateNodeTreeWhenMoveModification(studyUuid, nodeUuid);
        studyService.moveSubModification(studyUuid, nodeUuid,
                sourceCompositeUuid, targetCompositeUuid,
                modificationUuid, beforeUuid, userId);
    }

    private void handleMoveNetworkModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid, List<UUID> modificationsToCopyUuidList, String userId) {
        boolean isTargetInDifferentNodeTree = studyService.invalidateNodeTreeWhenMoveModifications(studyUuid, targetNodeUuid, originNodeUuid);
        studyService.moveNetworkModifications(studyUuid, targetNodeUuid, originNodeUuid, modificationsToCopyUuidList, null, isTargetInDifferentNodeTree, userId);
    }

    private void handleRebuildNode(UUID studyUuid, UUID nodeUuid, String userId, Runnable action) {
        handleRebuildNode(studyUuid, nodeUuid, nodeUuid, userId, action);
    }

    private <T> T handleRebuildNodeWithReturn(UUID studyUuid, UUID nodeUuid, String userId, Supplier<T> action) {
        return handleRebuildNodeWithReturn(studyUuid, nodeUuid, nodeUuid, userId, action);
    }

    private void handleRebuildNode(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Runnable action) {
        handleRebuildNodeWithReturn(studyUuid, node1Uuid, node2Uuid, userId, () -> {
            action.run();
            return null;
        });
    }

    private <T> T handleRebuildNodeWithReturn(UUID studyUuid, UUID node1Uuid, UUID node2Uuid, String userId, Supplier<T> action) {
        // if node 1 and 2 are in the same "subtree", rebuild only the highest one - otherwise, rebuild both
        List<UUID> highestNodeUuids = networkModificationTreeService.getHighestNodeUuids(node1Uuid, node2Uuid);

        List<UUID> nodesToSetActivity = highestNodeUuids.stream()
            .filter(Predicate.not(networkModificationTreeService::isRootNode)).toList();
        // isRootOrConstructionNode is a superset of isRootNode, so nodesToReBuild is always a subset of nodesToSetActivity
        List<UUID> nodesToReBuild = nodesToSetActivity.stream()
            .filter(Predicate.not(networkModificationTreeService::isConstructionNode)).toList();

        Map<UUID, Set<UUID>> rootNetworkUuidsByNodeBuilt = nodesToReBuild.stream().collect(Collectors.toMap(
            nodeUuid -> nodeUuid,
            nodeUuid -> getRootNetworkWhereNodeIsBuilt(studyUuid, nodeUuid)
        ));

        T result;
        if (nodesToSetActivity.isEmpty()) {
            result = action.get();
        } else {
            studyService.setNodeActivityAcrossAllRootNetworks(studyUuid, nodesToSetActivity, NodeActivityStatus.UPDATING);
            try {
                result = action.get();
            } finally {
                studyService.clearNodeActivityAcrossAllRootNetworks(studyUuid, nodesToSetActivity);
            }
        }

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

        return result;
    }

    private Set<UUID> getRootNetworkWhereNodeIsBuilt(UUID studyUuid, UUID nodeUuid) {
        return studyService.getNodeBuildStatusByRootNetwork(studyUuid, nodeUuid).entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
