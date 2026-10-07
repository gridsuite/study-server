/*
  Copyright (c) 2026, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.networkmodification;

import lombok.NonNull;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.ModificationReference;
import org.gridsuite.study.server.dto.ReferenceAttributes;
import org.gridsuite.study.server.dto.modification.*;
import org.gridsuite.study.server.dto.networkexport.PermissionType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.DirectoryService;
import org.gridsuite.study.server.service.NetworkModificationTreeService;
import org.gridsuite.study.server.service.RootNetworkNodeInfoService;
import org.gridsuite.study.server.service.RootNetworkService;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Slimane amar <slimane.amar at rte-france.com>
 */
@Service
public class NetworkModificationService {
    private final NetworkModificationTreeService networkModificationTreeService;
    private final RootNetworkService rootNetworkService;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final NetworkModificationRestService networkModificationRestService;
    private final NotificationService notificationService;
    private final DirectoryService directoryService;

    public NetworkModificationService(NetworkModificationTreeService networkModificationTreeService,
                                      RootNetworkService rootNetworkService,
                                      RootNetworkNodeInfoService rootNetworkNodeInfoService,
                                      NetworkModificationRestService networkModificationRestService,
                                      NotificationService notificationService,
                                      DirectoryService directoryService) {
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkService = rootNetworkService;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.networkModificationRestService = networkModificationRestService;
        this.notificationService = notificationService;
        this.directoryService = directoryService;
    }

    @Transactional
    public void createNetworkModification(UUID studyUuid, UUID nodeUuid, String modificationAttributes, String userId) {
        handleCreateNetworkModification(studyUuid, nodeUuid, modificationAttributes, userId);
    }

