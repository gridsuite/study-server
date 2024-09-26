/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree;

import org.gridsuite.study.server.networkmodificationtree.dto.*;
import org.gridsuite.study.server.networkmodificationtree.entities.NetworkModificationNodeInfoEntity;
import org.gridsuite.study.server.networkmodificationtree.entities.TimePointNodeInfoEntity;
import org.gridsuite.study.server.repository.networkmodificationtree.NetworkModificationNodeInfoRepository;

import java.util.UUID;

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
        var networkModificationNodeInfoEntity = NetworkModificationNodeInfoEntity.builder()
            .modificationGroupUuid(modificationNode.getModificationGroupUuid())
            .build();
        return completeEntityNodeInfo(node, networkModificationNodeInfoEntity);
    }

    @Override
    public NetworkModificationNode toDto(NetworkModificationNodeInfoEntity node) {
        @SuppressWarnings("unused")
        TimePointNodeInfoEntity timePointNodeStatusEntity = node.getFirstTimePointNodeStatusEntity();
        int ignoreSize = timePointNodeStatusEntity.getModificationsToExclude().size(); // to load the lazy collection
        return completeNodeInfo(node,
            NetworkModificationNode.completeDtoFromTimePointNodeInfo(
                NetworkModificationNode.builder().modificationGroupUuid(node.getModificationGroupUuid()).build(),
                timePointNodeStatusEntity));
    }

    @Override
    public UUID getModificationGroupUuid(AbstractNode node) {
        return ((NetworkModificationNode) node).getModificationGroupUuid();
    }
}
