/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Getter
@AllArgsConstructor
public enum BatchActionType {
    INVALIDATE_NODE_TREE
}
