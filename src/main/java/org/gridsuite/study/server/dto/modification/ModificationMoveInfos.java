/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 *
 * One move of a batch, forwarded as is to the network-modification-server: origin and target nodes (hence groups)
 * are given once per request, a null composite designates the node's own group.
 */
public record ModificationMoveInfos(
        UUID modificationUuid,
        @Schema(description = "composite currently containing the modification; the origin node's group when null") UUID sourceCompositeUuid,
        @Schema(description = "composite to move the modification into; the target node's group when null") UUID targetCompositeUuid,
        @Schema(description = "insert before this modification of the target container; appends when null") UUID beforeUuid) { }
