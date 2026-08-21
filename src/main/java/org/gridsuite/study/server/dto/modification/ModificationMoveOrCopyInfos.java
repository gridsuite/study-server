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
 * one entry of a copy/move-modifications request: pairs a modification with the container it currently belongs
 * to, so a selection mixing modifications directly under a node's group and modifications nested in a composite
 * can be resolved per-item, the same way {@link MoveModificationInfos} does for a single-modification move.
 */
public record ModificationMoveOrCopyInfos(
        UUID modificationUuid,
        @Schema(description = "container the modification currently belongs to; omitted only for GROUP, where node's own group is used") ModificationContainerInfos source) {
}
