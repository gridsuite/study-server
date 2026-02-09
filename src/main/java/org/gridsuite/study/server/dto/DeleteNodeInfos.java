/**
 * Copyright (c) 2022 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

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
public class DeleteNodeInfos extends NodeInfos {

    private List<UUID> removedNodeUuids = new ArrayList<>();
    private List<UUID> modificationGroupUuids = new ArrayList<>();
    private Map<UUID, List<String>> variantIds = new HashMap<>();

    public void addRemovedNodeUuid(UUID removedNodeUuid) {
        removedNodeUuids.add(removedNodeUuid);
    }

    public void addModificationGroupUuid(UUID modificationGroupUuid) {
        modificationGroupUuids.add(modificationGroupUuid);
    }

    public void addVariantId(UUID networkUuid, String variantId) {
        variantIds.getOrDefault(networkUuid, new ArrayList<>()).add(variantId);
    }

}
