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
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.ModelNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.ModelNodeInfoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class ModelNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<ModelNodeInfoEntity, ModelNodeInfoRepository, ModelNode> {
    public ModelNodeInfoRepositoryProxy(ModelNodeInfoRepository modelNodeInfoRepository) {
        super(modelNodeInfoRepository);
    }

    @Override
    public ModelNodeInfoEntity toEntity(AbstractNode node) {
        var modelNodeInfoEntity = new ModelNodeInfoEntity(((ModelNode) node).getModel());
        return completeEntityNodeInfo(node, modelNodeInfoEntity);

    }

    @Override
    public ModelNode toDto(ModelNodeInfoEntity node) {
        return completeNodeInfo(node, new ModelNode(node.getModel()));
    }

    @Override
    public Optional<String> getVariantId(AbstractNode node, boolean generateId) {
        return Optional.empty();
    }

    @Override
    public Optional<UUID> getModificationGroupUuid(AbstractNode node, boolean generateId) {
        return Optional.empty();
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
    public void updateBuildStatus(AbstractNode node, BuildStatus buildStatus, List<UUID> changedNodes) {
        // Do nothing : no build associated to this node
    }

    @Override
    public BuildStatus getBuildStatus(AbstractNode node) {
        return BuildStatus.NOT_BUILT;
    }

    @Override
    public void invalidateBuildStatus(AbstractNode node, List<UUID> changedNodes) {
        // Do nothing : no build associated to this node
    }

    @Override
    public void handleExcludeModification(AbstractNode node, UUID modificationUuid, boolean active) {
        // Do nothing : no build associated to this node
    }
}
