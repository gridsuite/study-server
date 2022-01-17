/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class NetworkModificationNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<NetworkModificationNodeInfoEntity, NetworkModificationNodeInfoRepository, NetworkModificationNode> {
    public NetworkModificationNodeInfoRepositoryProxy(NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository) {
        super(networkModificationNodeInfoRepository);
    }

    @Override
    public NetworkModificationNodeInfoEntity toEntity(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        var networkModificationNodeInfoEntity = new NetworkModificationNodeInfoEntity(modificationNode.getNetworkModification(),
                                                                                      modificationNode.getVariantId());
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        return completeNodeInfo(node, new NetworkModificationNode(node.getNetworkModificationId(),
                                                                  node.getVariantId()));
    }

    @Override
    public Optional<String> getVariantId(AbstractNode node, boolean generateId) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        if (networkModificationNode.getVariantId() == null && generateId) {
            networkModificationNode.setVariantId(UUID.randomUUID().toString());  // variant id generated with UUID format ????
            updateNode(networkModificationNode);
        }
        return Optional.ofNullable(networkModificationNode.getVariantId());
    }

    @Override
    public Optional<UUID> getModificationGroupUuid(AbstractNode node, boolean generateId) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        if (networkModificationNode.getNetworkModification() == null && generateId) {
            networkModificationNode.setNetworkModification(UUID.randomUUID());
            updateNode(networkModificationNode);
        }
        return Optional.ofNullable(networkModificationNode.getNetworkModification());
    }

    @Override
    public LoadFlowStatus getLoadFlowStatus(AbstractNode node) {
        return LoadFlowStatus.NOT_DONE;
    }

    @Override
    public void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
        // Do nothing : no loadflow result and status associated to this node
    }

    @Override
    public void updateLoadFlowStatus(AbstractNode node, LoadFlowStatus loadFlowStatus) {
        // Do nothing : no loadflow status associated to this node
    }

    @Override
    public LoadFlowInfos getLoadFlowInfos(AbstractNode node) {
        return LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.NOT_DONE).build();
    }

    @Override
    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
        // Do nothing : no security analysis result associated to this node
    }

    @Override
    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return null;
    }

    @Override
    public void updateBuildStatus(AbstractNode node, BuildStatus buildStatus) {
        // Do nothing : no build status associated to this node
    }

    @Override
    public BuildStatus getBuildStatus(AbstractNode node) {
        return BuildStatus.NOT_BUILT;
    }

    @Override
    public void invalidateBuildStatus(AbstractNode node) {
        // Do nothing : no build status associated to this node
    }
}
