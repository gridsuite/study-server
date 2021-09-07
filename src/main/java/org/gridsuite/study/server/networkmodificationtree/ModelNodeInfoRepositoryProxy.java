/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.ModelNode;
import org.gridsuite.study.server.networkmodificationtree.entities.ModelNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.ModelNodeInfoRepository;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class ModelNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<ModelNodeInfoEntity, ModelNodeInfoRepository, ModelNode> {
    public ModelNodeInfoRepositoryProxy(ModelNodeInfoRepository rootNodeInfoRepository) {
        super(rootNodeInfoRepository);
    }

    @Override
    public ModelNodeInfoEntity toEntity(AbstractNode node) {
        var modelNodeInfoEntity = new ModelNodeInfoEntity(((ModelNode) node).getModel());
        return completeEntityNodeInfo(node, modelNodeInfoEntity);

    }

    @Override
    public ModelNode toDto(ModelNodeInfoEntity node) {
        return completeNodeInfo(node, new ModelNode(node.getModel()));
    }

}
