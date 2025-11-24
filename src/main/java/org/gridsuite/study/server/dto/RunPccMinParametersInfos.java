/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * @author Maissa Souissi <maissa.souissi at rte-france.com>
 */
@AllArgsConstructor
@Getter
public class RunPccMinParametersInfos {

    private UUID shortCircuitParametersUuid;

    private UUID pccMinParametersUuid;

    private String busId;
}
