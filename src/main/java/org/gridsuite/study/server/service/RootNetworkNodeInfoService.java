/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service;

import lombok.NonNull;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNetworkNodeInfoEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkEntity;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkNodeInfoRepository;
import org.gridsuite.study.server.repository.rootnetwork.RootNetworkRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Slimane amar <slimane.amar at rte-france.com
 */
@Service
public class RootNetworkNodeInfoService {
    private final RootNetworkRepository rootNetworkRepository;
    private final RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository;

    public RootNetworkNodeInfoService(RootNetworkRepository rootNetworkRepository, RootNetworkNodeInfoRepository rootNetworkNodeInfoRepository) {
        this.rootNetworkRepository = rootNetworkRepository;
        this.rootNetworkNodeInfoRepository = rootNetworkNodeInfoRepository;
    }

    public void createRootNetworkLinks(@NonNull UUID studyUuid, @NonNull RootNetworkEntity rootNetworkEntity) {
        // For each network modification node (nodeInfoEntity) create a link with the root network
        // Create a default NetworkModificationNode (replace by the RootNetworkNodeInfo DTO)
        // addLink(nodeInfoEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
    }

    // TODO create a DTO RootNetworkNodeInfo
    public void createNodeLinks(@NonNull UUID studyUuid, @NonNull NetworkModificationNodeInfoEntity modificationNodeInfoEntity, @NonNull NetworkModificationNode rootNetworkNodeInfo) {
        if (Objects.isNull(rootNetworkNodeInfo.getNodeBuildStatus())) {
            rootNetworkNodeInfo.setNodeBuildStatus(NodeBuildStatus.from(BuildStatus.NOT_BUILT));
        }
        if (rootNetworkNodeInfo.getVariantId() == null) {
            rootNetworkNodeInfo.setVariantId(UUID.randomUUID().toString());
        }
        if (rootNetworkNodeInfo.getModificationReports() == null) {
            rootNetworkNodeInfo.setModificationReports(new HashMap<>(Map.of(modificationNodeInfoEntity.getId(), UUID.randomUUID())));
        }

        // For each root network create a link with the node
        rootNetworkRepository.findAllByStudyId(studyUuid).forEach(rootNetworkEntity -> {
            RootNetworkNodeInfoEntity newRootNetworkNodeInfoEntity = RootNetworkNodeInfoEntity.builder()
                    .nodeBuildStatus(rootNetworkNodeInfo.getNodeBuildStatus().toEntity())
                    .variantId(rootNetworkNodeInfo.getVariantId())
                    .dynamicSimulationResultUuid(rootNetworkNodeInfo.getDynamicSimulationResultUuid())
                    .loadFlowResultUuid(rootNetworkNodeInfo.getLoadFlowResultUuid())
                    .nonEvacuatedEnergyResultUuid(rootNetworkNodeInfo.getNonEvacuatedEnergyResultUuid())
                    .securityAnalysisResultUuid(rootNetworkNodeInfo.getSecurityAnalysisResultUuid())
                    .sensitivityAnalysisResultUuid(rootNetworkNodeInfo.getSensitivityAnalysisResultUuid())
                    .oneBusShortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getOneBusShortCircuitAnalysisResultUuid())
                    .shortCircuitAnalysisResultUuid(rootNetworkNodeInfo.getShortCircuitAnalysisResultUuid())
                    .stateEstimationResultUuid(rootNetworkNodeInfo.getStateEstimationResultUuid())
                    .voltageInitResultUuid(rootNetworkNodeInfo.getVoltageInitResultUuid())
                    .computationReports(rootNetworkNodeInfo.getComputationsReports())
                    .modificationReports(rootNetworkNodeInfo.getModificationReports())
                    .build();
            addLink(modificationNodeInfoEntity, rootNetworkEntity, newRootNetworkNodeInfoEntity);
        });
    }

    private void addLink(NetworkModificationNodeInfoEntity nodeInfoEntity, RootNetworkEntity rootNetworkEntity, RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        nodeInfoEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkEntity.addRootNetworkNodeInfo(rootNetworkNodeInfoEntity);
        rootNetworkNodeInfoRepository.save(rootNetworkNodeInfoEntity);
    }
}
