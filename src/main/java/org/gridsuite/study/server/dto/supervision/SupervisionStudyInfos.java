/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.supervision;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.dto.RootNetworkInfos;
import org.gridsuite.study.server.dto.StudyInfos;

import java.util.List;
import java.util.UUID;

// more detailed version of StudyInfos for supervision and admin tools operations
@SuperBuilder
@NoArgsConstructor
@Getter
@ToString(callSuper = true)
@Schema(description = "Supervision Study attributes")
@EqualsAndHashCode(callSuper = true)
public class SupervisionStudyInfos extends StudyInfos {
    private List<RootNetworkInfos> rootNetworkInfos;
    private List<UUID> caseUuids;
}
