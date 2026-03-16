/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.securityanalysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class SecurityAnalysisParameters {
    private String provider;

    private double lowVoltageAbsoluteThreshold;

    private double lowVoltageProportionalThreshold;

    private double highVoltageAbsoluteThreshold;

    private double highVoltageProportionalThreshold;

    private double flowProportionalThreshold;

    private List<ContingencyLists> contingencyListsInfos;

    private List<LimitReductionsByVoltageLevel> limitReductions;
}
