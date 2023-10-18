/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy;

import com.powsybl.sensitivity.SensitivityAnalysisParameters;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@SuperBuilder
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Sensitivity analysis non evacuated energy input data")
public class NonEvacuatedEnergyInputData {
    private List<NonEvacuatedEnergyStages> nonEvacuatedEnergyStages;

    private NonEvacuatedEnergyGeneratorsLimit nonEvacuatedEnergyGeneratorsLimit;

    private List<NonEvacuatedEnergyMonitoredBranches> nonEvacuatedEnergyMonitoredBranches;

    private List<NonEvacuatedEnergyContingencies> nonEvacuatedEnergyContingencies;

    @Schema(description = "Sensitivity parameters")
    private SensitivityAnalysisParameters parameters;

    @Schema(description = "Loadflow model-specific parameters")
    private Map<String, String> loadFlowSpecificParameters;
}
