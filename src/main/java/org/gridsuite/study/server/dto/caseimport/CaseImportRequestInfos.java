/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.caseimport;

import lombok.Builder;
import org.gridsuite.study.server.dto.studyexport.StudyImportContext;

import java.util.Map;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Builder
public record CaseImportRequestInfos(String userId,
                                     UUID importReportUuid,
                                     Map<String, Object> importParameters,
                                     CaseImportAction caseImportAction,
                                     StudyImportContext importContext) {
}
