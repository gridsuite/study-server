/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.hypothesisTree;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.hypothesisTree.dto.AbstractNode;
import org.gridsuite.study.server.hypothesisTree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.hypothesisTree.repositories.NodeInfoRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public abstract class AbstractNodeRepositoryProxy<NodeInfoEntity extends AbstractNodeInfoEntity, NodeInfoEntityRepository extends NodeInfoRepository<NodeInfoEntity>, NodeDto extends AbstractNode> {
    public static final String WRONG_NODE_TYPE = "Wrong node type, should never happen";

    final NodeInfoEntityRepository nodeInfoRepository;

    public AbstractNodeRepositoryProxy(NodeInfoEntityRepository nodeInfoRepository) {
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
        return toDto(nodeInfoRepository.getOne(id));
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

    @Modifying
    public void updateNode(AbstractNode node) {
        if (nodeInfoRepository.existsById(node.getId())) {
            NodeInfoEntity entity = toEntity(node);
            entity.markNotNew();
            nodeInfoRepository.save(entity);
        } else {
            throw new StudyException(StudyException.Type.ELEMENT_NOT_FOUND);
        }
    }
}
