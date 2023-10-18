/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis.nonevacuatedenergy;

import com.powsybl.iidm.network.EnergySource;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "nonEvacuatedEnergyStageDefinition")
public class NonEvacuatedEnergyStageDefinitionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
        name = "nonEvacuatedEnergyStageDefinitionGenerators",
        joinColumns = @JoinColumn(name = "non_evacuated_energy_stage_definition_id")
    )
    private List<EquipmentsContainerEmbeddable> generators;

    @Enumerated(EnumType.STRING)
    private EnergySource energySource;

    @Column(name = "pmax_percent")
    @ElementCollection
    @CollectionTable(
        name = "nonEvacuatedEnergyStageDefinitionPmaxPercent",
        joinColumns = @JoinColumn(name = "non_evacuated_energy_stage_definition_id")
    )
    List<Float> pMaxPercents;
}
