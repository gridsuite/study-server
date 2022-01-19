/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import com.powsybl.loadflow.LoadFlowResult;
import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.dto.LoadFlowInfos;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeInfoRepository;
import org.gridsuite.study.server.utils.PropertyUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public abstract class AbstractNodeRepositoryProxy<NodeInfoEntity extends AbstractNodeInfoEntity, NodeInfoEntityRepository extends NodeInfoRepository<NodeInfoEntity>, NodeDto extends AbstractNode> {

    final NodeInfoEntityRepository nodeInfoRepository;

    protected AbstractNodeRepositoryProxy(NodeInfoEntityRepository nodeInfoRepository) {
        this.nodeInfoRepository = nodeInfoRepository;
    }

    public abstract NodeInfoEntity toEntity(AbstractNode node);

    public abstract NodeDto toDto(NodeInfoEntity node);

    public abstract Optional<String> getVariantId(AbstractNode node, boolean generateId);

    public abstract Optional<UUID> getModificationGroupUuid(AbstractNode node, boolean generateId);

    public abstract LoadFlowStatus getLoadFlowStatus(AbstractNode node);

    public abstract LoadFlowInfos getLoadFlowInfos(AbstractNode node);

    public abstract BuildStatus getBuildStatus(AbstractNode node);

    public abstract void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus);

    public abstract void updateLoadFlowStatus(AbstractNode node, LoadFlowStatus loadFlowStatus);

    public abstract void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid);

    public abstract UUID getSecurityAnalysisResultUuid(AbstractNode node);

    public abstract void updateBuildStatus(AbstractNode node, BuildStatus buildStatus, List<UUID> changedNodes);

    public abstract void invalidateBuildStatus(AbstractNode node, List<UUID> changedNodes);

    public abstract void handleExcludeModification(AbstractNode node, UUID modificationUuid, boolean active);

    public abstract void removeModificationToExclude(AbstractNode node, UUID modificationUuid);

    public void createNodeInfo(AbstractNode nodeInfo) {
        nodeInfoRepository.save(toEntity(nodeInfo));
    }

    public void deleteByNodeId(UUID id) {
        nodeInfoRepository.deleteById(id);
    }

    public NodeDto getNode(UUID id) {
        return toDto(nodeInfoRepository.findById(id).orElseThrow(() -> new StudyException(StudyException.Type.ELEMENT_NOT_FOUND)));
    }

    protected NodeDto completeNodeInfo(AbstractNodeInfoEntity nodeInfoEntity, NodeDto node) {
        node.setId(nodeInfoEntity.getId());
        node.setName(nodeInfoEntity.getName());
        node.setDescription(nodeInfoEntity.getDescription());
        return node;
    }

    protected NodeInfoEntity completeEntityNodeInfo(AbstractNode node, NodeInfoEntity entity) {
        entity.setIdNode(node.getId());
        entity.setName(node.getName());
        entity.setDescription(node.getDescription());
        return entity;
    }

    public void updateNode(AbstractNode node) {
        var persistedNode = getNode(node.getId());
        /* using only DTO values not jpa Entity */
        PropertyUtils.copyNonNullProperties(node, persistedNode);
        var entity = toEntity(persistedNode);
        entity.markNotNew();
        nodeInfoRepository.save(entity);
    }

    public Map<UUID, NodeDto> getAll(Collection<UUID> ids) {
        return nodeInfoRepository.findAllById(ids).stream().map(this::toDto).collect(Collectors.toMap(NodeDto::getId, Function.identity()));
    }

    public void deleteAll(Set<UUID> collect) {
        nodeInfoRepository.deleteByIdNodeIn(collect);
    }

    public Optional<String> getVariantId(UUID nodeUuid, boolean generateId) {
        return getVariantId(getNode(nodeUuid), generateId);
    }

    public Optional<UUID> getModificationGroupUuid(UUID nodeUuid, boolean generateId) {
        return getModificationGroupUuid(getNode(nodeUuid), generateId);
    }

    public LoadFlowStatus getLoadFlowStatus(UUID nodeUuid) {
        return getLoadFlowStatus(getNode(nodeUuid));
    }

    public void updateLoadFlowResultAndStatus(UUID nodeUuid, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
        updateLoadFlowResultAndStatus(getNode(nodeUuid), loadFlowResult, loadFlowStatus);
    }

    public void updateLoadFlowStatus(UUID nodeUuid, LoadFlowStatus loadFlowStatus) {
        updateLoadFlowStatus(getNode(nodeUuid), loadFlowStatus);
    }

    public LoadFlowInfos getLoadFlowInfos(UUID nodeUuid) {
        return getLoadFlowInfos(getNode(nodeUuid));
    }

    public void updateSecurityAnalysisResultUuid(UUID nodeUuid, UUID securityAnalysisResultUuid) {
        updateSecurityAnalysisResultUuid(getNode(nodeUuid), securityAnalysisResultUuid);
    }

    public UUID getSecurityAnalysisResultUuid(UUID nodeUuid) {
        return getSecurityAnalysisResultUuid(getNode(nodeUuid));
    }

    public void updateBuildStatus(UUID nodeUuid, BuildStatus buildStatus, List<UUID> changedNodes) {
        updateBuildStatus(getNode(nodeUuid), buildStatus, changedNodes);
    }

    public BuildStatus getBuildStatus(UUID nodeUuid) {
        return getBuildStatus(getNode(nodeUuid));
    }

    public void invalidateBuildStatus(UUID nodeUuid, List<UUID> changedNodes) {
        invalidateBuildStatus(getNode(nodeUuid), changedNodes);
    }

    public void handleExcludeModification(UUID nodeUuid, UUID modificationUuid, boolean active) {
        handleExcludeModification(getNode(nodeUuid), modificationUuid, active);
    }

    public void removeModificationToExclude(UUID nodeUuid, UUID modificationUuid) {
        removeModificationToExclude(getNode(nodeUuid), modificationUuid);
    }
}
