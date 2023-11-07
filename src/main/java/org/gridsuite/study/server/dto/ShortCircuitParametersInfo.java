/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.powsybl.shortcircuit.InitialVoltageProfileMode;
import com.powsybl.shortcircuit.ShortCircuitParameters;
import com.powsybl.shortcircuit.StudyType;
import com.powsybl.shortcircuit.VoltageRange;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ShortCircuitParametersInfo {
    private String version;
    private boolean withLimitViolations;
    private boolean withFortescueResult;
    private boolean withFeederResult;
    private StudyType studyType;
    private boolean withVoltageResult;
    private double minVoltageDropProportionalThreshold;
    private boolean withLoads;
    private boolean withShuntCompensators;
    private boolean withVSCConverterStations;
    private boolean withNeutralPosition;
    private InitialVoltageProfileMode initialVoltageProfileMode;
    private ShortCircuitPredefinedConfiguration predefinedParameters;
    private ShortCircuitParameters parameters;
    private Map<String, List<VoltageRange>> voltageRanges;
}
