/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Caroline Jeandat <caroline.jeandat at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class NadRequestInfos {
    private UUID nadConfigUuid;
    private UUID filterUuid;
    private Set<String> voltageLevelIds;
    private Set<String> voltageLevelToExpandIds;
    private Set<String> voltageLevelToOmitIds;
    private List<Map<String, Object>> positions;
    private String nadPositionsGenerationMode;
    private UUID nadPositionsConfigUuid;
    private List<CurrentLimitViolationInfos> currentLimitViolationsInfos;
    private List<Map<String, Object>> baseVoltagesConfigInfos;
}
