/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.hypothesisTree;

import org.gridsuite.study.server.hypothesisTree.dto.AbstractNode;
import org.gridsuite.study.server.hypothesisTree.dto.HypothesisNode;
import org.gridsuite.study.server.hypothesisTree.entities.HypothesisNodeInfoEntity;
import org.gridsuite.study.server.hypothesisTree.repositories.HypothesisNodeInfoRepository;

public class HypothesisNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<HypothesisNodeInfoEntity, HypothesisNodeInfoRepository, HypothesisNode> {
    public HypothesisNodeInfoRepositoryProxy(HypothesisNodeInfoRepository hypothesisNodeInfoRepository) {
        super(hypothesisNodeInfoRepository);
    }

    @Override
    public HypothesisNodeInfoEntity toEntity(AbstractNode node) {
        var hypothesisNodeInfoEntity = new HypothesisNodeInfoEntity(((HypothesisNode) node).getHypothesis());
        return completeEntityNodeInfo(node, hypothesisNodeInfoEntity);
    }

    @Override
    public HypothesisNode toDto(HypothesisNodeInfoEntity node) {
        return completeNodeInfo(node, new HypothesisNode(node.getHypothesisId()));
    }

}
