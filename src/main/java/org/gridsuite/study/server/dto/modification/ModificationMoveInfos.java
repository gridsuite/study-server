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
 */
public record ModificationMoveInfos(
        UUID modificationUuid,
        @Schema(description = "current container; resolved to the parent composite or the origin node's group when omitted") ModificationContainerInfos source,
        @Schema(description = "destination container; defaults to the target node's group") ModificationContainerInfos target,
        @Schema(description = "insert before this modification of the target container; appends when null") UUID beforeUuid) { }
