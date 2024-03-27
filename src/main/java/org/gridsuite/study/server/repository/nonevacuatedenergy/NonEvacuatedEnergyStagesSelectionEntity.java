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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "nonEvacuatedEnergyStagesSelection")
public class NonEvacuatedEnergyStagesSelectionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "name")
    private String name;

    @Column(name = "stages_definition_index")
    @ElementCollection
    @CollectionTable(
        name = "nonEvacuatedEnergyStagesSelectionDefinitionIndex",
        joinColumns = @JoinColumn(name = "non_evacuated_energy_stages_selection_id")
    )
    List<Integer> stageDefinitionIndex;

    @Column(name = "pmax_percent_index")
    @ElementCollection
    @CollectionTable(
        name = "nonEvacuatedEnergyStageSelectionPmaxPercentIndex",
        joinColumns = @JoinColumn(name = "non_evacuated_energy_stages_selection_id")
    )
    List<Integer> pMaxPercentsIndex;

    @Column(name = "activated", columnDefinition = "boolean default true")
    private boolean activated;
}
