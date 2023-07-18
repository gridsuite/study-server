/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class NetworkModificationNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<NetworkModificationNodeInfoEntity, NetworkModificationNodeInfoRepository, NetworkModificationNode> {
    public NetworkModificationNodeInfoRepositoryProxy(NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository) {
        super(networkModificationNodeInfoRepository);
    }

    @Override
    public void createNodeInfo(AbstractNode nodeInfo) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) nodeInfo;
        if (Objects.isNull(networkModificationNode.getNodeBuildStatus())) {
            networkModificationNode.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        }
        if (networkModificationNode.getModificationGroupUuid() == null) {
            networkModificationNode.setModificationGroupUuid(UUID.randomUUID());
        }
        if (networkModificationNode.getVariantId() == null) {
            networkModificationNode.setVariantId(UUID.randomUUID().toString());
        }
        super.createNodeInfo(networkModificationNode);
    }

    @Override
    public NetworkModificationNodeInfoEntity toEntity(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        var networkModificationNodeInfoEntity = new NetworkModificationNodeInfoEntity(modificationNode.getModificationGroupUuid(),
            modificationNode.getVariantId(),
            modificationNode.getModificationsToExclude(),
            modificationNode.getShortCircuitAnalysisResultUuid(),
            modificationNode.getLoadFlowResultUuid(),
            modificationNode.getVoltageInitResultUuid(),
            modificationNode.getSecurityAnalysisResultUuid(),
            modificationNode.getSensitivityAnalysisResultUuid(),
            modificationNode.getDynamicSimulationResultUuid(),
            modificationNode.getNodeBuildStatus().toEntity());
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        @SuppressWarnings("unused")
        int ignoreSize = node.getModificationsToExclude().size(); // to load the lazy collection
        return completeNodeInfo(node, new NetworkModificationNode(node.getModificationGroupUuid(),
            node.getVariantId(),
            new HashSet<>(node.getModificationsToExclude()), // Need to create a new set because it is a persistent set (org.hibernate.collection.internal.PersistentSet)
            node.getLoadFlowResultUuid(),
            node.getShortCircuitAnalysisResultUuid(),
            node.getVoltageInitResultUuid(),
            node.getSecurityAnalysisResultUuid(),
            node.getSensitivityAnalysisResultUuid(),
            node.getDynamicSimulationResultUuid(),
            node.getNodeBuildStatus().toDto()));
    }

    @Override
    public String getVariantId(AbstractNode node) {
        return ((NetworkModificationNode) node).getVariantId();
    }

    @Override
    public UUID getModificationGroupUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getModificationGroupUuid();
    }

    @Override
    public void handleExcludeModification(AbstractNode node, UUID modificationUuid, boolean active) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        if (networkModificationNode.getModificationsToExclude() == null) {
            networkModificationNode.setModificationsToExclude(new HashSet<>());
        }
        if (!active) {
            networkModificationNode.getModificationsToExclude().add(modificationUuid);
        } else {
            networkModificationNode.getModificationsToExclude().remove(modificationUuid);
        }
        updateNode(networkModificationNode);
    }

    @Override
    public void removeModificationsToExclude(AbstractNode node, List<UUID> modificationsUuids) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        if (networkModificationNode.getModificationsToExclude() != null) {
            modificationsUuids.forEach(networkModificationNode.getModificationsToExclude()::remove);
            updateNode(networkModificationNode);
        }
    }

    @Override
    public void updateShortCircuitAnalysisResultUuid(AbstractNode node, UUID shortCircuitAnalysisUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setShortCircuitAnalysisResultUuid(shortCircuitAnalysisUuid);
        updateNode(modificationNode, "shortCircuitAnalysisResultUuid");
    }

    @Override
    public void updateLoadFlowResultUuid(AbstractNode node, UUID loadFlowUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setLoadFlowResultUuid(loadFlowUuid);
        updateNode(modificationNode, "loadFlowResultUuid");
    }

    @Override
    public UUID getShortCircuitAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getShortCircuitAnalysisResultUuid();
    }

    @Override
    public UUID getLoadFlowResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getLoadFlowResultUuid();
    }

    @Override
    public void updateVoltageInitResultUuid(AbstractNode node, UUID voltageInitUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setVoltageInitResultUuid(voltageInitUuid);
        updateNode(modificationNode, "voltageInitResultUuid");
    }

    @Override
    public UUID getVoltageInitResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getVoltageInitResultUuid();
    }

    @Override
    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        updateNode(modificationNode, "securityAnalysisResultUuid");
    }

    @Override
    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getSecurityAnalysisResultUuid();
    }

    @Override
    public void updateSensitivityAnalysisResultUuid(AbstractNode node, UUID sensitivityAnalysisResultUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        updateNode(modificationNode, "sensitivityAnalysisResultUuid");
    }

    @Override
    public UUID getSensitivityAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getSensitivityAnalysisResultUuid();
    }

    @Override
    public void updateDynamicSimulationResultUuid(AbstractNode node, UUID dynamicSimulationResultUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setDynamicSimulationResultUuid(dynamicSimulationResultUuid);
        updateNode(modificationNode, "dynamicSimulationResultUuid");
    }

    @Override
    public UUID getDynamicSimulationResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getDynamicSimulationResultUuid();
    }

    private void updateNode(NetworkModificationNode node, List<UUID> changedNodes) {
        updateNode(node);
        changedNodes.add(node.getId());
    }

    @Override
    public void updateNodeBuildStatus(AbstractNode node, NodeBuildStatus nodeBuildStatus, List<UUID> changedNodes) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setNodeBuildStatus(nodeBuildStatus);
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeBuildStatus getNodeBuildStatus(AbstractNode node) {
        return ((NetworkModificationNode) node).getNodeBuildStatus();
    }

    @Override
    public void invalidateNodeBuildStatus(AbstractNode node, List<UUID> changedNodes) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        if (!modificationNode.getNodeBuildStatus().isBuilt()) {
            return;
        }

        modificationNode.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        modificationNode.setVariantId(UUID.randomUUID().toString());
        modificationNode.setReportUuid(UUID.randomUUID());
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        return NodeModificationInfos.builder()
            .id(networkModificationNode.getId())
            .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
            .variantId(networkModificationNode.getVariantId())
            .reportUuid(networkModificationNode.getReportUuid())
            .loadFlowUuid(networkModificationNode.getLoadFlowResultUuid())
            .securityAnalysisUuid(networkModificationNode.getSecurityAnalysisResultUuid())
            .sensitivityAnalysisUuid(networkModificationNode.getSensitivityAnalysisResultUuid())
            .shortCircuitAnalysisUuid(networkModificationNode.getShortCircuitAnalysisResultUuid())
            .voltageInitUuid(networkModificationNode.getVoltageInitResultUuid())
            .dynamicSimulationUuid(networkModificationNode.getDynamicSimulationResultUuid())
            .build();
    }
}
