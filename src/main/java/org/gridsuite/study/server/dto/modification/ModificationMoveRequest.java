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
 * Wire DTO for the move endpoint.  Uses {@link ModificationLocationInfos}
 * (node-aware) rather than {@link ModificationContainerInfos} (group-aware).
 */
public record ModificationMoveRequest(
        UUID modificationUuid,
        ModificationLocationInfos source,
        ModificationLocationInfos target,
        @Schema(description = "Insert before this modification; appends when null") UUID beforeUuid) {
}
