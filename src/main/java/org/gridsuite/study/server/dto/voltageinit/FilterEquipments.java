/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.UUID;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterEquipments {

    @Schema(description = "filter id")
    private UUID filterId;

    @Schema(description = "filter name")
    private String filterName;

    @Schema(description = "equipments of filter")
    private List<IdentifiableAttributes> identifiableAttributes;

    @Schema(description = "equipments not found in network")
    private List<String> notFoundEquipments;
}

