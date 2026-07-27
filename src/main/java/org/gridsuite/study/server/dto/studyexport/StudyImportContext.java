/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport;

import java.util.UUID;

/**
 * Context for study import - stores both the export data and directory info
 *
 * @param studyExportInfos The study export data (node tree, root networks)
 * @param studyName The name of the study to create
 * @param description The description of the study
 * @param parentDirectoryUuid The parent directory UUID where to create the study element
 */
public record StudyImportContext(
        StudyExportInfos studyExportInfos,
        String studyName,
        String description,
        UUID parentDirectoryUuid
) {
}
