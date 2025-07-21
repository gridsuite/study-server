/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto.caseimport;

/**
 * @author Kevin Le Saulnier <kevin.lesaulnier at rte-france.com>
 */

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CaseImportReceiver {
    private UUID studyUuid;
    private UUID rootNetworkUuid;
    private UUID caseUuid;
    private UUID originalCaseUuid;
    private UUID reportUuid;
    private String userId;
    private Long startTime;
    private CaseImportAction caseImportAction;
}
