/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "RootNodeInfo")
public class RootNodeInfoEntity extends AbstractNodeInfoEntity<TimePointRootNodeInfoEntity> {

    @Override
    public TimePointRootNodeInfoEntity getFirstTimePointNodeStatusEntity() {
        if (timePointNodeStatuses == null || timePointNodeStatuses.isEmpty()) {
            return null;
        }
        return timePointNodeStatuses.get(0);
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
