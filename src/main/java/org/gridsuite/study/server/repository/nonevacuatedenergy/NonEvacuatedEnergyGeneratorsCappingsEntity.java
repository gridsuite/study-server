/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.nonevacuatedenergy;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "nonEvacuatedEnergyGeneratorsCappings")
public class NonEvacuatedEnergyGeneratorsCappingsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "sensitivity_threshold")
    private double sensitivityThreshold;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "non_evacuated_energy_generators_cappings_id")
    private List<NonEvacuatedEnergyGeneratorsCappingsByTypeEntity> generatorsCappings = new ArrayList<>();

    public void addGenerators(NonEvacuatedEnergyGeneratorsCappingsByTypeEntity generatorsCappingsByTypeEntity) {
        generatorsCappings.add(generatorsCappingsByTypeEntity);
    }
}
