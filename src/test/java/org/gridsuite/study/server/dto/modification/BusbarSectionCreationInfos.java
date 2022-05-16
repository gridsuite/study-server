/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Laurent GARNIER <laurent.garnier at rte-france.com>
 */
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Voltage level bus bar section creation")
public class BusbarSectionCreationInfos {
    @Schema(description = "bus bar section id")
    private String id;

    @Schema(description = "bus bar section name")
    private String name;

    @Schema(description = "vertical position")
    private int vertPos;

    @Schema(description = "horizontal position")
    private int horizPos;
}
