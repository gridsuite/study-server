/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.dto.ComputationType;
import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNetworkModificationNodeInfoEntity;
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
    public NetworkModificationNodeInfoEntity createNodeInfo(AbstractNode nodeInfo) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) nodeInfo;
        TimePointNetworkModificationNode timePointNodeInfo = networkModificationNode.getFirstTimePointNode();
        if (Objects.isNull(timePointNodeInfo.getNodeBuildStatus())) {
            timePointNodeInfo.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        }
        if (networkModificationNode.getModificationGroupUuid() == null) {
            networkModificationNode.setModificationGroupUuid(UUID.randomUUID());
        }
        if (timePointNodeInfo.getVariantId() == null) {
            timePointNodeInfo.setVariantId(UUID.randomUUID().toString());
        }
        networkModificationNode.setTimePointNetworkModificationNodeList(List.of(
            TimePointNetworkModificationNode.builder()
                .node(networkModificationNode)
                .build())
        );
        return super.createNodeInfo(networkModificationNode);
    }

    @Override
    public NetworkModificationNodeInfoEntity toEntity(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeInfo = modificationNode.getFirstTimePointNode();
        var networkModificationNodeInfoEntity = NetworkModificationNodeInfoEntity.builder()
            .modificationGroupUuid(modificationNode.getModificationGroupUuid())
            .timePointNodeInfos(List.of(TimePointNetworkModificationNodeInfoEntity.builder()
                .variantId(timePointNodeInfo.getVariantId())
                .modificationsToExclude(timePointNodeInfo.getModificationsToExclude())
                .shortCircuitAnalysisResultUuid(timePointNodeInfo.getShortCircuitAnalysisResultUuid())
                .oneBusShortCircuitAnalysisResultUuid(timePointNodeInfo.getOneBusShortCircuitAnalysisResultUuid())
                .loadFlowResultUuid(timePointNodeInfo.getLoadFlowResultUuid())
                .voltageInitResultUuid(timePointNodeInfo.getVoltageInitResultUuid())
                .securityAnalysisResultUuid(timePointNodeInfo.getSecurityAnalysisResultUuid())
                .sensitivityAnalysisResultUuid(timePointNodeInfo.getSensitivityAnalysisResultUuid())
                .nonEvacuatedEnergyResultUuid(timePointNodeInfo.getNonEvacuatedEnergyResultUuid())
                .dynamicSimulationResultUuid(timePointNodeInfo.getDynamicSimulationResultUuid())
                .stateEstimationResultUuid(timePointNodeInfo.getStateEstimationResultUuid())
                .nodeBuildStatus(timePointNodeInfo.getNodeBuildStatus().toEntity()).build()))
            .build();
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        @SuppressWarnings("unused")
        TimePointNetworkModificationNodeInfoEntity timePointNodeStatusEntity = node.getFirstTimePointNodeStatusEntity();
        int ignoreSize = timePointNodeStatusEntity.getModificationsToExclude().size(); // to load the lazy collection
        return completeNodeInfo(node, new NetworkModificationNode(node.getModificationGroupUuid(),
            List.of(
                //TODO: fix it
                timePointNodeStatusEntity.toDto()
            ))
        );
    }

    @Override
    public String getVariantId(AbstractNode node) {
        return ((NetworkModificationNode) node).getFirstTimePointNode().getVariantId();
    }

    @Override
    public UUID getModificationGroupUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getModificationGroupUuid();
    }

    @Override
    public void handleExcludeModification(AbstractNode node, UUID modificationUuid, boolean active) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = networkModificationNode.getFirstTimePointNode();
        if (timePointNodeStatusEntity.getModificationsToExclude() == null) {
            timePointNodeStatusEntity.setModificationsToExclude(new HashSet<>());
        }
        if (!active) {
            timePointNodeStatusEntity.getModificationsToExclude().add(modificationUuid);
        } else {
            timePointNodeStatusEntity.getModificationsToExclude().remove(modificationUuid);
        }
        updateNode(networkModificationNode);
    }

    @Override
    public void removeModificationsToExclude(AbstractNode node, List<UUID> modificationsUuids) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = networkModificationNode.getFirstTimePointNode();
        if (timePointNodeStatusEntity.getModificationsToExclude() != null) {
            modificationsUuids.forEach(timePointNodeStatusEntity.getModificationsToExclude()::remove);
            updateNode(networkModificationNode);
        }
    }

    @Override
    public void updateComputationResultUuid(AbstractNode node, UUID computationUuid, ComputationType computationType) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = modificationNode.getFirstTimePointNode();
        switch (computationType) {
            case LOAD_FLOW -> timePointNodeStatusEntity.setLoadFlowResultUuid(computationUuid);
            case SECURITY_ANALYSIS -> timePointNodeStatusEntity.setSecurityAnalysisResultUuid(computationUuid);
            case SENSITIVITY_ANALYSIS -> timePointNodeStatusEntity.setSensitivityAnalysisResultUuid(computationUuid);
            case NON_EVACUATED_ENERGY_ANALYSIS -> timePointNodeStatusEntity.setNonEvacuatedEnergyResultUuid(computationUuid);
            case SHORT_CIRCUIT -> timePointNodeStatusEntity.setShortCircuitAnalysisResultUuid(computationUuid);
            case SHORT_CIRCUIT_ONE_BUS -> timePointNodeStatusEntity.setOneBusShortCircuitAnalysisResultUuid(computationUuid);
            case VOLTAGE_INITIALIZATION -> timePointNodeStatusEntity.setVoltageInitResultUuid(computationUuid);
            case DYNAMIC_SIMULATION -> timePointNodeStatusEntity.setDynamicSimulationResultUuid(computationUuid);
            case STATE_ESTIMATION -> timePointNodeStatusEntity.setStateEstimationResultUuid(computationUuid);
        }
        updateNode(modificationNode, computationType.getResultUuidLabel());
    }

    @Override
    public UUID getComputationResultUuid(AbstractNode node, ComputationType computationType) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = modificationNode.getFirstTimePointNode();
        return switch (computationType) {
            case LOAD_FLOW -> timePointNodeStatusEntity.getLoadFlowResultUuid();
            case SECURITY_ANALYSIS -> timePointNodeStatusEntity.getSecurityAnalysisResultUuid();
            case SENSITIVITY_ANALYSIS -> timePointNodeStatusEntity.getSensitivityAnalysisResultUuid();
            case NON_EVACUATED_ENERGY_ANALYSIS -> timePointNodeStatusEntity.getNonEvacuatedEnergyResultUuid();
            case SHORT_CIRCUIT -> timePointNodeStatusEntity.getShortCircuitAnalysisResultUuid();
            case SHORT_CIRCUIT_ONE_BUS -> timePointNodeStatusEntity.getOneBusShortCircuitAnalysisResultUuid();
            case VOLTAGE_INITIALIZATION -> timePointNodeStatusEntity.getVoltageInitResultUuid();
            case DYNAMIC_SIMULATION -> timePointNodeStatusEntity.getDynamicSimulationResultUuid();
            case STATE_ESTIMATION -> timePointNodeStatusEntity.getStateEstimationResultUuid();
        };
    }

    private void updateNode(NetworkModificationNode node, List<UUID> changedNodes) {
        updateNode(node);
        changedNodes.add(node.getId());
    }

    @Override
    public void updateNodeBuildStatus(AbstractNode node, NodeBuildStatus nodeBuildStatus, List<UUID> changedNodes) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.getFirstTimePointNode().setNodeBuildStatus(nodeBuildStatus);
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeBuildStatus getNodeBuildStatus(AbstractNode node) {
        return ((NetworkModificationNode) node).getFirstTimePointNode().getNodeBuildStatus();
    }

    @Override
    public void invalidateNodeBuildStatus(AbstractNode node, List<UUID> changedNodes) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = modificationNode.getFirstTimePointNode();
        if (!timePointNodeStatusEntity.getNodeBuildStatus().isBuilt()) {
            return;
        }

        timePointNodeStatusEntity.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        timePointNodeStatusEntity.setVariantId(UUID.randomUUID().toString());
        timePointNodeStatusEntity.setReportUuid(UUID.randomUUID());
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        TimePointNetworkModificationNode timePointNodeStatusEntity = networkModificationNode.getFirstTimePointNode();
        return NodeModificationInfos.builder()
            .id(networkModificationNode.getId())
            .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
            .variantId(timePointNodeStatusEntity.getVariantId())
            .reportUuid(timePointNodeStatusEntity.getReportUuid())
            .loadFlowUuid(timePointNodeStatusEntity.getLoadFlowResultUuid())
            .securityAnalysisUuid(timePointNodeStatusEntity.getSecurityAnalysisResultUuid())
            .sensitivityAnalysisUuid(timePointNodeStatusEntity.getSensitivityAnalysisResultUuid())
            .nonEvacuatedEnergyUuid(timePointNodeStatusEntity.getNonEvacuatedEnergyResultUuid())
            .shortCircuitAnalysisUuid(timePointNodeStatusEntity.getShortCircuitAnalysisResultUuid())
            .oneBusShortCircuitAnalysisUuid(timePointNodeStatusEntity.getOneBusShortCircuitAnalysisResultUuid())
            .voltageInitUuid(timePointNodeStatusEntity.getVoltageInitResultUuid())
            .dynamicSimulationUuid(timePointNodeStatusEntity.getDynamicSimulationResultUuid())
            .stateEstimationUuid(timePointNodeStatusEntity.getStateEstimationResultUuid())
            .build();
    }
}
