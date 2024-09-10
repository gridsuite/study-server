/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.entities.NodeType;

import java.util.List;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Setter
@EqualsAndHashCode(callSuper = true)
public class NetworkModificationNode extends AbstractNode {
    private List<TimePointNetworkModificationNode> timePointNetworkModificationNodeList;

    @Override
    public TimePointNetworkModificationNode getFirstTimePointNode() {
        return timePointNetworkModificationNodeList.get(0);
    }

    @Override
    public NodeType getType() {
        return NodeType.NETWORK_MODIFICATION;
    }
}
