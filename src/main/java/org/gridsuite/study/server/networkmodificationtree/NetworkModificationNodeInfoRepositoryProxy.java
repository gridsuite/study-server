/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            modificationNode.getOneBusShortCircuitAnalysisResultUuid(),
            modificationNode.getLoadFlowResultUuid(),
            modificationNode.getVoltageInitResultUuid(),
            modificationNode.getSecurityAnalysisResultUuid(),
            modificationNode.getSensitivityAnalysisResultUuid(),
            modificationNode.getNonEvacuatedEnergyResultUuid(),
            modificationNode.getDynamicSimulationResultUuid(),
            modificationNode.getStateEstimationResultUuid(),
            modificationNode.getNodeBuildStatus().toEntity(),
            modificationNode.getComputationsReports(),
            modificationNode.getModificationReports());
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
            node.getOneBusShortCircuitAnalysisResultUuid(),
            node.getVoltageInitResultUuid(),
            node.getSecurityAnalysisResultUuid(),
            node.getSensitivityAnalysisResultUuid(),
            node.getNonEvacuatedEnergyResultUuid(),
            node.getDynamicSimulationResultUuid(),
            node.getStateEstimationResultUuid(),
            node.getNodeBuildStatus().toDto(),
            node.getComputationReports(),
            node.getModificationReports()));
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
    public void updateComputationReportUuid(AbstractNode node, UUID reportUuid, ComputationType computationType) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.getComputationsReports().put(computationType.name(), reportUuid);
        updateNode(modificationNode);
    }

    @Override
    public Map<String, UUID> getComputationReports(AbstractNode node) {
        return ((NetworkModificationNode) node).getComputationsReports();
    }

    @Override
    public Map<UUID, UUID> getModificationReports(AbstractNode node) {
        return ((NetworkModificationNode) node).getModificationReports();
    }

    @Override
    public UUID getReportUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getModificationReports().get(node.getId());
    }

    @Override
    public void setModificationReports(AbstractNode node, Map<UUID, UUID> modificationReports) {
        ((NetworkModificationNode) node).setModificationReports(modificationReports);
        updateNode(node);
    }

    @Override
    public void setComputationsReports(AbstractNode node, Map<String, UUID> computationReports) {
        ((NetworkModificationNode) node).setComputationsReports(computationReports);
        updateNode(node);
    }

    @Override
    public void updateComputationResultUuid(AbstractNode node, UUID computationUuid, ComputationType computationType) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        switch (computationType) {
            case LOAD_FLOW -> modificationNode.setLoadFlowResultUuid(computationUuid);
            case SECURITY_ANALYSIS -> modificationNode.setSecurityAnalysisResultUuid(computationUuid);
            case SENSITIVITY_ANALYSIS -> modificationNode.setSensitivityAnalysisResultUuid(computationUuid);
            case NON_EVACUATED_ENERGY_ANALYSIS -> modificationNode.setNonEvacuatedEnergyResultUuid(computationUuid);
            case SHORT_CIRCUIT -> modificationNode.setShortCircuitAnalysisResultUuid(computationUuid);
            case SHORT_CIRCUIT_ONE_BUS -> modificationNode.setOneBusShortCircuitAnalysisResultUuid(computationUuid);
            case VOLTAGE_INITIALIZATION -> modificationNode.setVoltageInitResultUuid(computationUuid);
            case DYNAMIC_SIMULATION -> modificationNode.setDynamicSimulationResultUuid(computationUuid);
            case STATE_ESTIMATION -> modificationNode.setStateEstimationResultUuid(computationUuid);
        }
        updateNode(modificationNode, computationType.getResultUuidLabel());
    }

    @Override
    public UUID getComputationResultUuid(AbstractNode node, ComputationType computationType) {
        return switch (computationType) {
            case LOAD_FLOW -> ((NetworkModificationNode) node).getLoadFlowResultUuid();
            case SECURITY_ANALYSIS -> ((NetworkModificationNode) node).getSecurityAnalysisResultUuid();
            case SENSITIVITY_ANALYSIS -> ((NetworkModificationNode) node).getSensitivityAnalysisResultUuid();
            case NON_EVACUATED_ENERGY_ANALYSIS -> ((NetworkModificationNode) node).getNonEvacuatedEnergyResultUuid();
            case SHORT_CIRCUIT -> ((NetworkModificationNode) node).getShortCircuitAnalysisResultUuid();
            case SHORT_CIRCUIT_ONE_BUS -> ((NetworkModificationNode) node).getOneBusShortCircuitAnalysisResultUuid();
            case VOLTAGE_INITIALIZATION -> ((NetworkModificationNode) node).getVoltageInitResultUuid();
            case DYNAMIC_SIMULATION -> ((NetworkModificationNode) node).getDynamicSimulationResultUuid();
            case STATE_ESTIMATION -> ((NetworkModificationNode) node).getStateEstimationResultUuid();
        };
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
        modificationNode.setModificationReports(Map.of(modificationNode.getId(), UUID.randomUUID()));
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        return NodeModificationInfos.builder()
            .id(networkModificationNode.getId())
            .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
            .variantId(networkModificationNode.getVariantId())
            .loadFlowUuid(networkModificationNode.getLoadFlowResultUuid())
            .securityAnalysisUuid(networkModificationNode.getSecurityAnalysisResultUuid())
            .sensitivityAnalysisUuid(networkModificationNode.getSensitivityAnalysisResultUuid())
            .nonEvacuatedEnergyUuid(networkModificationNode.getNonEvacuatedEnergyResultUuid())
            .shortCircuitAnalysisUuid(networkModificationNode.getShortCircuitAnalysisResultUuid())
            .oneBusShortCircuitAnalysisUuid(networkModificationNode.getOneBusShortCircuitAnalysisResultUuid())
            .voltageInitUuid(networkModificationNode.getVoltageInitResultUuid())
            .dynamicSimulationUuid(networkModificationNode.getDynamicSimulationResultUuid())
            .stateEstimationUuid(networkModificationNode.getStateEstimationResultUuid())
            .reportUuid(networkModificationNode.getModificationReports().get(networkModificationNode.getId()))
            .nodeType(networkModificationNode.getType())
            .build();
    }
}
