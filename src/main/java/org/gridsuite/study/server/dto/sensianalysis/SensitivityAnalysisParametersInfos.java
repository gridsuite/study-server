/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SensitivityAnalysisParametersInfos {
    private double flowFlowSensitivityValueThreshold;

    private double angleFlowSensitivityValueThreshold;

    private double flowVoltageSensitivityValueThreshold;

    List<SensitivityAnalysisInputData.SensitivityInjectionsSet> sensitivityInjectionsSet = new ArrayList<>();

    List<SensitivityAnalysisInputData.SensitivityInjection> sensitivityInjection= new ArrayList<>();

    List<SensitivityAnalysisInputData.SensitivityHVDC> sensitivityHVDC = new ArrayList<>();

    List<SensitivityAnalysisInputData.SensitivityPST> sensitivityPST = new ArrayList<>();

    List<SensitivityAnalysisInputData.SensitivityNodes> sensitivityNodes = new ArrayList<>();
}
