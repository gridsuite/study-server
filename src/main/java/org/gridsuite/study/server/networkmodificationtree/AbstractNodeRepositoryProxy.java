/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;
import org.gridsuite.study.server.networkmodificationtree.repositories.NodeInfoRepository;

import java.util.Collection;
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

    public void createNodeInfo(AbstractNode nodeInfo) {
        nodeInfoRepository.save(toEntity(nodeInfo));
    }

    public void deleteByNodeId(UUID id) {
        nodeInfoRepository.deleteById(id);
    }

    public NodeDto getNode(UUID id) {
        return toDto(nodeInfoRepository.findById(id).orElseThrow(() -> new StudyException(StudyException.Type.ELEMENT_NOT_FOUND)));
    }

    protected NodeDto completeNodeInfo(AbstractNodeInfoEntity nodeEntity, NodeDto node) {
        node.setId(nodeEntity.getNode().getIdNode());
        node.setName(nodeEntity.getName());
        node.setDescription(nodeEntity.getDescription());
        return node;
    }

    protected NodeInfoEntity completeEntityNodeInfo(AbstractNode node, NodeInfoEntity entity) {
        entity.setIdNode(node.getId());
        entity.setName(node.getName());
        entity.setDescription(node.getDescription());
        return entity;
    }

    public void updateNode(AbstractNode node) {
        if (nodeInfoRepository.existsById(node.getId())) {
            NodeInfoEntity entity = toEntity(node);
            entity.markNotNew();
            nodeInfoRepository.save(entity);
        } else {
            throw new StudyException(StudyException.Type.ELEMENT_NOT_FOUND);
        }
    }

    public Map<UUID, NodeDto> getAll(Collection<UUID> ids) {
        return nodeInfoRepository.findAllById(ids).stream().map(this::toDto).collect(Collectors.toMap(NodeDto::getId, Function.identity()));
    }

    public void deleteAll(Set<UUID> collect) {
        nodeInfoRepository.deleteByIdNodeIn(collect);
    }

    public Optional<String> getVariantId(UUID nodeUuid, boolean generateId) {
        AbstractNode node = getNode(nodeUuid);
        if (node.getType() == NodeType.ROOT) {
            return Optional.of("");  // we will use the network initial variant
        } else if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
            if (networkModificationNode.getVariantId() == null && generateId) {
                networkModificationNode.setVariantId(UUID.randomUUID().toString());  // variant id generated with UUID format ????
                updateNode(networkModificationNode);
            }
            return Optional.ofNullable(networkModificationNode.getVariantId());
        } else {
            return Optional.empty();
        }
    }

    public Optional<UUID> getModificationGroupUuid(UUID nodeUuid, boolean generateId) {
        AbstractNode node = getNode(nodeUuid);
        if (node.getType() == NodeType.ROOT) {
            RootNode rootNode = (RootNode) node;
            if (rootNode.getNetworkModification() == null && generateId) {
                rootNode.setNetworkModification(UUID.randomUUID());
                updateNode(rootNode);
            }
            return Optional.ofNullable(rootNode.getNetworkModification());
        } else if (node.getType() == NodeType.NETWORK_MODIFICATION) {
            NetworkModificationNode networkModificationNode = (NetworkModificationNode) node;
            if (networkModificationNode.getNetworkModification() == null && generateId) {
                networkModificationNode.setNetworkModification(UUID.randomUUID());
                updateNode(networkModificationNode);
            }
            return Optional.ofNullable(networkModificationNode.getNetworkModification());
        } else {
            return Optional.empty();
        }
    }
}
