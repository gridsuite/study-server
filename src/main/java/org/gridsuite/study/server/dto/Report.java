/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import org.gridsuite.study.server.StudyConstants;

import java.util.List;
import java.util.UUID;

/**
 * @author David Braquart <david.braquart at rte-france.com>
 */
@Schema(description = "Report data")
@Builder
public record Report(
        UUID id,
        UUID parentId,
        String message,
        StudyConstants.Severity severity,
        List<Report> subReports
) { }
