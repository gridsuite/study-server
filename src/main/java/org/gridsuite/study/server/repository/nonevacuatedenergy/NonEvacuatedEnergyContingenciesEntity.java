/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.nonevacuatedenergy;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;

import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "nonEvacuatedEnergyContingencies")
public class NonEvacuatedEnergyContingenciesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "nonEvacuatedEnergyContingency",
            joinColumns = @JoinColumn(name = "NonEvacuatedEnergyContingenciesId", foreignKey = @ForeignKey(name = "nonEvacuatedEnergyContingenciesEntity_contingencies_fk"))
    )
    private List<EquipmentsContainerEmbeddable> contingencies;

    @Column(name = "activated", columnDefinition = "boolean default true")
    private boolean activated;
}
