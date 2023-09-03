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
@Table(name = "sensitivityParametersInjectionsSet")
public class SensitivityParametersInjectionsSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "sensitivityParametersInjectionsSetDistributionType")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.DistributionType distributionType;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsSetEntityMonitoredBranches",
            joinColumns = @JoinColumn(name = "injectionsSetId", foreignKey = @ForeignKey(name = "sensitivityInjectionsSetEntity_monitoredBranches_fK"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsSetEntityInjections",
            joinColumns = @JoinColumn(name = "injectionsSetId", foreignKey = @ForeignKey(name = "sensitivityInjectionsSetEntity_injections_fk"))
    )
    private List<FilterEquipmentsEmbeddable> injections;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsSetEntityContingencies",
            joinColumns = @JoinColumn(name = "injectionsSetId", foreignKey = @ForeignKey(name = "sensitivityInjectionsSetEntity_contingencies_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
