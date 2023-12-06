/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis.nonevacuatedenergy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class NonEvacuatedEnergyParametersInfos {
    @Builder.Default
    List<NonEvacuatedEnergyStageDefinition> stagesDefinition = new ArrayList<>();

    @Builder.Default
    List<NonEvacuatedEnergyStagesSelection> stagesSelection = new ArrayList<>();

    NonEvacuatedEnergyGeneratorsLimit generatorsLimit;

    @Builder.Default
    List<NonEvacuatedEnergyMonitoredBranches> monitoredBranches = new ArrayList<>();

    @Builder.Default
    List<NonEvacuatedEnergyContingencies> contingencies = new ArrayList<>();
}
