/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentsContainer {

    @Schema(description = "container id")
    private UUID containerId;

    @Schema(description = "container name")
    private String containerName;

    @JsonCreator(mode = DELEGATING)
    public EquipmentsContainer(String containerId) {
        this.containerId = UUID.fromString(containerId);
    }

    public static List<EquipmentsContainer> enrichEquipmentsContainer(List<UUID> containerIds, Map<UUID, String> containerNames) {
        if (containerIds == null) {
            return null;
        }
        return containerIds.stream()
                .map(id -> new EquipmentsContainer(id, containerNames.get(id)))
                .toList();
    }

    public static List<UUID> getEquipmentsContainerUuids(List<EquipmentsContainer> containers) {
        if (containers == null) {
            return null;
        }
        return containers.stream()
                .map(EquipmentsContainer::getContainerId)
                .toList();
    }
}

