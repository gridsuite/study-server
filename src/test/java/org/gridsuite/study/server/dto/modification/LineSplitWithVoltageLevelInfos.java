/*
  Copyright (c) 2022, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
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
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString(callSuper = true)
@Schema(description = "Line split with voltage level")
public class LineSplitWithVoltageLevelInfos extends ModificationInfos {

    @Schema(description = "line to split ID")
    private String lineToSplitId;

    @Schema(description = "percentage of line length from side 1")
    private double percent;

    @Schema(description = "possible new voltage level to create before inserting it, may be null")
    private VoltageLevelCreationInfos mayNewVoltageLevelInfos;

    @Schema(description = "if no new voltage level, ID for the existing voltage level")
    private String existingVoltageLevelId;

    @Schema(description = "bus bar section or bus id")
    private String bbsOrBusId;

    @Schema(description = "new line 1 ID")
    private String newLine1Id;

    @Schema(description = "new line 1 name")
    private String newLine1Name;

    @Schema(description = "new line 1 ID")
    private String newLine2Id;

    @Schema(description = "new line 2 name")
    private String newLine2Name;
}
