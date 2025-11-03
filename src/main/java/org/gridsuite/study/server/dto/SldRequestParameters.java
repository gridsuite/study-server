/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.List;

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
public class SldRequestParameters {
    private List<CurrentLimitViolationInfos> currentLimitViolations;
    private BaseVoltagesConfigInfos baseVoltagesConfigInfos;
}
