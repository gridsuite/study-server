/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import java.util.UUID;

public record RootNetworkCreationInfos(
    UUID caseUuid,
    UUID originalCaseUuid,
    UUID studyUuid,
    UUID rootNetworkUuid,
    String caseFormat
) { }

