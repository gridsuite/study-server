/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.study.server.StudyService;
import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class RootNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<RootNodeInfoEntity, RootNodeInfoRepository, RootNode> {
    public RootNodeInfoRepositoryProxy(RootNodeInfoRepository rootNodeInfoRepository) {
        super(rootNodeInfoRepository);
    }

    @Override
    public void createNodeInfo(AbstractNode nodeInfo) {
        RootNode rootNode = (RootNode) nodeInfo;
        rootNode.setBuildStatus(BuildStatus.NOT_BUILT);
        super.createNodeInfo(rootNode);
    }

    @Override
    public RootNodeInfoEntity toEntity(AbstractNode node) {
        RootNode rootNode = (RootNode) node;
        var rootNodeInfoEntity = new RootNodeInfoEntity();
        rootNodeInfoEntity.setNetworkModificationId(rootNode.getNetworkModification());
        rootNodeInfoEntity.setLoadFlowStatus(rootNode.getLoadFlowStatus());
        rootNodeInfoEntity.setLoadFlowResult(StudyService.toEntity(rootNode.getLoadFlowResult()));
        rootNodeInfoEntity.setSecurityAnalysisResultUuid(rootNode.getSecurityAnalysisResultUuid());
        rootNodeInfoEntity.setBuildStatus(rootNode.getBuildStatus());
        rootNodeInfoEntity.setIdNode(node.getId());
        rootNodeInfoEntity.setName("Root");
        return rootNodeInfoEntity;
    }

    @Override
    public RootNode toDto(RootNodeInfoEntity node) {
        return completeNodeInfo(node, new RootNode(null,
                                                   node.getNetworkModificationId(),
                                                   node.getLoadFlowStatus(),
                                                   StudyService.fromEntity(node.getLoadFlowResult()),
                                                   node.getSecurityAnalysisResultUuid(),
                                                   node.getBuildStatus()));
    }

    @Override
    public Optional<String> getVariantId(AbstractNode node, boolean generateId) {
        return Optional.of("");  // we will use the network initial variant
    }

    @Override
    public Optional<UUID> getModificationGroupUuid(AbstractNode node, boolean generateId) {
        RootNode rootNode = (RootNode) node;
        if (rootNode.getNetworkModification() == null && generateId) {
            rootNode.setNetworkModification(UUID.randomUUID());
            updateNode(rootNode);
        }
        return Optional.ofNullable(rootNode.getNetworkModification());
    }

    @Override
    public LoadFlowStatus getLoadFlowStatus(AbstractNode node) {
        LoadFlowStatus status = ((RootNode) node).getLoadFlowStatus();
        return status != null ? status : LoadFlowStatus.NOT_DONE;
    }

    @Override
    public void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
        RootNode rootNode = (RootNode) node;
        rootNode.setLoadFlowResult(loadFlowResult);
        rootNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(rootNode);
    }

    @Override
    public void updateLoadFlowStatus(AbstractNode node, LoadFlowStatus loadFlowStatus) {
        RootNode rootNode = (RootNode) node;
        rootNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(rootNode);
    }

    @Override
    public LoadFlowInfos getLoadFlowInfos(AbstractNode node) {
        RootNode rootNode = (RootNode) node;
        return LoadFlowInfos.builder().loadFlowStatus(rootNode.getLoadFlowStatus()).loadFlowResult(rootNode.getLoadFlowResult()).build();
    }

    @Override
    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
        RootNode rootNode = (RootNode) node;
        rootNode.setSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        updateNode(rootNode);
    }

    @Override
    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return ((RootNode) node).getSecurityAnalysisResultUuid();
    }

    @Override
    public void updateBuildStatus(AbstractNode node, BuildStatus buildStatus) {
        RootNode rootNode = (RootNode) node;
        rootNode.setBuildStatus(buildStatus);
        updateNode(rootNode);
    }

    @Override
    public BuildStatus getBuildStatus(AbstractNode node) {
        return ((RootNode) node).getBuildStatus();
    }

    @Override
    public void invalidateBuildStatus(AbstractNode node) {
        RootNode rootNode = (RootNode) node;
        if (rootNode.getBuildStatus() == BuildStatus.BUILT) {
            rootNode.setBuildStatus(BuildStatus.BUILT_INVALID);
            updateNode(rootNode);
        }
    }
}
