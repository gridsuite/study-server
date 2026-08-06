/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import static org.gridsuite.study.server.nodeactivity.NodeActivityLabel.*;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Getter
@AllArgsConstructor
public enum NodeActivityType {
    // label, invalidates children, affects all root networks, removed by a result message
    BUILD(BUILDING, false, false, true),
    UNBUILD(UNBUILDING, false, false, false),
    UNBUILD_CHILDREN(UNBUILDING, true, false, false),
    UNBUILD_ALL(UNBUILDING, true, true, false),
    COMPUTE(COMPUTING, false, false, true),
    COMPUTE_AND_UNBUILD_CHILDREN(COMPUTING, true, false, true),
    REIMPORT_CASE(UPDATING, true, false, true),
    EDIT_TREE(UPDATING, true, true, false),
    EDIT_MODIFICATIONS(UPDATING, true, true, false),
    DELETE_NODES(DELETING, true, true, false),
    EDIT_EVENTS(UPDATING, false, true, false);

    private final NodeActivityLabel label;

    @Accessors(fluent = true)
    private final boolean invalidatesChildren;

    @Accessors(fluent = true)
    private final boolean affectsAllRootNetworks;

    /**
     * The row outlives the request, so every path a result message can take through ConsumerService
     * must remove the activity. When false the row goes as soon as the call returns.
     */
    private final boolean removedByResultMessage;
}
