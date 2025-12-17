/*
  Copyright (c) 2025, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Mathieu Deharbe <mathieu.deharbe at rte-france.com>
 *
 * Metadata of the network modifications used in gridstudy tree view
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Schema(description = "Modification metadata")
public class NetworkModificationMetadata {
    @Schema(description = "If the modification is activated")
    Boolean activated;
    @Schema(description = "Modification description")
    String description;
    @Schema(description = "Modification type")
    String type;
}
