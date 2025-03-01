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

import java.util.*;

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

    private List<UUID> modificationGroupUuids = new ArrayList<>();

    // map with modification groups as key, modification to excludes as value
    private Map<UUID, Set<UUID>> modificationUuidsToExclude = new HashMap<>();

    private List<ReportInfos> reportsInfos = new ArrayList<>();

    public void insertModificationInfos(UUID modificationGroupUuid, Set<UUID> modificationUuidsToExclude, ReportInfos reportInfos) {
        if (modificationUuidsToExclude != null && !modificationUuidsToExclude.isEmpty()) {
            this.modificationUuidsToExclude.put(modificationGroupUuid, modificationUuidsToExclude);
        }
        modificationGroupUuids.add(0, modificationGroupUuid);
        reportsInfos.add(0, reportInfos);
    }
}
