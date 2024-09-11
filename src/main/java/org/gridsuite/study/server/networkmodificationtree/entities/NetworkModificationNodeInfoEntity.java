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
import org.gridsuite.study.server.repository.timepoint.TimePointEntity;

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
public class NetworkModificationNodeInfoEntity extends AbstractNodeInfoEntity<TimePointNetworkModificationNodeInfoEntity> {
    @Override
    public TimePointNetworkModificationNodeInfoEntity getFirstTimePointNodeStatusEntity() {
        if (timePointNodeStatuses == null || timePointNodeStatuses.isEmpty()) {
            return null;
        }
        return timePointNodeStatuses.get(0);
    }

    @Column
    private UUID modificationGroupUuid;

    @Override
    public TimePointNetworkModificationNodeInfoEntity toTimePointNodeInfoEntity(TimePointEntity timePoint) {
        return new TimePointNetworkModificationNodeInfoEntity(timePoint, this);
    }

    @Override
    public NodeType getType() {
        return NodeType.NETWORK_MODIFICATION;
    }
}
