/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.networkmodificationtree.dto.NetworkModificationNode;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.repositories.NetworkModificationNodeInfoRepository;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com
 */
public class NetworkModificationNodeInfoRepositoryProxy extends AbstractNodeRepositoryProxy<NetworkModificationNodeInfoEntity, NetworkModificationNodeInfoRepository, NetworkModificationNode> {
    public NetworkModificationNodeInfoRepositoryProxy(NetworkModificationNodeInfoRepository networkModificationNodeInfoRepository) {
        super(networkModificationNodeInfoRepository);
    }

    @Override
    public NetworkModificationNodeInfoEntity toEntity(AbstractNode node) {
        NetworkModificationNode modificationNode = (NetworkModificationNode) node;
        var networkModificationNodeInfoEntity = new NetworkModificationNodeInfoEntity(modificationNode.getNetworkModification(),
                                                                                      modificationNode.getVariantId());
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        return completeNodeInfo(node, new NetworkModificationNode(node.getNetworkModificationId(), node.getVariantId()));
    }

}
