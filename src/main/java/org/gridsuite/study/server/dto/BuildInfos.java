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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

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

    private List<Pair<UUID, UUID>> modificationGroupAndReportUuids = new ArrayList<>();

    private Set<UUID> modificationsToExclude = new HashSet<>();

    public void insertModificationGroupAndReport(UUID modificationGroupUuid, UUID reportUuid) {
        modificationGroupAndReportUuids.add(Pair.of(modificationGroupUuid, reportUuid));
    }

    public List<UUID> getModificationGroupUuids() {
        return modificationGroupAndReportUuids.stream().map(Pair::getLeft).collect(Collectors.toList());
    }

    public void addModificationsToExclude(Set<UUID> modificationsUuid) {
        modificationsToExclude.addAll(modificationsUuid);
    }
}
