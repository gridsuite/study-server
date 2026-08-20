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
    BUILD(BUILDING, false, false, false),
    UNBUILD(UNBUILDING, false, false, true),
    UNBUILD_CHILDREN(UNBUILDING, true, false, true),
    UNBUILD_ALL(UNBUILDING, true, true, true),
    COMPUTE(COMPUTING, false, false, false),
    COMPUTE_AND_UNBUILD_CHILDREN(COMPUTING, true, false, false),
    REIMPORT_CASE(UPDATING, true, false, false),
    EDIT_TREE(UPDATING, true, true, true),
    EDIT_MODIFICATIONS(UPDATING, true, true, true),
    DELETE_NODES(DELETING, true, true, true),
    EDIT_EVENTS(UPDATING, false, true, true);

    private final NodeActivityLabel label;

    @Accessors(fluent = true)
    private final boolean invalidatesChildren;

    @Accessors(fluent = true)
    private final boolean affectsAllRootNetworks;

    private final boolean synchronous;
}
