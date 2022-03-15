/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.study.server.StudyService;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.ModelNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.ModelNodeInfoRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class ModelNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<ModelNodeInfoEntity, ModelNodeInfoRepository, ModelNode> {
    public ModelNodeInfoRepositoryProxy(ModelNodeInfoRepository modelNodeInfoRepository) {
        super(modelNodeInfoRepository);
    }

    @Override
    public void createNodeInfo(AbstractNode nodeInfo) {
        ModelNode modelNode = (ModelNode) nodeInfo;
        if (Objects.isNull(modelNode.getBuildStatus())) {
            modelNode.setBuildStatus(BuildStatus.NOT_BUILT);
        }
        super.createNodeInfo(modelNode);
    }

    @Override
    public ModelNodeInfoEntity toEntity(AbstractNode node) {
        ModelNode modelNode = (ModelNode) node;
        var modelNodeInfoEntity = new ModelNodeInfoEntity(modelNode.getModel(),
            modelNode.getLoadFlowStatus(),
            StudyService.toEntity(modelNode.getLoadFlowResult()),
            modelNode.getSecurityAnalysisResultUuid(),
            modelNode.getBuildStatus());
        return completeEntityNodeInfo(node, modelNodeInfoEntity);
    }

    @Override
    public ModelNode toDto(ModelNodeInfoEntity node) {
        return completeNodeInfo(node, new ModelNode(node.getModel(),
            node.getLoadFlowStatus(),
            StudyService.fromEntity(node.getLoadFlowResult()),
            node.getSecurityAnalysisResultUuid(),
            node.getBuildStatus()));
    }

    @Override
    public LoadFlowStatus getLoadFlowStatus(AbstractNode node) {
        LoadFlowStatus status = ((ModelNode) node).getLoadFlowStatus();
        return status != null ? status : LoadFlowStatus.NOT_DONE;
    }

    @Override
    public void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
        ModelNode modificationNode = (ModelNode) node;
        modificationNode.setLoadFlowResult(loadFlowResult);
        modificationNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(modificationNode);
    }

    @Override
    //TODO overloading this method here is kind of a hack of the whole philosophy
    //of the AbstractNodeRepositoryProxy. It's done to avoid converting from entities
    //to dtos and back to entitites, which generate spurious database queries:
    //subentities are deleted and reinserted all the time (for example LoadFlowResult)
    //Everything should be refactored to use the same style as this method: load entities,
    //modify them and let jpa flush the changes to the database when the transaction is committed.
    public void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        this.nodeInfoRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(ELEMENT_NOT_FOUND)).setLoadFlowStatus(loadFlowStatus);
    }

    @Override
    public void updateLoadFlowStatus(AbstractNode node, LoadFlowStatus loadFlowStatus) {
        ModelNode modelNode = (ModelNode) node;
        modelNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(modelNode);
    }

    @Override
    public LoadFlowInfos getLoadFlowInfos(AbstractNode node) {
        ModelNode modelNode = (ModelNode) node;
        return LoadFlowInfos.builder().loadFlowStatus(modelNode.getLoadFlowStatus()).loadFlowResult(modelNode.getLoadFlowResult()).build();
    }

    @Override
    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
        ModelNode modelNode = (ModelNode) node;
        modelNode.setSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        updateNode(modelNode);
    }

    @Override
    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return ((ModelNode) node).getSecurityAnalysisResultUuid();
    }

    @Override
    public void updateBuildStatus(AbstractNode node, BuildStatus buildStatus, List<UUID> changedNodes) {
        ModelNode modelNode = (ModelNode) node;
        modelNode.setBuildStatus(buildStatus);
        updateNode(modelNode);
        changedNodes.add(node.getId());
    }

    @Override
    public BuildStatus getBuildStatus(AbstractNode node) {
        return ((ModelNode) node).getBuildStatus();
    }

    @Override
    public void invalidateBuildStatus(AbstractNode node, List<UUID> changedNodes) {
        ModelNode modelNode = (ModelNode) node;
        if (modelNode.getBuildStatus() == BuildStatus.BUILT) {
            updateBuildStatus(modelNode, BuildStatus.BUILT_INVALID, changedNodes);
        }
    }
}
