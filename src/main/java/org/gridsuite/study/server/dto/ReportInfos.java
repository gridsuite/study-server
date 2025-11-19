/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Schema(description = "Report infos")
public record ReportInfos(
    UUID reportUuid,
    UUID nodeUuid,
    ReportMode reportMode
) {
    public ReportInfos(UUID reportUuid, UUID nodeUuid) {
        this(reportUuid, nodeUuid, ReportMode.APPEND);
    }
}

