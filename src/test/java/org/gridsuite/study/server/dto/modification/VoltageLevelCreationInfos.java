/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Laurent GARNIER <laurent.garnier at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@Schema(description = "Voltage level creation")
public class VoltageLevelCreationInfos extends EquipmentCreationInfos {

    @Schema(description = "nominal voltage in kV")
    private double nominalVoltage;

    @Schema(description = "substation id")
    private String substationId;

    @Schema(description = "bus bar sections")
    private List<BusbarSectionCreationInfos> busbarSections;

    @Schema(description = "connections between bus bar sections")
    private List<BusbarConnectionCreationInfos> busbarConnections;
}
