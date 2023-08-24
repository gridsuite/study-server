/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "sensiParamNodes")
public class SensitivityAnalysisParametersNodesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamMonitoredBranchesEntityFilters",
            joinColumns = @JoinColumn(name = "monitoredBranchesId", foreignKey = @ForeignKey(name = "sensiParamMonitoredBranchesEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredVoltageLevels;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamRegulEntityFilters",
            joinColumns = @JoinColumn(name = "equipmentsVoltageRegulationId", foreignKey = @ForeignKey(name = "sensiParamRegulEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> equipmentsInVoltageRegulation;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamContingenciesEntityFilters",
            joinColumns = @JoinColumn(name = "contingenciesId", foreignKey = @ForeignKey(name = "sensiParamContingenciesEntity_filters_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
