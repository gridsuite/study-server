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
import org.gridsuite.study.server.dto.ShortCircuitStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;
import org.gridsuite.study.server.service.LoadflowService;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.gridsuite.study.server.StudyException.Type.ELEMENT_NOT_FOUND;

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
        if (Objects.isNull(networkModificationNode.getBuildStatus())) {
            networkModificationNode.setBuildStatus(BuildStatus.NOT_BUILT);
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
            modificationNode.getLoadFlowStatus(),
            LoadflowService.toEntity(modificationNode.getLoadFlowResult()),
            modificationNode.getShortCircuitStatus(),
            modificationNode.getShortCircuitAnalysisResultUuid(),
            modificationNode.getSecurityAnalysisResultUuid(),
            modificationNode.getSensitivityAnalysisResultUuid(),
            modificationNode.getBuildStatus());
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        @SuppressWarnings("unused")
        int ignoreSize = node.getModificationsToExclude().size(); // to load the lazy collection
        return completeNodeInfo(node, new NetworkModificationNode(node.getModificationGroupUuid(),
            node.getVariantId(),
            node.getModificationsToExclude(),
            node.getLoadFlowStatus(),
            LoadflowService.fromEntity(node.getLoadFlowResult()),
            node.getShortCircuitStatus(),
            node.getShortCircuitAnalysisResultUuid(),
            node.getSecurityAnalysisResultUuid(),
            node.getSensitivityAnalysisResultUuid(),
            node.getBuildStatus()));
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
    public LoadFlowStatus getLoadFlowStatus(AbstractNode node) {
        LoadFlowStatus status = ((NetworkModificationNode) node).getLoadFlowStatus();
        return status != null ? status : LoadFlowStatus.NOT_DONE;
    }

    @Override
    public void updateLoadFlowResultAndStatus(AbstractNode node, LoadFlowResult loadFlowResult, LoadFlowStatus loadFlowStatus) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setLoadFlowResult(loadFlowResult);
        modificationNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(modificationNode, "loadFlowResult");
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
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setLoadFlowStatus(loadFlowStatus);
        updateNode(modificationNode);
    }

    @Override
    public void updateShortCircuitStatus(AbstractNode node, ShortCircuitStatus shortCircuitStatus) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setShortCircuitStatus(shortCircuitStatus);
        updateNode(modificationNode);
    }

    @Override
    public void updateShortCircuitAnalysisResultUuid(AbstractNode node, UUID shortCircuitAnalysisUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setShortCircuitAnalysisResultUuid(shortCircuitAnalysisUuid);
        updateNode(modificationNode);
    }

    @Override
    public UUID getShortCircuitAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getShortCircuitAnalysisResultUuid();
    }

    @Override
    public LoadFlowInfos getLoadFlowInfos(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        return LoadFlowInfos.builder().loadFlowStatus(modificationNode.getLoadFlowStatus()).loadFlowResult(modificationNode.getLoadFlowResult()).build();
    }

    @Override
    public void updateSecurityAnalysisResultUuid(AbstractNode node, UUID securityAnalysisResultUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setSecurityAnalysisResultUuid(securityAnalysisResultUuid);
        updateNode(modificationNode);
    }

    @Override
    public UUID getSecurityAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getSecurityAnalysisResultUuid();
    }

    @Override
    public void updateSensitivityAnalysisResultUuid(AbstractNode node, UUID sensitivityAnalysisResultUuid) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        modificationNode.setSensitivityAnalysisResultUuid(sensitivityAnalysisResultUuid);
        updateNode(modificationNode);
    }

    @Override
    public UUID getSensitivityAnalysisResultUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getSensitivityAnalysisResultUuid();
    }

    private void updateNode(NetworkModificationNode node, List<UUID> changedNodes) {
        updateNode(node);
        changedNodes.add(node.getId());
    }

    @Override
    public void updateBuildStatus(AbstractNode node, BuildStatus buildStatus, List<UUID> changedNodes) {
        ((NetworkModificationNode) node).setBuildStatus(buildStatus);
        updateNode((NetworkModificationNode) node, changedNodes);
    }

    @Override
    public BuildStatus getBuildStatus(AbstractNode node) {
        return ((NetworkModificationNode) node).getBuildStatus();
    }

    @Override
    public void invalidateBuildStatus(AbstractNode node, List<UUID> changedNodes) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        if (modificationNode.getBuildStatus() != BuildStatus.BUILT) {
            return;
        }

        modificationNode.setBuildStatus(BuildStatus.NOT_BUILT);
        modificationNode.setVariantId(UUID.randomUUID().toString());
        modificationNode.setReportUuid(UUID.randomUUID());
        updateNode(modificationNode, changedNodes);
    }

    @Override
    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
        return NodeModificationInfos.builder()
            .modificationGroupUuid(networkModificationNode.getModificationGroupUuid())
            .variantId(networkModificationNode.getVariantId())
            .reportUuid(networkModificationNode.getReportUuid())
            .build();
    }
}
