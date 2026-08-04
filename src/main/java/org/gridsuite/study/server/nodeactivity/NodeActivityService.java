/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.nodeactivity.NodeActivityRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
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
    private final NotificationService notificationService;

    private final NodeActivityService self;

    public NodeActivityService(NodeActivityRepository nodeActivityRepository,
                               NodeRepository nodeRepository,
                               NotificationService notificationService,
                               @Lazy NodeActivityService self) {
        this.nodeActivityRepository = nodeActivityRepository;
        this.nodeRepository = nodeRepository;
        this.notificationService = notificationService;
        this.self = self;
    }

    public void setNodeActivityUntilReturn(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                           List<UUID> nodeUuids, Runnable action) {
        setNodeActivityUntilReturn(type, studyUuid, rootNetworkUuid, nodeUuids, () -> {
            action.run();
            return null;
        });
    }

    public void setNodeActivityUntilReturn(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        setNodeActivityUntilReturn(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T setNodeActivityUntilReturn(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Supplier<T> action) {
        return setNodeActivityUntilReturn(type, studyUuid, null, nodeUuids, action);
    }

    private <T> T setNodeActivityUntilReturn(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                             List<UUID> nodeUuids, Supplier<T> action) {
        self.setNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids);
        try {
            return action.get();
        } finally {
            self.removeNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids);
        }
    }

    public void setNodeActivityUntilResult(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                           List<UUID> nodeUuids, Runnable action) {
        self.setNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids);
        boolean started = false;
        try {
            action.run();
            started = true;
        } finally {
            if (!started) {
                // nothing was launched, so no result message will ever remove the row
                self.removeNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids);
            }
        }
    }

    @Transactional
    public void setNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        List<NodeActivityEntity> runningActivities = nodeActivityRepository.findAllByStudyId(studyUuid);
        if (!runningActivities.isEmpty()) {
            assertNoConflict(runningActivities, type, rootNetworkUuid, nodeUuids,
                getParentsByNode(studyUuid, type, runningActivities, nodeUuids));
        }
        try {
            nodeActivityRepository.saveAllAndFlush(nodeUuids.stream()
                .map(nodeUuid -> NodeActivityEntity.from(type, studyUuid, rootNetworkUuid, nodeUuid))
                .toList());
        } catch (DataIntegrityViolationException e) {
            // someone wrote the same node between the read above and this insert
            throw new StudyException(NODE_ACTIVITY_CONFLICT,
                "%s refused: another activity started on one of the nodes %s".formatted(type, nodeUuids));
        }
        notificationService.emitNodeActivityUpdated(studyUuid);
    }

    public void setNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids) {
        self.setNodeActivity(type, studyUuid, null, nodeUuids);
    }

    @Transactional
    public void removeNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        if (type.isAffectsAllRootNetworks()) {
            nodeActivityRepository.deleteByTypeAndNodeIdInAndRootNetworkIdIsNull(type, nodeUuids);
        } else {
            nodeActivityRepository.deleteByTypeAndNodeIdInAndRootNetworkId(type, nodeUuids, rootNetworkUuid);
        }
        notificationService.emitNodeActivityUpdated(studyUuid);
    }

    public void removeNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids) {
        self.removeNodeActivity(type, studyUuid, null, nodeUuids);
    }

    @Transactional(readOnly = true)
    public List<NodeActivityInfos> getNodeActivities(UUID studyUuid) {
        return nodeActivityRepository.findAllByStudyId(studyUuid).stream()
            .map(NodeActivityInfos::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean isNodeActivityRunning(NodeActivityType type, UUID rootNetworkUuid, UUID nodeUuid) {
        return nodeActivityRepository.existsByTypeAndNodeIdAndRootNetworkId(type, nodeUuid, rootNetworkUuid);
    }

    static void assertNoConflict(List<NodeActivityEntity> runningActivities, NodeActivityType requestedType,
                                 UUID requestedRootNetworkUuid, List<UUID> requestedNodes,
                                 Map<UUID, Set<UUID>> parentsByNode) {
        // either side works on shared data, or both are in the same root network
        List<NodeActivityEntity> activitiesInScope = runningActivities.stream()
            .filter(activity -> requestedType.isAffectsAllRootNetworks() || activity.getType().isAffectsAllRootNetworks()
                || activity.getRootNetworkId().equals(requestedRootNetworkUuid))
            .toList();

        for (UUID requestedNode : requestedNodes) {
            for (NodeActivityEntity runningActivity : activitiesInScope) {
                UUID busyNode = runningActivity.getNodeId();
                if (busyNode.equals(requestedNode)
                        || runningActivity.getType().isInvalidatesChildren()
                           && parentsByNode.get(requestedNode).contains(busyNode)
                        || requestedType.isInvalidatesChildren()
                           && parentsByNode.get(busyNode).contains(requestedNode)) {
                    throw conflict(requestedType, requestedNode, runningActivity);
                }
            }
        }
    }

    private static StudyException conflict(NodeActivityType requestedType, UUID requestedNode,
                                           NodeActivityEntity runningActivity) {
        return new StudyException(NODE_ACTIVITY_CONFLICT, "%s on node %s refused: %s is running on node %s"
            .formatted(requestedType, requestedNode, runningActivity.getType(), runningActivity.getNodeId()));
    }

    private Map<UUID, Set<UUID>> getParentsByNode(UUID studyUuid, NodeActivityType requestedType,
                                                  List<NodeActivityEntity> runningActivities, List<UUID> requestedNodes) {
        UUID rootNodeUuid = getStudyRootNodeUuid(studyUuid);
        Stream<UUID> involvedNodes = requestedType.isInvalidatesChildren()
            ? Stream.concat(requestedNodes.stream(), runningActivities.stream().map(NodeActivityEntity::getNodeId))
            : requestedNodes.stream();
        return involvedNodes.distinct()
            .collect(Collectors.toMap(nodeUuid -> nodeUuid, nodeUuid -> getParents(nodeUuid, rootNodeUuid)));
    }

    private Set<UUID> getParents(UUID nodeUuid, UUID rootNodeUuid) {
        Set<UUID> parents = new HashSet<>(nodeRepository.findAllAncestorsUuids(nodeUuid));
        if (!nodeUuid.equals(rootNodeUuid)) {
            parents.add(rootNodeUuid);
        }
        return parents;
    }

    private UUID getStudyRootNodeUuid(UUID studyUuid) {
        return nodeRepository.findByStudyIdAndType(studyUuid, NodeType.ROOT)
            .orElseThrow(() -> new StudyException(NOT_FOUND, "Root node not found for study " + studyUuid))
            .getIdNode();
    }

    @Transactional
    public int removeAbandonedNodeActivities(Instant startedBefore) {
        List<NodeActivityEntity> abandonedActivities = nodeActivityRepository.findAllByStartedAtBefore(startedBefore);
        if (abandonedActivities.isEmpty()) {
            return 0;
        }
        // only the one whose delete actually removed the rows notifies
        int released = nodeActivityRepository.deleteByStartedAtBefore(startedBefore);
        if (released == 0) {
            return 0;
        }
        abandonedActivities.stream()
            .map(NodeActivityEntity::getStudyId)
            .distinct()
            .forEach(notificationService::emitNodeActivityUpdated);
        return released;
    }
}
