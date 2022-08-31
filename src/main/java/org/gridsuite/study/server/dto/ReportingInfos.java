/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Laurent Garnier <laurent.garnier at rte-france.com>
 */
@Getter
@Builder
@AllArgsConstructor
public class ReportingInfos {
    private UUID definingNodeUuid;
    private UUID reportUuid;
    private UUID modificationGroupUuid;
    private String definingNodeName;
}
