/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Getter
@Setter
@Builder
public class SensitivityAnalysisParametersInfos {
    private String provider;
    private UUID uuid;
    private double flowFlowSensitivityValueThreshold;
    private double angleFlowSensitivityValueThreshold;
    private double flowVoltageSensitivityValueThreshold;
    List<SensitivityInjectionsSetInfos> sensitivityInjectionsSet;
    List<SensitivityInjectionInfos> sensitivityInjection;
    List<SensitivityHvdcInfos> sensitivityHVDC;
    List<SensitivityPstInfos> sensitivityPST;
    List<SensitivityNodesInfos> sensitivityNodes;


}
