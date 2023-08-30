/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class SensibilityAnalysisParametersInfos {
    private double flowFlowSensitivityValueThreshold;

    private double angleFlowSensitivityValueThreshold;

    private double flowVoltageSensitivityValueThreshold;

    List<SensibilityAnalysisInjectionsSetParameterInfos> sensitivityInjectionsSet;

    List<SensibilityAnalysisInjectionsParameterInfos> sensitivityInjection;

    List<SensibilityAnalysisHvdcParameterInfos> sensitivityHVDC;

    List<SensibilityAnalysisPtsParameterInfos> sensitivityPST;

    List<SensibilityAnalysisNodesParameterInfos> sensitivityNodes;
}