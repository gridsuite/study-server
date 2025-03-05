/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.dto.modification.BuildContext;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Build infos")
public class BuildInfos {
    private String originVariantId;

    private String destinationVariantId;

    private List<BuildContext> buildContextsInfos = new ArrayList<>();

    private Stream<ReportInfos> getReportsInfos() {
        return buildContextsInfos.stream().map(BuildContext::reportInfos);
    }

    public Map<UUID, UUID> getReportsMappings() {
        return getReportsInfos().collect(Collectors.toMap(ReportInfos::nodeUuid, ReportInfos::reportUuid));
    }

    public void addApplicationContextInfos(UUID modificationGroupUuid, Set<UUID> modificationUuidsToExclude, ReportInfos reportInfos) {
        buildContextsInfos.add(0, new BuildContext(modificationGroupUuid, modificationUuidsToExclude == null ? new HashSet<>() : modificationUuidsToExclude, reportInfos));
    }
}
