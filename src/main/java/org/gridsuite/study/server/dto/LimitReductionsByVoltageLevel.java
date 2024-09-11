/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitReductionsByVoltageLevel {

    @Setter
    @Getter
    @NoArgsConstructor
    public static class LimitDuration {
        private Integer lowBound;
        private boolean lowClosed;
        private Integer highBound;
        private boolean highClosed;
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class VoltageLevel {
        private double nominalV;
        private double lowBound;
        private double highBound;
    }

    @Builder
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitReduction {
        double reduction;
        LimitDuration limitDuration;
    }

    private VoltageLevel voltageLevel;
    private double permanentLimitReduction;
    List<LimitReduction> temporaryLimitReductions;
}
