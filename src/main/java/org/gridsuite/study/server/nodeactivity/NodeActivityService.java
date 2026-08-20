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
    public void setNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        doSetNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids);
    }

    private void doSetNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        List<UUID> nodes = nodeUuids.stream().distinct().toList();
        if (nodes.isEmpty()) {
            return;
        }
        assertNodesExistInStudy(studyUuid, nodes);
        List<NodeActivityEntity> newActivities = nodes.stream()
            .map(nodeUuid -> NodeActivityEntity.from(type, studyUuid, rootNetworkUuid, nodeUuid))
            .toList();
        List<NodeActivityEntity> currentActivities = nodeActivityRepository.findAllByStudyId(studyUuid);
        if (!currentActivities.isEmpty()) {
            Map<UUID, Set<UUID>> ancestorsByNode = getAncestorsByNode(type, currentActivities, nodes);
            newActivities.forEach(newActivity -> NodeActivityRules.findActivityConflict(currentActivities, newActivity, ancestorsByNode)
                .ifPresent(conflictingActivity -> {
                    throw throwConflict(newActivity, conflictingActivity);
                }));
        }
        nodeActivityRepository.saveAll(newActivities);
        notifyNodeActivities(studyUuid);
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
            throw new StudyException(NOT_FOUND, "Nodes %s not all found in study %s".formatted(nodes, studyUuid));
        }
    }

    @Transactional
    public void removeNodeActivity(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        doRemoveNodeActivity(studyUuid, rootNetworkUuid, nodeUuids);
    }

    private void doRemoveNodeActivity(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        if (rootNetworkUuid == null) {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkIdIsNull(nodeUuids);
        } else {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkId(nodeUuids, rootNetworkUuid);
        }
        notifyNodeActivities(studyUuid);
    }

    private void notifyNodeActivities(UUID studyUuid) {
        notificationService.emitNodeActivityUpdated(studyUuid, () -> doGetNodeActivities(studyUuid));
    }

    @Transactional(readOnly = true)
    public List<NodeActivityInfos> getNodeActivities(UUID studyUuid) {
        return doGetNodeActivities(studyUuid);
    }

    private List<NodeActivityInfos> doGetNodeActivities(UUID studyUuid) {
        return nodeActivityRepository.findAllByStudyId(studyUuid).stream()
            .map(NodeActivityInfos::from)
            .toList();
    }

    private Map<UUID, Set<UUID>> getAncestorsByNode(NodeActivityType requestedType,
                                                    List<NodeActivityEntity> runningActivities, List<UUID> requestedNodes) {
        Stream<UUID> involvedNodes = requestedType.invalidatesChildren()
            ? Stream.concat(requestedNodes.stream(), runningActivities.stream().map(NodeActivityEntity::getNodeId))
            : requestedNodes.stream();
        return involvedNodes.distinct()
            .collect(Collectors.toMap(nodeUuid -> nodeUuid, this::getAncestors));
    }

    private Set<UUID> getAncestors(UUID nodeUuid) {
        return new HashSet<>(nodeRepository.findAllAncestorsUuids(nodeUuid));
    }

    @Transactional
    public List<NodeActivityEntity> removeNodeActivities(Instant startedBefore) {
        List<NodeActivityEntity> nodeActivities = nodeActivityRepository.findAllByStartedAtBefore(startedBefore);
        if (nodeActivities.isEmpty()) {
            return List.of();
        }
        nodeActivityRepository.deleteAllInBatch(nodeActivities);
        nodeActivities.stream()
            .map(NodeActivityEntity::getStudyId)
            .distinct()
            .forEach(this::notifyNodeActivities);
        return nodeActivities;
    }
}
