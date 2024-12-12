/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "NetworkModificationNodeInfo")
public class NetworkModificationNodeInfoEntity extends AbstractNodeInfoEntity {

    @Column
    private UUID modificationGroupUuid;

    @OneToMany(orphanRemoval = true, mappedBy = "nodeInfo", cascade = CascadeType.ALL)
    @Builder.Default
    protected List<RootNetworkNodeInfoEntity> rootNetworkNodeInfos = new ArrayList<>();

    //TODO temporary, for now we are only working with one root network by study
    public RootNetworkNodeInfoEntity getFirstRootNetworkNodeInfosEntity() {
        return rootNetworkNodeInfos.stream().findFirst().orElse(null);
    }

    public void addRootNetworkNodeInfo(RootNetworkNodeInfoEntity rootNetworkNodeInfoEntity) {
        rootNetworkNodeInfoEntity.setNodeInfo(this);
        rootNetworkNodeInfos.add(rootNetworkNodeInfoEntity);
    }
}
