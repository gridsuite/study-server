/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.StudyException;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.entities.AbstractNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NodeInfoRepository;

import java.util.*;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public abstract class AbstractNodeRepositoryProxy<T extends AbstractNodeInfoEntity, R extends NodeInfoRepository<T>, U extends AbstractNode> {

    final R nodeInfoRepository;

    protected AbstractNodeRepositoryProxy(R nodeInfoRepository) {
        this.nodeInfoRepository = nodeInfoRepository;
    }

    public abstract U toDto(T node);

    public void deleteByNodeId(UUID id) {
        nodeInfoRepository.deleteById(id);
    }

    public U getNode(UUID id) {
        return toDto(nodeInfoRepository.findById(id).orElseThrow(() -> new StudyException(StudyException.Type.ELEMENT_NOT_FOUND)));
    }

    protected U completeNodeInfo(AbstractNodeInfoEntity nodeInfoEntity, U node) {
        node.setId(nodeInfoEntity.getId());
        node.setName(nodeInfoEntity.getName());
        node.setDescription(nodeInfoEntity.getDescription());
        node.setColumnPosition(nodeInfoEntity.getColumnPosition());
        node.setReadOnly(nodeInfoEntity.getReadOnly());
        return node;
    }

    public List<U> getAll(Collection<UUID> ids) {
        return nodeInfoRepository.findAllById(ids).stream().map(this::toDto).toList();
    }

    public List<U> getAllInOrder(List<UUID> ids) {
        ArrayList<U> res = new ArrayList<>();
        ids.forEach(nodeId -> res.add(nodeInfoRepository.findById(nodeId).map(this::toDto).orElseThrow()));
        return res;
    }

    public void deleteAll(Set<UUID> collect) {
        nodeInfoRepository.deleteByIdNodeIn(collect);
    }

    public Boolean isReadOnly(UUID nodeUuid) {
        return nodeInfoRepository.findById(nodeUuid).orElseThrow(() -> new StudyException(StudyException.Type.NODE_NOT_FOUND)).getReadOnly();
    }
}
