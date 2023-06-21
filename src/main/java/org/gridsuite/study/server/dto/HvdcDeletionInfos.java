/*
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.iidm.network.HvdcConverterStation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@Schema(description = "HVDC deletion")
public class HvdcDeletionInfos {

    private String id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private HvdcConverterStation.HvdcType hvdcType;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<HvdcDeletionSelectedShuntCompensatorData> mcsOnSide1;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<HvdcDeletionSelectedShuntCompensatorData> mcsOnSide2;
}
