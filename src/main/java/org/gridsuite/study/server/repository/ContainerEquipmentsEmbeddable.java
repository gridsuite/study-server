/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.dto.sensianalysis.EquipmentsContainer;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class ContainerEquipmentsEmbeddable {

    @Column(name = "containerId")
    private UUID containerId;

    @Column(name = "containerName")
    private String containerName;

    public static List<ContainerEquipmentsEmbeddable> toEmbeddableFilterEquipments(List<EquipmentsContainer> containers) {
        return containers == null ? null :
                containers.stream()
                        .map(container -> new ContainerEquipmentsEmbeddable(container.getContainerId(), container.getContainerName()))
                        .collect(Collectors.toList());
    }

    public static List<EquipmentsContainer> fromEmbeddableFilterEquipments(List<ContainerEquipmentsEmbeddable> containers) {
        return containers == null ? null :
                containers.stream()
                        .map(container -> new EquipmentsContainer(container.getContainerId(), container.getContainerName(), null, null))
                        .collect(Collectors.toList());
    }
}
