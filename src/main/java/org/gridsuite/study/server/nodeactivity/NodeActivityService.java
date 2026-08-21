/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NODE_ACTIVITY_CONFLICT;
import static org.gridsuite.study.server.error.StudyBusinessErrorCode.NOT_FOUND;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Service
public class NodeActivityService {

    private final NodeActivityRepository nodeActivityRepository;
    private final NodeRepository nodeRepository;
    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    private final NotificationService notificationService;

    public NodeActivityService(NodeActivityRepository nodeActivityRepository,
                               NodeRepository nodeRepository,
                               NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                               NotificationService notificationService) {
        this.nodeActivityRepository = nodeActivityRepository;
        this.nodeRepository = nodeRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void addNodeActivities(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        doAddActivities(type, studyUuid, rootNetworkUuid, nodeUuids);
    }

    private void doAddActivities(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        List<UUID> nodesUuids = nodeUuids.stream().distinct().toList();
        if (nodesUuids.isEmpty()) {
            return;
        }

        assertNodesExistInStudy(studyUuid, nodesUuids);
        List<NodeActivityEntity> newActivities = nodesUuids.stream()
            .map(nodeUuid -> NodeActivityEntity.from(type, studyUuid, rootNetworkUuid, nodeUuid))
            .toList();

        List<NodeActivityEntity> currentActivities = nodeActivityRepository.findAllByStudyId(studyUuid);
        if (!currentActivities.isEmpty()) {
            assertNoConflict(newActivities, type.invalidatesChildren(), currentActivities);
        }

        nodeActivityRepository.saveAll(newActivities);

        notifyActivities(studyUuid);
    }

    private void assertNoConflict(List<NodeActivityEntity> newActivities, boolean withInvalidatesChildren, List<NodeActivityEntity> currentActivities) {
        if (!currentActivities.isEmpty()) {
            Map<UUID, Set<UUID>> ancestorsByNode = getAncestorsByNode(newActivities, withInvalidatesChildren, currentActivities);
            newActivities.forEach(newActivity -> findConflictualCurrentActivity(newActivity, currentActivities, ancestorsByNode)
                .ifPresent(conflictualActivity -> {
                    throw throwConflict(newActivity, conflictualActivity);
                }));
        }
    }

    private static Optional<NodeActivityEntity> findConflictualCurrentActivity(
        NodeActivityEntity activity, List<NodeActivityEntity> currentActivities, Map<UUID, Set<UUID>> ancestorsByNode) {
        return currentActivities.stream()
            .filter(
                currentActivity -> activity.hasConflictWith(currentActivity, ancestorsByNode)
            )
            .findFirst();
    }

    private StudyException throwConflict(NodeActivityEntity newActivityEntity, NodeActivityEntity conflictingActivity) {
        String requestedNodeName = getNodeName(newActivityEntity.getNodeId());
        String conflictingNodeName = getNodeName(conflictingActivity.getNodeId());
        return new StudyException(NODE_ACTIVITY_CONFLICT,
            "%s on node %s refused: %s is running on node %s"
                .formatted(newActivityEntity.getType(), newActivityEntity.getNodeId(), conflictingActivity.getType(), conflictingActivity.getNodeId()),
            Map.of("requestedLabel", newActivityEntity.getType().getLabel().name(),
                   "requestedNodeName", requestedNodeName,
                   "requestedOnRootNode", String.valueOf(requestedNodeName.isEmpty()),
                   "label", conflictingActivity.getType().getLabel().name(),
                   "nodeName", conflictingNodeName,
                   "onRootNode", String.valueOf(conflictingNodeName.isEmpty())));
    }

    private String getNodeName(UUID nodeUuid) {
        return networkModificationNodeInfoRepository.findById(nodeUuid)
            .map(NetworkModificationNodeInfoEntity::getName)
            .orElse("");
    }

    private void assertNodesExistInStudy(UUID studyUuid, List<UUID> nodes) {
        if (nodeRepository.countByIdNodeInAndStudyId(nodes, studyUuid) != nodes.size()) {
            throw new StudyException(NOT_FOUND, "Activity creation : nodes %s not all found in study %s".formatted(nodes, studyUuid));
        }
    }

    @Transactional
    public void removeActivities(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        doRemoveActivities(studyUuid, rootNetworkUuid, nodeUuids);
    }

    private void doRemoveActivities(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        if (rootNetworkUuid == null) {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkIdIsNull(nodeUuids);
        } else {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkId(nodeUuids, rootNetworkUuid);
        }
        notifyActivities(studyUuid);
    }

    private void notifyActivities(UUID studyUuid) {
        notificationService.emitNodeActivitiesUpdated(studyUuid, () -> doGetActivities(studyUuid));
    }

    @Transactional(readOnly = true)
    public List<NodeActivityInfos> getActivities(UUID studyUuid) {
        return doGetActivities(studyUuid);
    }

    private List<NodeActivityInfos> doGetActivities(UUID studyUuid) {
        return nodeActivityRepository.findAllByStudyId(studyUuid).stream()
            .map(NodeActivityInfos::from)
            .toList();
    }

    private Map<UUID, Set<UUID>> getAncestorsByNode(List<NodeActivityEntity> newActivities,
                                                    boolean withInvalidatesChildren,
                                                    List<NodeActivityEntity> currentActivities) {
        Stream<NodeActivityEntity> involvedActivities = withInvalidatesChildren
            ? Stream.concat(newActivities.stream(), currentActivities.stream()) : newActivities.stream();
        return involvedActivities.map(NodeActivityEntity::getNodeId).distinct()
            .collect(Collectors.toMap(nodeUuid -> nodeUuid, this::getAncestors));
    }

    private Set<UUID> getAncestors(UUID nodeUuid) {
        return new HashSet<>(nodeRepository.findAllAncestorsUuids(nodeUuid));
    }

    @Transactional
    public List<NodeActivityEntity> removeActivities(Instant startedBefore) {
        List<NodeActivityEntity> nodeActivities = nodeActivityRepository.findAllByStartedAtBefore(startedBefore);
        if (nodeActivities.isEmpty()) {
            return List.of();
        }
        nodeActivityRepository.deleteAllInBatch(nodeActivities);
        nodeActivities.stream()
            .map(NodeActivityEntity::getStudyId)
            .distinct()
            .forEach(this::notifyActivities);
        return nodeActivities;
    }
}
