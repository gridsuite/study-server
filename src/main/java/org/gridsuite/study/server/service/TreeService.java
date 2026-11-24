/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import org.apache.commons.collections4.CollectionUtils;
import org.gridsuite.study.server.dto.DeleteNodeInfos;
import org.gridsuite.study.server.dto.sequence.NodeSequenceType;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.InsertMode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyEntity;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.service.dynamicsimulation.DynamicSimulationEventService;
import org.gridsuite.study.server.service.networkmodification.NetworkModificationRestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_ALLOWED;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;
import static org.gridsuite.study.server.service.StudyService.STUDY_NOT_FOUND;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class TreeService {

    private final NetworkModificationTreeService networkModificationTreeService;
    private final NotificationService notificationService;
    private final RootNetworkService rootNetworkService;
    private final NetworkModificationRestService networkModificationRestService;
    private final StudyRepository studyRepository;
    private final RootNetworkNodeInfoService rootNetworkNodeInfoService;
    private final StudyServerExecutionService studyServerExecutionService;
    private final NetworkService networkStoreService;
    private final DynamicSimulationEventService dynamicSimulationEventService;

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeService.class);

    public TreeService(NetworkModificationTreeService networkModificationTreeService,
                       NotificationService notificationService,
                       RootNetworkService rootNetworkService,
                       NetworkModificationRestService networkModificationRestService,
                       StudyRepository studyRepository,
                       RootNetworkNodeInfoService rootNetworkNodeInfoService,
                       StudyServerExecutionService studyServerExecutionService,
                       NetworkService networkStoreService,
                       DynamicSimulationEventService dynamicSimulationEventService) {
        this.networkModificationTreeService = networkModificationTreeService;
        this.notificationService = notificationService;
        this.rootNetworkService = rootNetworkService;
        this.networkModificationRestService = networkModificationRestService;
        this.studyRepository = studyRepository;
        this.rootNetworkNodeInfoService = rootNetworkNodeInfoService;
        this.studyServerExecutionService = studyServerExecutionService;
        this.networkStoreService = networkStoreService;
        this.dynamicSimulationEventService = dynamicSimulationEventService;
    }

    @Transactional
    public void moveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode, String userId) {
        assertMoveStudyNode(studyUuid, nodeToMoveUuid, referenceNodeUuid, insertMode);

        List<NodeEntity> oldChildren = null;
        boolean shouldUnbuildChildren = networkModificationTreeService.hasModifications(nodeToMoveUuid, false);

        //Unbuild previous children if necessary
        if (shouldUnbuildChildren) {
            oldChildren = networkModificationTreeService.getChildren(nodeToMoveUuid);
        }

        networkModificationTreeService.moveStudyNode(nodeToMoveUuid, referenceNodeUuid, insertMode);

        //Unbuilding moved node or new children if necessary
        if (shouldUnbuildChildren) {
            networkModificationTreeService.invalidateNodeTree(studyUuid, nodeToMoveUuid);
            oldChildren.forEach(child -> networkModificationTreeService.invalidateNodeTree(studyUuid, child.getIdNode()));
        } else {
            networkModificationTreeService.invalidateNode(studyUuid, nodeToMoveUuid);
        }
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void assertMoveStudyNode(UUID studyUuid, UUID nodeToMoveUuid, UUID referenceNodeUuid, InsertMode insertMode) {
        networkModificationTreeService.assertStudyContainsNode(studyUuid, nodeToMoveUuid);
        networkModificationTreeService.assertStudyContainsNode(studyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateNode(nodeToMoveUuid, referenceNodeUuid, insertMode);
    }

    @Transactional
    public void moveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID referenceNodeUuid, String userId) {
        assertMoveStudySubtree(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);

        List<UUID> allChildren = networkModificationTreeService.getChildrenUuids(parentNodeToMoveUuid);
        if (allChildren.contains(referenceNodeUuid)) {
            throw new StudyException(NOT_ALLOWED);
        }

        networkModificationTreeService.moveStudySubtree(parentNodeToMoveUuid, referenceNodeUuid);

        rootNetworkService.getStudyRootNetworks(studyUuid).forEach(rootNetworkEntity -> {
            UUID rootNetworkUuid = rootNetworkEntity.getId();
            if (networkModificationTreeService.getNodeBuildStatus(parentNodeToMoveUuid, rootNetworkUuid).isBuilt()) {
                networkModificationTreeService.invalidateNodeTree(studyUuid, parentNodeToMoveUuid);
            }
            allChildren.stream()
                    .filter(childUuid -> networkModificationTreeService.getNodeBuildStatus(childUuid, rootNetworkUuid).isBuilt())
                    .forEach(childUuid -> networkModificationTreeService.invalidateNodeTree(studyUuid, childUuid));
        });

        notificationService.emitSubtreeMoved(studyUuid, parentNodeToMoveUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(studyUuid, userId);
    }

    private void assertMoveStudySubtree(UUID studyUuid, UUID parentNodeToMoveUuid, UUID referenceNodeUuid) {
        networkModificationTreeService.assertStudyContainsNode(studyUuid, parentNodeToMoveUuid);
        networkModificationTreeService.assertStudyContainsNode(studyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateSubtree(parentNodeToMoveUuid, referenceNodeUuid);
    }

    private StudyEntity getStudy(UUID studyUuid) {
        return studyRepository.findById(studyUuid).orElseThrow(() -> new StudyException(NOT_FOUND, STUDY_NOT_FOUND));
    }

    @Transactional
    public void duplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid, String userId) {
        assertDuplicateStudySubtree(sourceStudyUuid, targetStudyUuid, parentNodeToCopyUuid, referenceNodeUuid);
        if (!sourceStudyUuid.equals(targetStudyUuid)) {
            assertCanWriteModificationsInStudy(targetStudyUuid, networkModificationTreeService.getSubtreeModificationGroupUuids(parentNodeToCopyUuid),
                    List.of(), List.of(), userId);
        }
        AbstractNode studySubTree = networkModificationTreeService.getStudySubtree(sourceStudyUuid, parentNodeToCopyUuid, null);
        StudyEntity studyEntity = getStudy(targetStudyUuid);
        UUID duplicatedNodeUuid = networkModificationTreeService.cloneStudyTree(studySubTree, referenceNodeUuid, studyEntity, userId);
        notificationService.emitSubtreeInserted(targetStudyUuid, duplicatedNodeUuid, referenceNodeUuid);
        notificationService.emitElementUpdated(targetStudyUuid, userId);
    }

    private void assertDuplicateStudySubtree(UUID sourceStudyUuid, UUID targetStudyUuid, UUID parentNodeToCopyUuid, UUID referenceNodeUuid) {
        networkModificationTreeService.assertStudyContainsNode(sourceStudyUuid, parentNodeToCopyUuid);
        networkModificationTreeService.assertStudyContainsNode(targetStudyUuid, referenceNodeUuid);
        networkModificationTreeService.assertMoveOrDuplicateSubtree(parentNodeToCopyUuid, referenceNodeUuid);
    }

    /**
     * Modifications written into a study must be associated with all its root network tags, including the shared
     * modifications they point to: the ones the user cannot write on, which they cannot associate with a new tag, must
     * already be.
     *
     * @param containerUuids groups or composites whose content is written into the study: only the shared modifications
     *                       their content refers to are checked, not the containers themselves
     * @param modificationUuids modifications written into the study: the shared modifications they and their content
     *                          refer to are checked
     * @param sharedUuids shared modifications the study is given a reference to: they are checked themselves, along
     *                    with the shared modifications their content refers to
     */
    //TODO : duplicate from study-server
    private void assertCanWriteModificationsInStudy(UUID studyUuid, List<UUID> containerUuids, List<UUID> modificationUuids,
                                                    List<UUID> sharedUuids, String userId) {
        networkModificationRestService.assertRootNetworkTagsAllowed(containerUuids, modificationUuids, sharedUuids,
                rootNetworkService.getStudyRootNetworkTags(studyUuid), userId);
    }

    @Transactional
    public NetworkModificationNode createNode(UUID studyUuid, UUID nodeId, NetworkModificationNode nodeInfo, InsertMode insertMode, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.assertCreateNode(nodeId, nodeInfo.getNodeType(), insertMode);
        NetworkModificationNode newNode = networkModificationTreeService.createNode(study, nodeId, nodeInfo, insertMode, userId);

        UUID parentUuid = networkModificationTreeService.getParentNodeUuid(newNode.getId()).orElse(null);
        notificationService.emitNodeInserted(study.getId(), parentUuid, newNode.getId(), insertMode, nodeId);
        // userId is null when creating initial nodes, we don't need to send element update notifications in this case
        if (userId != null) {
            notificationService.emitElementUpdated(study.getId(), userId);
        }
        return newNode;
    }

    public void restoreNodes(UUID studyId, List<UUID> nodeIds, UUID anchorNodeId, String userId) {
        networkModificationTreeService.assertIsRootOrConstructionNode(anchorNodeId);
        networkModificationTreeService.restoreNode(studyId, nodeIds, anchorNodeId);
        notificationService.emitElementUpdated(studyId, userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> getRootNetworksToBuildNewNode(UUID studyUuid, UUID parentNodeUuid, NetworkModificationNode newNode) {
        if (!newNode.isSecurityNode() || !networkModificationTreeService.isRootOrConstructionNode(parentNodeUuid)) {
            return List.of();
        }
        return rootNetworkService.getStudyRootNetworks(studyUuid).stream().map(RootNetworkEntity::getId).toList();
    }

    public List<Pair<AbstractNode, Integer>> getStashedNodes(UUID studyId) {
        return networkModificationTreeService.getStashedNodes(studyId);
    }

    @Transactional
    public NetworkModificationNode createSequence(UUID studyUuid, UUID parentNodeUuid, NodeSequenceType nodeSequenceType, String userId) {
        StudyEntity study = getStudy(studyUuid);
        networkModificationTreeService.assertIsRootOrConstructionNode(parentNodeUuid);

        NetworkModificationNode newParentNode = networkModificationTreeService.createTreeNodeFromNodeSequence(study, parentNodeUuid, nodeSequenceType);

        notificationService.emitSubtreeInserted(study.getId(), newParentNode.getId(), parentNodeUuid);
        // userId is null when creating initial nodes, we don't need to send element update notifications in this case
        if (userId != null) {
            notificationService.emitElementUpdated(study.getId(), userId);
        }
        return newParentNode;
    }

    @Transactional
    public void stashNode(UUID studyUuid, UUID nodeId, boolean stashChildren, String userId) {
        removeNodesFromAliases(studyUuid, List.of(nodeId), stashChildren);

        AtomicReference<Long> startTime = new AtomicReference<>(null);
        startTime.set(System.nanoTime());

        boolean unbuildChildren = stashChildren || networkModificationTreeService.hasModifications(nodeId, false);
        List<UUID> rootNetworkUuids = rootNetworkService.getStudyRootNetworks(studyUuid).stream()
                .map(RootNetworkEntity::getId)
                .toList();

        if (unbuildChildren) {
            rootNetworkUuids.forEach(rootNetworkId ->
                    networkModificationTreeService.invalidateNodeTree(studyUuid, nodeId, rootNetworkId));
        } else {
            rootNetworkUuids.forEach(rootNetworkId ->
                    networkModificationTreeService.invalidateNode(studyUuid, nodeId, rootNetworkId)
            );
        }

        networkModificationTreeService.doStashNode(nodeId, stashChildren, userId);

        if (startTime.get() != null) {
            LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId, studyUuid,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
        }

        notificationService.emitElementUpdated(studyUuid, userId);
    }

    @Transactional
    public void deleteNodes(UUID studyUuid, List<UUID> nodeIds, boolean deleteChildren, String userId, boolean emitNotification) {
        removeNodesFromAliases(studyUuid, nodeIds, deleteChildren);

        DeleteNodeInfos deleteNodeInfos = new DeleteNodeInfos();
        for (UUID nodeId : nodeIds) {
            AtomicReference<Long> startTime = new AtomicReference<>(null);
            startTime.set(System.nanoTime());

            boolean invalidateChildrenBuild = !deleteChildren && networkModificationTreeService.hasModifications(nodeId, false);
            List<NodeEntity> childrenNodes = networkModificationTreeService.getChildren(nodeId);
            networkModificationTreeService.doDeleteNode(nodeId, deleteChildren, deleteNodeInfos);

            if (invalidateChildrenBuild) {
                childrenNodes.forEach(nodeEntity -> networkModificationTreeService.invalidateNodeTree(studyUuid, nodeEntity.getIdNode()));
            }

            if (startTime.get() != null && LOGGER.isTraceEnabled()) {
                LOGGER.trace("Delete node '{}' of study '{}' : {} seconds", nodeId.toString().replaceAll("[\n\r]", "_"), studyUuid,
                        TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime.get()));
            }
        }

        deleteNodesInfos(deleteNodeInfos, userId);
        if (emitNotification) {
            notificationService.emitElementUpdated(studyUuid, userId);
        }
    }

    private void removeNodesFromAliases(UUID studyUuid, List<UUID> nodeIds, boolean removeChildren) {
        StudyEntity studyEntity = getStudy(studyUuid);
        if (!CollectionUtils.isEmpty(studyEntity.getNodeAliases())) {
            Set<UUID> allNodeIds = new HashSet<>(nodeIds);
            if (removeChildren) {
                nodeIds.forEach(n -> allNodeIds.addAll(networkModificationTreeService.getAllChildrenUuids(n)));
            }
            studyEntity.getNodeAliases().forEach(nodeAliasEmbeddable -> {
                if (nodeAliasEmbeddable.getNodeId() != null && allNodeIds.contains(nodeAliasEmbeddable.getNodeId())) {
                    nodeAliasEmbeddable.setNodeId(null);
                }
            });
        }
    }

    private void deleteNodesInfos(DeleteNodeInfos deleteNodeInfos, String userId) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        futures.add(studyServerExecutionService.runAsync(() -> deleteNodeInfos.getVariantIds().forEach(networkStoreService::deleteVariants)));
        List<UUID> modificationGroupUuids = deleteNodeInfos.getModificationGroupUuids();
        List<UUID> removedNodeUuids = deleteNodeInfos.getRemovedNodeUuids();
        List<Pair<UUID, UUID>> modificationGroupUuidsNodeUuids = IntStream.range(0, modificationGroupUuids.size())
                .mapToObj(index -> Pair.of(modificationGroupUuids.get(index), removedNodeUuids.get(index)))
                .toList();
        futures.add(studyServerExecutionService.runAsync(() -> modificationGroupUuidsNodeUuids.forEach(
                groupUuidNodeUuid -> networkModificationRestService.deleteModificationsByGroup(groupUuidNodeUuid.getFirst(), userId))
        ));
        futures.add(studyServerExecutionService.runAsync(() -> deleteNodeInfos.getRemovedNodeUuids().forEach(dynamicSimulationEventService::deleteEventsByNodeId)));
        futures.addAll(rootNetworkNodeInfoService.getRemoteDeletions(deleteNodeInfos));
        // Do not wait completion and do not throw exception
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }
}
