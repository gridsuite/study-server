/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout;

import lombok.*;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class NadVoltageLevelPositionInfos {
    private UUID id;
    private String voltageLevelId;
    @JsonProperty("xPosition")
    private Double xPosition;
    @JsonProperty("yPosition")
    private Double yPosition;
    @JsonProperty("xLabelPosition")
    private Double xLabelPosition;
    @JsonProperty("yLabelPosition")
    private Double yLabelPosition;
}