    @Transactional
    public void updateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        handleUpdateNetworkModification(studyUuid, updateModificationAttributes, nodeUuid, modificationUuid, userId);
    }

    private void handleUpdateNetworkModification(UUID studyUuid, String updateModificationAttributes, UUID nodeUuid, UUID modificationUuid, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            networkModificationRestService.updateModification(updateModificationAttributes, modificationUuid, userId);
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void handleCreateNetworkModification(UUID studyUuid, UUID nodeUuid, String createModificationAttributes, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            UUID groupUuid = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            List<RootNetworkEntity> studyRootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);

            List<ModificationApplicationContext> modificationApplicationContexts = studyRootNetworkEntities.stream()
                .map(rootNetworkEntity -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rootNetworkEntity.getId(), nodeUuid, rootNetworkEntity.getNetworkUuid()))
                .toList();

            NetworkModificationsResult networkModificationResults =
                networkModificationRestService.createModification(groupUuid, Pair.of(createModificationAttributes, modificationApplicationContexts));

            if (networkModificationResults != null && networkModificationResults.modificationResults() != null) {
                int index = 0;
                // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
                for (Optional<NetworkModificationResult> modificationResultOpt : networkModificationResults.modificationResults()) {
                    if (modificationResultOpt.isPresent() && studyRootNetworkEntities.get(index) != null) {
                        networkModificationTreeService.handleNetworkModificationApplyResult(studyUuid, nodeUuid, studyRootNetworkEntities.get(index).getId(), modificationResultOpt.get());
                    }
                    index++;
                }
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void stashNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationRestService.stashModifications(groupId, modificationsUuids, userId);
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void restoreNetworkModifications(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationRestService.restoreModifications(groupId, modificationsUuids, studyUuid, nodeUuid, userId);
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsMetadata(UUID studyUuid, UUID nodeUuid, List<UUID> modificationsUuids, String userId, NetworkModificationMetadata metadata) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            UUID groupId = networkModificationTreeService.getModificationGroupUuid(nodeUuid);
            networkModificationRestService.updateModificationsMetadata(groupId, modificationsUuids, metadata, userId);
            if (metadata.getActivated() != null || metadata.getName() != null) {
                networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid);
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void updateNetworkModificationsApplicabilityInRootNetwork(UUID studyUuid, UUID nodeUuid, UUID rootNetworkUuid, Set<UUID> modificationsUuids, String userId, boolean applicable) {
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(nodeUuid);
        networkModificationRestService.verifyModifications(networkModificationTreeService.getModificationGroupUuid(nodeUuid), modificationsUuids);
        try {
            if (!networkModificationTreeService.getStudyUuidForNodeId(nodeUuid).equals(studyUuid)) {
                throw new StudyException(NOT_ALLOWED);
            }
            // the applicability of a reference modification is held by its parent (the shared modification itself),
            // so changing it requires the right to write on it
            assertCanUpdateSharedModifications(new ArrayList<>(modificationsUuids), userId);
            networkModificationRestService.updateRootNetworkApplicability(new ArrayList<>(modificationsUuids),
                rootNetworkService.getRootNetworkTag(rootNetworkUuid), applicable);
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, rootNetworkUuid);
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, nodeUuid, Optional.of(rootNetworkUuid), childrenUuids);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    /**
     * A shared modification holds the applicabilities used by every study referencing it: only a user allowed to write
     * on the shared element may change them.
     */
    private void assertCanUpdateSharedModifications(List<UUID> modificationsUuids, String userId) {
        List<UUID> sharedModificationsUuids = networkModificationRestService.getModificationReferences(modificationsUuids).stream()
            .map(ModificationReference::referencedId)
            .distinct()
            .toList();
        if (!sharedModificationsUuids.isEmpty()) {
            directoryService.checkPermission(sharedModificationsUuids, null, userId, PermissionType.WRITE, false);
        }
    }

    @Transactional
    public void moveNetworkModifications(
        @NonNull UUID studyUuid,
        @NonNull UUID originNodeUuid,
        @NonNull UUID targetNodeUuid,
        @NonNull List<ModificationMoveInfos> modificationInfos,
        boolean isTargetInDifferentNodeTree,
        String userId) {
        boolean isSameNode = originNodeUuid.equals(targetNodeUuid);
        List<UUID> targetChildrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        List<UUID> originChildrenUuids = isSameNode ? List.of() : networkModificationTreeService.getChildrenUuids(originNodeUuid);

        try {
            List<RootNetworkEntity> rootNetworkEntities = rootNetworkService.getStudyRootNetworks(studyUuid);
            networkModificationTreeService.assertStudyContainsNode(studyUuid, targetNodeUuid);
            List<ModificationApplicationContext> applicationContexts = rootNetworkEntities.stream()
                .map(rn -> rootNetworkNodeInfoService.getNetworkModificationApplicationContext(rn.getId(), targetNodeUuid, rn.getNetworkUuid()))
                .toList();

            // Send all modifications operations in bulk
            NetworkModificationsResult result = networkModificationRestService.moveModifications(
                networkModificationTreeService.getModificationGroupUuid(originNodeUuid),
                networkModificationTreeService.getModificationGroupUuid(targetNodeUuid),
                modificationInfos, applicationContexts, isTargetInDifferentNodeTree);
            if (result != null && isTargetInDifferentNodeTree) {
                handleNetworkModificationApplyResult(studyUuid, result.modificationResults(), rootNetworkEntities, targetNodeUuid);
            }

            // Update ModificationReference data
            // TODO this logic ought to be moved in network modification server
            List<UUID> allModificationUuids = modificationInfos.stream().map(ModificationMoveInfos::modificationUuid).toList();
            List<ModificationReference> allReferencesToMove = networkModificationRestService.getModificationReferences(allModificationUuids);
            Map<UUID, List<ModificationReference>> referencesByModification = allReferencesToMove.stream()
                .collect(Collectors.groupingBy(ModificationReference::modificationUuid));
            for (ModificationMoveInfos move : modificationInfos) {
                moveElementReferences(move.sourceCompositeUuid(), move.targetCompositeUuid(),
                    referencesByModification.getOrDefault(move.modificationUuid(), List.of()),
                    userId, studyUuid, targetNodeUuid, isSameNode);
            }
        } finally {
            notificationService.emitModificationsUpdated(studyUuid, targetNodeUuid, targetChildrenUuids);
            if (!isSameNode) {
                notificationService.emitModificationsUpdated(studyUuid, originNodeUuid, originChildrenUuids);
            }
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    /**
     * Repoints (never duplicates) node-references to shared composites when a move changes the modification's container:
     * - landing in a composite: the reference now targets that composite
     * - landing in a node's group: the reference now targets that node
     * Nothing to do when the modification stays in the same container (reorder).
     */
    private void moveElementReferences(UUID sourceCompositeUuid, UUID targetCompositeUuid,
                                       List<ModificationReference> modificationReferences,
                                       String userId, UUID studyUuid,
                                       UUID targetNodeUuid, boolean isSameNode) {
        if (modificationReferences.isEmpty() || isSameNode && Objects.equals(sourceCompositeUuid, targetCompositeUuid)) {
            return;
        }

        if (targetCompositeUuid != null) {
            updateElementsReferences(modificationReferences, targetNodeUuid, targetCompositeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE_NETWORK_MODIFICATION, userId);
        } else {
            updateElementsReferences(modificationReferences, studyUuid, targetNodeUuid, ReferenceAttributes.ReferenceType.STUDY_NODE, userId);
        }
    }

    private void updateElementsReferences(List<ModificationReference> modificationReferences, UUID rootContainerId, UUID containerId,
                                          ReferenceAttributes.ReferenceType targetReferenceType, String userId) {
        modificationReferences.forEach(ref -> directoryService.updateElementReference(
            ref.referencedId(),
            ReferenceAttributes.createReferenceAttributes(ref.modificationUuid(), rootContainerId, containerId, targetReferenceType), userId)
        );
    }

    private void handleNetworkModificationApplyResult(UUID studyUuid, List<Optional<NetworkModificationResult>> modificationResults, List<RootNetworkEntity> rootNetworkEntities, UUID impactedNode) {
        int index = 0;

        // for each NetworkModificationResult, send an impact notification - studyRootNetworkEntities are ordered in the same way as networkModificationResults
        for (Optional<NetworkModificationResult> modificationResultOpt : modificationResults) {
            if (modificationResultOpt.isPresent() && rootNetworkEntities.get(index) != null) {
                networkModificationTreeService.handleNetworkModificationApplyResult(studyUuid, impactedNode, rootNetworkEntities.get(index).getId(), modificationResultOpt.get());
            }
            index++;
        }
    }

    @Transactional
    public UUID assembleModificationsIntoComposite(
        UUID targetStudyUuid,
        UUID targetNodeUuid,
        List<UUID> modificationsUuids,
        String userId) {
        UUID newCompositeUuid;
        List<UUID> childrenUuids = networkModificationTreeService.getChildrenUuids(targetNodeUuid);
        try {
            networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, targetNodeUuid);
            newCompositeUuid = networkModificationRestService.assembleModificationsIntoComposite(modificationsUuids, targetNodeUuid, userId);
        } finally {
            notificationService.emitModificationsUpdated(targetStudyUuid, targetNodeUuid, childrenUuids);
        }
        notificationService.emitElementUpdated(targetStudyUuid, userId);
        return newCompositeUuid;
    }

    @Transactional
    public boolean invalidateNodeTreeWhenMoveModifications(UUID studyUuid, UUID targetNodeUuid, UUID originNodeUuid) {
        boolean isTargetInDifferentNodeTree = !targetNodeUuid.equals(originNodeUuid)
            && !networkModificationTreeService.isAChild(originNodeUuid, targetNodeUuid);

        networkModificationTreeService.invalidateNodeTree(studyUuid, originNodeUuid, InvalidateNodeTreeParameters.ALL);

        if (isTargetInDifferentNodeTree) {
            networkModificationTreeService.invalidateNodeTreeWithLF(studyUuid, targetNodeUuid, InvalidateNodeTreeParameters.ComputationsInvalidationMode.ALL);
        }

        return isTargetInDifferentNodeTree;
    }

    @Transactional
    public void invalidateNodeTreeWhenMoveModification(UUID studyUuid, UUID nodeUuid) {
        networkModificationTreeService.invalidateNodeTree(studyUuid, nodeUuid, InvalidateNodeTreeParameters.ALL);
    }

    @Transactional
    public void buildNode(@NonNull UUID studyUuid, @NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        networkModificationTreeService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, null);
    }

    @Transactional
    public void buildNodes(@NonNull UUID studyUuid, @NonNull List<UUID> nodeUuids, @NonNull UUID rootNetworkUuid, @NonNull String userId) {
        nodeUuids.forEach(nodeUuid -> networkModificationTreeService.buildNode(studyUuid, nodeUuid, rootNetworkUuid, userId, null));
    }

    @Transactional(readOnly = true)
    public boolean isNodeBuilt(@NonNull UUID nodeUuid, @NonNull UUID rootNetworkUuid) {
        return networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid).isBuilt();
    }

    @Transactional(readOnly = true)
    public NodeBuildStatus getNodeBuildStatus(UUID nodeUuid, UUID rootNetworkUuid) {
        return networkModificationTreeService.getNodeBuildStatus(nodeUuid, rootNetworkUuid);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Set<UUID>> getSecurityNodesToRebuild(UUID studyUuid, UUID node1Uuid, UUID node2Uuid) {
        // if node 1 and 2 are in the same "subtree", rebuild only the highest one - otherwise, rebuild both
        List<UUID> nodesToReBuild = networkModificationTreeService.getHighestNodeUuids(node1Uuid, node2Uuid)
            .stream()
            .filter(Predicate.not(networkModificationTreeService::isRootOrConstructionNode)).toList();

        return nodesToReBuild.stream().collect(Collectors.toMap(
            nodeUuid -> nodeUuid,
            nodeUuid -> getRootNetworkWhereNodeIsBuilt(studyUuid, nodeUuid)
        ));
    }

    private Set<UUID> getRootNetworkWhereNodeIsBuilt(UUID studyUuid, UUID nodeUuid) {
        return getNodeBuildStatusByRootNetwork(studyUuid, nodeUuid).entrySet().stream()
            .filter(entry -> entry.getValue().isBuilt())
            .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private Map<UUID, NodeBuildStatus> getNodeBuildStatusByRootNetwork(UUID studyUuid, UUID nodeUuid) {
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().collect(Collectors.toMap(
            RootNetworkEntity::getId,
            rn -> rootNetworkNodeInfoService.getRootNetworkNodeInfo(nodeUuid, rn.getId()).map(rni -> rni.getNodeBuildStatus().toDto()).orElseThrow(() -> new StudyException(NOT_FOUND,
                "Root network not found"))
        ));
    }

}
