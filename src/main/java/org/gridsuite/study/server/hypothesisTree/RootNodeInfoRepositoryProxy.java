/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.hypothesisTree;

import org.gridsuite.study.server.hypothesisTree.dto.AbstractNode;
import org.gridsuite.study.server.hypothesisTree.dto.RootNode;
import org.gridsuite.study.server.hypothesisTree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.hypothesisTree.repositories.RootNodeInfoRepository;

import java.util.Optional;
import java.util.UUID;

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
        //return new RootNodeInfoEntity(nodeEntity.getIdNode(), nodeEntity, "Root", "", ((RootNode) node).getStudyId());
    }

    @Override
    public RootNode toDto(RootNodeInfoEntity node) {
        return completeNodeInfo(node, new RootNode(node.getStudyId()));
    }

    public Optional<RootNode> getByStudyId(UUID studyId) {
        return nodeInfoRepository.findFirstByStudyId(studyId).map(this::toDto);
    }

}
