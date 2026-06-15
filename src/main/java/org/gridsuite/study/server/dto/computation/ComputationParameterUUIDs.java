/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.computation;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ComputationParameterUUIDs(UUID loadFlowParametersUuid,
                                        UUID shortCircuitParametersUuid,
                                        UUID dynamicSimulationParametersUuid,
                                        UUID voltageInitParametersUuid,
                                        UUID securityAnalysisParametersUuid,
                                        UUID sensitivityAnalysisParametersUuid,
                                        UUID dynamicSecurityAnalysisParametersUuid,
                                        UUID dynamicMarginCalculationParametersUuid,
                                        UUID stateEstimationParametersUuid,
                                        UUID pccMinParametersUuid) {
}
