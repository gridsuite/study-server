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
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
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
        if (Objects.isNull(networkModificationNode.getNodeBuildStatus())) {
            networkModificationNode.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        }
        if (networkModificationNode.getModificationGroupUuid() == null) {
            networkModificationNode.setModificationGroupUuid(UUID.randomUUID());
        }
        if (networkModificationNode.getVariantId() == null) {
            networkModificationNode.setVariantId(UUID.randomUUID().toString());
        }
        if (networkModificationNode.getReportUuid() == null) {
            networkModificationNode.setReportUuid(UUID.randomUUID());
        }
        return super.createNodeInfo(networkModificationNode);
    }

    @Override
    public NetworkModificationNodeInfoEntity toEntity(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
//        TimePointNetworkModificationNode timePointNodeInfo = modificationNode.getFirstTimePointNode();
        var networkModificationNodeInfoEntity = NetworkModificationNodeInfoEntity.builder()
            .modificationGroupUuid(modificationNode.getModificationGroupUuid())
            .build();
//        networkModificationNodeInfoEntity.setTimePointNodeInfos(
//            List.of(TimePointNodeInfoEntity.builder()
//                .variantId(timePointNodeInfo.getVariantId())
//                .modificationsToExclude(timePointNodeInfo.getModificationsToExclude())
//                .shortCircuitAnalysisResultUuid(timePointNodeInfo.getShortCircuitAnalysisResultUuid())
//                .oneBusShortCircuitAnalysisResultUuid(timePointNodeInfo.getOneBusShortCircuitAnalysisResultUuid())
//                .loadFlowResultUuid(timePointNodeInfo.getLoadFlowResultUuid())
//                .voltageInitResultUuid(timePointNodeInfo.getVoltageInitResultUuid())
//                .securityAnalysisResultUuid(timePointNodeInfo.getSecurityAnalysisResultUuid())
//                .sensitivityAnalysisResultUuid(timePointNodeInfo.getSensitivityAnalysisResultUuid())
//                .nonEvacuatedEnergyResultUuid(timePointNodeInfo.getNonEvacuatedEnergyResultUuid())
//                .dynamicSimulationResultUuid(timePointNodeInfo.getDynamicSimulationResultUuid())
//                .stateEstimationResultUuid(timePointNodeInfo.getStateEstimationResultUuid())
//                .reportUuid(timePointNodeInfo.getReportUuid())
//                .nodeBuildStatus(timePointNodeInfo.getNodeBuildStatus() != null ? timePointNodeInfo.getNodeBuildStatus().toEntity() : null)
//                .nodeInfo(networkModificationNodeInfoEntity)
//                .build()
//            )
//        );
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        @SuppressWarnings("unused")
        TimePointNodeInfoEntity timePointNodeStatusEntity = node.getFirstTimePointNodeStatusEntity();
        int ignoreSize = timePointNodeStatusEntity.getModificationsToExclude().size(); // to load the lazy collection
        return completeNodeInfo(node,
            NetworkModificationNode.completeDtoFromTimePointNodeInfo(
                NetworkModificationNode.builder().modificationGroupUuid(node.getModificationGroupUuid()).build(),
                timePointNodeStatusEntity));
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
            .nonEvacuatedEnergyUuid(networkModificationNode.getNonEvacuatedEnergyResultUuid())
            .shortCircuitAnalysisUuid(networkModificationNode.getShortCircuitAnalysisResultUuid())
            .oneBusShortCircuitAnalysisUuid(networkModificationNode.getOneBusShortCircuitAnalysisResultUuid())
            .voltageInitUuid(networkModificationNode.getVoltageInitResultUuid())
            .dynamicSimulationUuid(networkModificationNode.getDynamicSimulationResultUuid())
            .stateEstimationUuid(networkModificationNode.getStateEstimationResultUuid())
            .build();
    }
}
