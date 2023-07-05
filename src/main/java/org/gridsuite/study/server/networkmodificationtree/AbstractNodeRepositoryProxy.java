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
import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeInfoRepository;
import org.gridsuite.study.server.utils.PropertyUtils;

import java.util.*;
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

    public String getVariantId(AbstractNode node) {
        return null;
    }

    public UUID getModificationGroupUuid(AbstractNode node) {
        return null;
    }

    public LoadFlowStatus getLoadFlowStatus(AbstractNode node) {
        return LoadFlowStatus.NOT_DONE;
    }

    public LoadFlowInfos getLoadFlowInfos(AbstractNode node) {
        return LoadFlowInfos.builder().loadFlowStatus(LoadFlowStatus.NOT_DONE).build();
    }

    public BuildStatus getGlobalBuildStatus(AbstractNode node) {
        return BuildStatus.NOT_BUILT;
    }

    public BuildStatus getLocalBuildStatus(AbstractNode node) {
        return BuildStatus.NOT_BUILT;
    }

    public void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
    }

    public void updateLoadFlowStatus(AbstractNode node, LoadFlowStatus loadFlowStatus) {
    }

    public void updateShortCircuitAnalysisResultUuid(AbstractNode node, UUID shortCircuitAnalysisResultUuid) {
    }

    public void updateVoltageInitResultUuid(AbstractNode node, UUID voltageInitResultUuid) {
    }

    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
    }

    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return null;
    }

    public void updateDynamicSimulationResultUuid(AbstractNode node, UUID dynamicSimulationResultUuid) {
    }

    public UUID getDynamicSimulationResultUuid(AbstractNode node) {
        return null;
    }

    public void updateSensitivityAnalysisResultUuid(AbstractNode node, UUID sensitivityAnalysisResultUuid) {
    }

    public UUID getSensitivityAnalysisResultUuid(AbstractNode node) {
        return null;
    }

    public UUID getShortCircuitAnalysisResultUuid(AbstractNode node) {
        return null;
    }

    public UUID getVoltageInitResultUuid(AbstractNode node) {
        return null;
    }

    public void handleExcludeModification(AbstractNode node, UUID modificationUuid, boolean active) {
    }

    public void removeModificationsToExclude(AbstractNode node, List<UUID> modificationUuid) {
    }

    public void updateBuildStatus(AbstractNode node, NodeBuildStatus nodeBuildStatus, List<UUID> changedNodes) {
    }

    public void invalidateBuildStatus(AbstractNode node, List<UUID> changedNodes) {
    }

    public void createNodeInfo(AbstractNode nodeInfo) {
        if (nodeInfo.getReportUuid() == null) {
            nodeInfo.setReportUuid(UUID.randomUUID());
        }
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
        node.setReadOnly(nodeInfoEntity.getReadOnly());
        node.setReportUuid(nodeInfoEntity.getReportUuid());
        return node;
    }

    protected NodeInfoEntity completeEntityNodeInfo(AbstractNode node, NodeInfoEntity entity) {
        entity.setIdNode(node.getId());
        entity.setName(node.getName());
        entity.setDescription(node.getDescription());
        entity.setReadOnly(node.getReadOnly());
        entity.setReportUuid(node.getReportUuid());
        return entity;
    }

    public void updateNode(AbstractNode node, String... authorizedNullProperties) {
        NodeDto persistedNode = getNode(node.getId());
        /* using only DTO values not jpa Entity */
        PropertyUtils.copyNonNullProperties(node, persistedNode, authorizedNullProperties);

        NodeInfoEntity entity = toEntity(persistedNode);
        entity.markNotNew();
        nodeInfoRepository.save(entity);
    }

    public Map<UUID, NodeDto> getAll(Collection<UUID> ids) {
        return nodeInfoRepository.findAllById(ids).stream().map(this::toDto).collect(Collectors.toMap(NodeDto::getId, Function.identity()));
    }

    public void deleteAll(Set<UUID> collect) {
        nodeInfoRepository.deleteByIdNodeIn(collect);
    }

    public String getVariantId(UUID nodeUuid) {
        return getVariantId(getNode(nodeUuid));
    }

    public UUID getModificationGroupUuid(UUID nodeUuid) {
        return getModificationGroupUuid(getNode(nodeUuid));
    }

    public UUID getReportUuid(UUID nodeUuid) {
        return getNode(nodeUuid).getReportUuid();
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

    public void updateShortCircuitAnalysisResultUuid(UUID nodeUuid, UUID shortCircuitAnalysisResultUuid) {
        updateShortCircuitAnalysisResultUuid(getNode(nodeUuid), shortCircuitAnalysisResultUuid);
    }

    public void updateVoltageInitResultUuid(UUID nodeUuid, UUID voltageInitResultUuid) {
        updateVoltageInitResultUuid(getNode(nodeUuid), voltageInitResultUuid);
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

    public void updateDynamicSimulationResultUuid(UUID nodeUuid, UUID dynamicSimulationResultUuid) {
        updateDynamicSimulationResultUuid(getNode(nodeUuid), dynamicSimulationResultUuid);
    }

    public UUID getDynamicSimulationResultUuid(UUID nodeUuid) {
        return getDynamicSimulationResultUuid(getNode(nodeUuid));
    }

    public void updateSensitivityAnalysisResultUuid(UUID nodeUuid, UUID sensitivityAnalysisResultUuid) {
        updateSensitivityAnalysisResultUuid(getNode(nodeUuid), sensitivityAnalysisResultUuid);
    }

    public UUID getSensitivityAnalysisResultUuid(UUID nodeUuid) {
        return getSensitivityAnalysisResultUuid(getNode(nodeUuid));
    }

    public UUID getShortCircuitAnalysisResultUuid(UUID nodeUuid) {
        return getShortCircuitAnalysisResultUuid(getNode(nodeUuid));
    }

    public UUID getVoltageInitResultUuid(UUID nodeUuid) {
        return getVoltageInitResultUuid(getNode(nodeUuid));
    }

    public void updateBuildStatus(UUID nodeUuid, NodeBuildStatus nodeBuildStatus, List<UUID> changedNodes) {
        updateBuildStatus(getNode(nodeUuid), nodeBuildStatus, changedNodes);
    }

    public BuildStatus getGlobalBuildStatus(UUID nodeUuid) {
        return getGlobalBuildStatus(getNode(nodeUuid));
    }

    public BuildStatus getLocalBuildStatus(UUID nodeUuid) {
        return getLocalBuildStatus(getNode(nodeUuid));
    }

    public void invalidateBuildStatus(UUID nodeUuid, List<UUID> changedNodes) {
        invalidateBuildStatus(getNode(nodeUuid), changedNodes);
    }

    public void handleExcludeModification(UUID nodeUuid, UUID modificationUuid, boolean active) {
        handleExcludeModification(getNode(nodeUuid), modificationUuid, active);
    }

    public void removeModificationsToExclude(UUID nodeUuid, List<UUID> modificationUuid) {
        removeModificationsToExclude(getNode(nodeUuid), modificationUuid);
    }

    public Boolean isReadOnly(UUID nodeUuid) {
        return getNode(nodeUuid).getReadOnly();
    }

    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        return null;
    }

    public NodeModificationInfos getNodeModificationInfos(UUID nodeUuid) {
        return getNodeModificationInfos(getNode(nodeUuid));
    }
}
