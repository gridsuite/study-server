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
@Table(name = "sensiParamPst")
public class SensitivityAnalysisParametersPstEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "SensitivityTypePst")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.SensitivityType sensitivityType;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamPstMonitoredBranchesEntityFilters",
            joinColumns = @JoinColumn(name = "monitoredBranchesId", foreignKey = @ForeignKey(name = "sensiParamPstMoniBrEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamPstsIdEntityFilters",
            joinColumns = @JoinColumn(name = "pstsId", foreignKey = @ForeignKey(name = "sensiParamPstsEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> psts;

    @ElementCollection
    @CollectionTable(
            name = "sensiParamPstContingenciesEntityFilters",
            joinColumns = @JoinColumn(name = "contingenciesId", foreignKey = @ForeignKey(name = "sensiParamPstContEntity_filters_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
