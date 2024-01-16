/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.nonevacuatedenergy;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis non evacuated energy input data")
public class NonEvacuatedEnergyInputData {
    private List<NonEvacuatedEnergyStageDefinition> nonEvacuatedEnergyStagesDefinition;

    private List<NonEvacuatedEnergyStagesSelection> nonEvacuatedEnergyStagesSelection;

    private NonEvacuatedEnergyGeneratorsCappings nonEvacuatedEnergyGeneratorsCappings;

    private List<NonEvacuatedEnergyMonitoredBranches> nonEvacuatedEnergyMonitoredBranches;

    private List<NonEvacuatedEnergyContingencies> nonEvacuatedEnergyContingencies;

    @Schema(description = "Sensitivity parameters")
    private SensitivityAnalysisParameters parameters;

    @Schema(description = "Loadflow model-specific parameters")
    private Map<String, String> loadFlowSpecificParameters;
}
