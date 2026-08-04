/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
public record NodeActivityInfos(
    UUID nodeId,
    // null when the activity affects every root network of the study
    UUID rootNetworkId,
    NodeActivityLabel label,
    boolean invalidatesChildren,
    Instant startedAt) {

    static NodeActivityInfos from(NodeActivityEntity activity) {
        return new NodeActivityInfos(
            activity.getNodeId(),
            activity.getRootNetworkId(),
            activity.getType().getLabel(),
            activity.getType().isInvalidatesChildren(),
            activity.getStartedAt());
    }
}
