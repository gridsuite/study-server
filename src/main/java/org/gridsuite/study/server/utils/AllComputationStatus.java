/*
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.gridsuite.study.server.dto.SecurityAnalysisStatus;
import org.gridsuite.study.server.dto.dynamicmargincalculation.DynamicMarginCalculationStatus;
import org.gridsuite.study.server.dto.dynamicsecurityanalysis.DynamicSecurityAnalysisStatus;
import org.gridsuite.study.server.dto.dynamicsimulation.DynamicSimulationStatus;

/**
 * @author Etienne Lesot <etienne.lesot at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
@Jacksonized
@Builder
public class AllComputationStatus {
    @JsonProperty("pccMinStatus")
    private String pccMinStatus;

    @JsonProperty("dynamicMarginCalculationStatus")
    private String dynamicMarginCalculationStatus;

    @JsonProperty("dynamicSecurityAnalysisStatus")
    private String dynamicSecurityAnalysisStatus;

    @JsonProperty("dynamicSimulationStatus")
    private String dynamicSimulationStatus;

    @JsonProperty("stateEstimationStatus")
    private String stateEstimationStatus;

    @JsonProperty("sensitivityAnalysisStatus")
    private String sensitivityAnalysisStatus;

    @JsonProperty("loadFlowStatus")
    private String loadFlowStatus;

    @JsonProperty("securityAnalysisStatus")
    private String securityAnalysisStatus;

    @JsonProperty("oneBusShortCircuitStatus")
    private String oneBusShortCircuitStatus;

    @JsonProperty("allBusShortCircuitStatus")
    private String allBusShortCircuitStatus;

    @JsonProperty("voltageInitStatus")
    private String voltageInitStatus;
}
