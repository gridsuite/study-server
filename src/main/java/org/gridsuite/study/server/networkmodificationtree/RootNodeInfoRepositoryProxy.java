/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.RootNodeInfoRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class RootNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<RootNodeInfoEntity, RootNodeInfoRepository, RootNode> {
    public RootNodeInfoRepositoryProxy(RootNodeInfoRepository rootNodeInfoRepository) {
        super(rootNodeInfoRepository);
    }

    @Override
    public RootNodeInfoEntity toEntity(AbstractNode node) {
        var rootNodeInfoEntity = new RootNodeInfoEntity(((RootNode) node).getStudyId());
        rootNodeInfoEntity.setIdNode(node.getId());
        rootNodeInfoEntity.setName("Root");
        return rootNodeInfoEntity;
    }

    @Override
    public RootNode toDto(RootNodeInfoEntity node) {
        return completeNodeInfo(node, new RootNode(node.getStudyId()));
    }

    public Optional<RootNode> getByStudyId(UUID studyId) {
        return nodeInfoRepository.findFirstByStudyId(studyId).map(this::toDto);
    }

}
