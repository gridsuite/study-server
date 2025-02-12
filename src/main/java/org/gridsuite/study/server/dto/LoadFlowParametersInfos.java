/**
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@Getter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class LoadFlowParametersInfos {

    private String provider;

    private Float limitReduction;

    private LoadFlowParameters commonParameters;

    private Map<String, Map<String, String>> specificParametersPerProvider;

    private List<LimitReductionsByVoltageLevel> limitReductions;


}
