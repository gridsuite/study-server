/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModificationLocationInfos(
        @Schema(description = "Node UUID — the node's root modification group") UUID nodeUuid,
        @Schema(description = "Composite UUID — a specific composite modification") UUID compositeUuid) {

    public ModificationContainerInfos resolve(UnaryOperator<UUID> nodeToGroupResolver) {
        if (compositeUuid != null) {
            return new ModificationContainerInfos(compositeUuid, ModificationContainerType.COMPOSITE);
        }
        return new ModificationContainerInfos(
                nodeToGroupResolver.apply(nodeUuid), ModificationContainerType.GROUP);
    }

    /** @return the node UUID if this is a node location, null otherwise */
    public UUID nodeUuidOrNull() {
        return nodeUuid;
    }
}
