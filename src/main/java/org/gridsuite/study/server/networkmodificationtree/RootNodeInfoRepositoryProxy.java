/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import java.util.UUID;

import org.gridsuite.study.server.dto.NodeModificationInfos;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.RootNode;
import org.gridsuite.study.server.networkmodificationtree.entities.RootNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.RootNodeInfoRepository;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class RootNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<RootNodeInfoEntity, RootNodeInfoRepository, RootNode> {
    public RootNodeInfoRepositoryProxy(RootNodeInfoRepository rootNodeInfoRepository) {
        super(rootNodeInfoRepository);
    }

    @Override
    public RootNodeInfoEntity toEntity(AbstractNode node) {
        var rootNodeInfoEntity = new RootNodeInfoEntity();
        rootNodeInfoEntity.setReportUuid(((RootNode) node).getReportUuid());
        return completeEntityNodeInfo(node, rootNodeInfoEntity);
    }

    @Override
    public RootNode toDto(RootNodeInfoEntity node) {
        return completeNodeInfo(node, new RootNode(null, node.getReportUuid()));
    }

    @Override
    public String getVariantId(AbstractNode node) {
        return "";  // we will use the network initial variant
    }

    @Override
    public NodeModificationInfos getNodeModificationInfos(AbstractNode node) {
        RootNode rootNode = (RootNode) node;
        return NodeModificationInfos.builder()
            .id(rootNode.getId())
            .variantId("")      // we will use the network initial variant
            .reportUuid(rootNode.getReportUuid())
            .nodeType(rootNode.getType())
            .build();
    }

    @Override
    public UUID getReportUuid(AbstractNode node) {
        return ((RootNode) node).getReportUuid();
    }
}
