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
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

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

    @OneToMany(orphanRemoval = true, mappedBy = "nodeInfo")
    protected List<TimePointNodeInfoEntity> timePointNodeInfos;

    //TODO temporary, for now we are only working with one timepoint by study
    @Transient
    public TimePointNodeInfoEntity getFirstTimePointNodeStatusEntity() {
        if (timePointNodeInfos == null || timePointNodeInfos.isEmpty()) {
            return null;
        }
        return timePointNodeInfos.get(0);
    }

    public void addTimePointNodeInfo(TimePointNodeInfoEntity timePointNodeInfoEntity) {
        if (timePointNodeInfos == null) {
            timePointNodeInfos = new ArrayList<>();
        }
        timePointNodeInfoEntity.setNodeInfo(this);
        timePointNodeInfos.add(timePointNodeInfoEntity);
    }
}
