/**
  Copyright (c) 2024, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class UserProfileInfos {

    private String name;

    private UUID loadFlowParameterId;

    private UUID securityAnalysisParameterId;

    private UUID sensitivityAnalysisParameterId;

    private UUID shortcircuitParameterId;

    private UUID dynamicSecurityAnalysisParameterId;

    private UUID voltageInitParameterId;

    Integer maxAllowedBuilds;
}
