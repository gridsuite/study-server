/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.dto.InvalidateNodeTreeParameters;
import org.gridsuite.study.server.dto.RootNetworkLoadStatus;
import org.gridsuite.study.server.error.StudyException;
import org.gridsuite.study.server.networkmodificationtree.entities.*;
import org.gridsuite.study.server.notification.NotificationService;
import org.gridsuite.study.server.repository.StudyRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeRepository;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.gridsuite.study.server.error.StudyBusinessErrorCode.*;

/**
 * @author Bassel El Cheikh <bassel.el-cheikh_externe at rte-france.com>
 */

@Service
public class NodeService {
    private final StudyRepository studyRepository;
    private final NodeRepository nodeRepository;
    private final RootNodeInfoRepository rootNodeInfoRepository;
    private final NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository;
    private final NetworkModificationTreeService networkModificationTreeService;
    private final RootNetworkService rootNetworkService;
    private final StudyServerExecutionService studyServerExecutionService;
    private final NotificationService notificationService;

    public NodeService(StudyRepository studyRepository,
                       NodeRepository nodeRepository,
                       RootNodeInfoRepository rootNodeInfoRepository,
                       NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository,
                       NetworkModificationTreeService networkModificationTreeService,
                       RootNetworkService rootNetworkService, StudyServerExecutionService studyServerExecutionService, NotificationService notificationService) {
        this.studyRepository = studyRepository;
        this.nodeRepository = nodeRepository;
        this.rootNodeInfoRepository = rootNodeInfoRepository;
        this.networkModificationNodeInfoRepository = networkModificationNodeInfoRepository;
        this.networkModificationTreeService = networkModificationTreeService;
        this.rootNetworkService = rootNetworkService;
        this.studyServerExecutionService = studyServerExecutionService;
        this.notificationService = notificationService;
    }

    public void assertIsStudyExist(UUID studyUuid) {
        if (!studyRepository.existsById(studyUuid)) {
            throw new StudyException(NOT_FOUND, "Study not found");
        }
    }

    public void assertIsStudyAndNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsStudyExist(studyUuid);
        assertIsNodeExist(studyUuid, nodeUuid);
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid) {
        assertIsNodeExist(studyUuid, nodeUuid, new StudyException(NOT_FOUND, "Node not found"));
    }

    public void assertIsNodeExist(UUID studyUuid, UUID nodeUuid, StudyException exception) {
        if (!nodeRepository.existsByStudyIdAndNodeId(studyUuid, nodeUuid)) {
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public void assertIsNodeNotReadOnly(UUID nodeUuid) {
        Boolean isReadOnly = isReadOnly(nodeUuid);
        if (Boolean.TRUE.equals(isReadOnly)) {
            throw new StudyException(NOT_ALLOWED);
        }
    }

    public Boolean isReadOnly(UUID nodeUuid) {
        return getNodeInfoEntity(nodeUuid).getReadOnly();
    }

    private NodeEntity getNodeEntity(UUID nodeId) {
        return nodeRepository.findById(nodeId).orElseThrow(() -> new StudyException(NOT_FOUND, "Node not found"));
    }

    public RootNodeInfoEntity getRootNodeInfoEntity(UUID nodeId) {
        return rootNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(NOT_FOUND, "Node not found"));
    }

    private AbstractNodeInfoEntity getNodeInfoEntity(UUID nodeUuid) {
        return getNodeEntity(nodeUuid).getType() == NodeType.ROOT ? getRootNodeInfoEntity(nodeUuid) : getNetworkModificationNodeInfoEntity(nodeUuid);
    }

    private NetworkModificationNodeInfoEntity getNetworkModificationNodeInfoEntity(UUID nodeId) {
        return networkModificationNodeInfoRepository.findById(nodeId).orElseThrow(() -> new StudyException(NODE_NOT_FOUND, "Node not found"));
    }

    @Transactional(readOnly = true)
    public void assertCanRunOnConstructionNode(UUID studyUuid, UUID nodeUuid, List<String> forbiddenProvidersOnConstructionNode, Function<UUID, String> providerGetter) {
        if (networkModificationTreeService.isConstructionNode(nodeUuid)) {
            String provider = providerGetter.apply(studyUuid);
            if (forbiddenProvidersOnConstructionNode.contains(provider)) {
                throw new StudyException(NOT_ALLOWED, provider + " must run only from a security type node !");
            }
        }
    }

    void doUnbuildNodeTree(UUID studyUuid, UUID rootNodeUuid, boolean skipDeleteVariants, @NonNull String userId) {
        InvalidateNodeTreeParameters invalidateNodeTreeParameters = InvalidateNodeTreeParameters.ALL;
        List<UUID> rootNetworkIds = rootNetworkService.getStudyRootNetworkIds(studyUuid);
        List<CompletableFuture<Void>> futures = rootNetworkIds.stream()
                .map(rnId -> studyServerExecutionService.runAsync(() ->
                        networkModificationTreeService.invalidateNodeTree(studyUuid, rootNodeUuid, rnId, invalidateNodeTreeParameters, skipDeleteVariants)))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        boolean noneMatchUnloading = rootNetworkIds.stream()
                .map(rnId -> rootNetworkService.getRootNetwork(rnId).orElseThrow(() -> new StudyException(NOT_FOUND, "Root network not found")))
                .noneMatch(rootNetwork -> rootNetwork.getLoadStatus() == RootNetworkLoadStatus.UNLOADING);
        if (noneMatchUnloading) {
            notificationService.emitElementUpdated(studyUuid, userId);
        }
    }
}
