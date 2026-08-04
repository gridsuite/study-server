/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import lombok.AllArgsConstructor;
import lombok.Getter;

import static org.gridsuite.study.server.nodeactivity.NodeActivityLabel.*;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Getter
@AllArgsConstructor
public enum NodeActivityType {
    BUILD(BUILDING, false, false),
    UNBUILD(UNBUILDING, false, false),
    UNBUILD_CHILDREN(UNBUILDING, true, false),
    UNBUILD_ALL(UNBUILDING, true, true),
    COMPUTE(COMPUTING, false, false),
    LOADFLOW_ON_SECURITY_NODE(COMPUTING, true, false),
    REIMPORT_CASE(UPDATING, true, false),
    EDIT_TREE(UPDATING, true, true),
    EDIT_MODIFICATIONS(UPDATING, true, true),
    EDIT_PARAMETERS(UPDATING, true, true),
    DELETE_NODES(DELETING, true, true),
    EDIT_EVENTS(UPDATING, false, true);

    private final NodeActivityLabel label;

    private final boolean invalidatesChildren;

    private final boolean affectsAllRootNetworks;
}
