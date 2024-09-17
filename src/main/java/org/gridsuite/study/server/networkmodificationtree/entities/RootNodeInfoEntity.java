/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@SuperBuilder
@Table(name = "RootNodeInfo")
public class RootNodeInfoEntity extends AbstractNodeInfoEntity<TimePointRootNodeInfoEntity> {

    @Override
    @Transient
    public TimePointRootNodeInfoEntity getFirstTimePointNodeStatusEntity() {
        if (timePointNodeInfos == null || timePointNodeInfos.isEmpty()) {
            return null;
        }
        return timePointNodeInfos.get(0);
    }

    @Override
    public TimePointRootNodeInfoEntity toTimePointNodeInfoEntity(TimePointEntity timePoint) {
        return new TimePointRootNodeInfoEntity(timePoint, this);
    }

    @Override
    public NodeType getType() {
        return NodeType.ROOT;
    }
}
