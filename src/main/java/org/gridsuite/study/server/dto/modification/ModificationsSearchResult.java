/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author Souissi Maissa <souissi.maissa at rte-france.com>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Modification search result")
public class ModificationsSearchResult {
    @Schema(description = "Modification id")
    private UUID modificationUuid;

    @Schema(description = "Modification type")
    private String type;

    @Schema(
            description = "Message type"
    )
    private String messageType;
    @Schema(
            description = "Message values"
    )
    private String messageValues;
}
