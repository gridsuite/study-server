/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;
import org.gridsuite.study.server.dto.SensitivityAnalysisInputData;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "sensiParamHvdc")
public class SensitivityAnalysisParametersHvdcEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "SensitivityTypeHvdc")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.SensitivityType sensitivityType;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamHvdcMonitoredBranchesEntityFilters",
            joinColumns = @JoinColumn(name = "monitoredBranchesId", foreignKey = @ForeignKey(name = "sensiParamHvdcMoniBrEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamHvdcsEntityFilters",
            joinColumns = @JoinColumn(name = "hvdcsId", foreignKey = @ForeignKey(name = "sensiParamHvdcsEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> hvdcs;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamHvdcContingenciesEntityFilters",
            joinColumns = @JoinColumn(name = "contingenciesId", foreignKey = @ForeignKey(name = "sensiParamHvdcContEntity_filters_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
