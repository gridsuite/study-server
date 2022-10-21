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

    private UUID reportUuid;

    private List<UUID> modificationGroupUuids = new ArrayList<>();

    private List<String> reporterIds = new ArrayList<>();

    private Set<UUID> modificationsToExclude = new HashSet<>();

    public void insertModificationInfos(UUID modificationGroupUuid, String reporterId) {
        modificationGroupUuids.add(0, modificationGroupUuid);
        reporterIds.add(0, reporterId);
    }

    public void addModificationsToExclude(Set<UUID> modificationsUuid) {
        modificationsToExclude.addAll(modificationsUuid);
    }
}
