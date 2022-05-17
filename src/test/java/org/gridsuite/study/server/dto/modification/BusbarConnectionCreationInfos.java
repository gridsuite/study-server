/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import com.powsybl.iidm.network.SwitchKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Laurent GARNIER <laurent.garnier at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Voltage level bus bar sections connection creation")
public class BusbarConnectionCreationInfos {
    @Schema(description = "one side of the connection")
    private String fromBBS;

    @Schema(description = "other side of the connection")
    private String toBBS;

    @Schema(description = "switch on the connection")
    private SwitchKind switchKind;
}
