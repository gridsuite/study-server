/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.powsybl.loadflow.LoadFlowParameters;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@AllArgsConstructor
@Getter
public class RunSecurityAnalysisParametersInfos {

    private UUID securityAnalysisParametersUuid;

    private Map<String, String> specificParams;

    private LoadFlowParameters loadFlowParameters;

    private List<String> contingencyListNames;

}
