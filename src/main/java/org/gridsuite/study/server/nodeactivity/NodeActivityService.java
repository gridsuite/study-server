/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import org.gridsuite.study.server.error.StudyException;
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

    public void runWithNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                    List<UUID> nodeUuids, Runnable action) {
        runWithNodeActivity(type, studyUuid, rootNetworkUuid, nodeUuids, asSupplier(action));
    }

    public void runWithNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Runnable action) {
        runWithNodeActivity(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWithNodeActivity(NodeActivityType type, UUID studyUuid, List<UUID> nodeUuids, Supplier<T> action) {
        return runWithNodeActivity(type, studyUuid, null, nodeUuids, action);
    }

    public <T> T runWithNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid,
                                     List<UUID> nodeUuids, Supplier<T> action) {
        UUID activityRootNetworkUuid = type.affectsAllRootNetworks() ? null : rootNetworkUuid;
        self.setNodeActivity(type, studyUuid, activityRootNetworkUuid, nodeUuids);
        boolean succeeded = false;
        try {
            T result = action.get();
            succeeded = true;
            return result;
        } finally {
            if (!succeeded || !type.isRemovedByResultMessage()) {
                self.removeNodeActivity(studyUuid, activityRootNetworkUuid, nodeUuids);
            }
        }
    }

    @Transactional
    public void setNodeActivity(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        List<NodeActivityEntity> requested = nodeUuids.stream()
            .map(nodeUuid -> NodeActivityEntity.from(type, studyUuid, rootNetworkUuid, nodeUuid))
            .toList();
        List<NodeActivityEntity> running = nodeActivityRepository.findAllByStudyId(studyUuid);
        if (!running.isEmpty()) {
            Map<UUID, Set<UUID>> ancestorsByNode = getAncestorsByNode(type, running, nodeUuids);
            requested.forEach(activity -> NodeActivityRules.assertNoConflict(running, activity, ancestorsByNode));
        }
        try {
            nodeActivityRepository.saveAllAndFlush(requested);
        } catch (DataIntegrityViolationException e) {
            // someone wrote the same node between the read above and this insert
            throw new StudyException(NODE_ACTIVITY_CONFLICT,
                "%s refused: another activity started on one of the nodes %s".formatted(type, nodeUuids));
        }
        notificationService.emitNodeActivityUpdated(studyUuid);
    }

    @Transactional
    public void removeNodeActivity(UUID studyUuid, UUID rootNetworkUuid, List<UUID> nodeUuids) {
        if (nodeUuids.isEmpty()) {
            return;
        }
        if (rootNetworkUuid == null) {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkIdIsNull(nodeUuids);
        } else {
            nodeActivityRepository.deleteByNodeIdInAndRootNetworkId(nodeUuids, rootNetworkUuid);
        }
        notificationService.emitNodeActivityUpdated(studyUuid);
    }

    @Transactional(readOnly = true)
    public List<NodeActivityInfos> getNodeActivities(UUID studyUuid) {
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
    public int removeAbandonedNodeActivities(Instant startedBefore) {
        List<NodeActivityEntity> abandonedActivities = nodeActivityRepository.findAllByStartedAtBefore(startedBefore);
        if (abandonedActivities.isEmpty()) {
            return 0;
        }
        nodeActivityRepository.deleteAllInBatch(abandonedActivities);
        abandonedActivities.stream()
            .map(NodeActivityEntity::getStudyId)
            .distinct()
            .forEach(notificationService::emitNodeActivityUpdated);
        return abandonedActivities.size();
    }

    private static <T> Supplier<T> asSupplier(Runnable action) {
        return () -> {
            action.run();
            return null;
        };
    }

}
