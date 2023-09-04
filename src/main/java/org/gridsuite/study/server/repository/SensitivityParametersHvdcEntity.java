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
@Table(name = "sensitivityParametersHvdc")
public class SensitivityParametersHvdcEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersHvdcEntityMonitoredBranches",
            joinColumns = @JoinColumn(name = "HvdcId", foreignKey = @ForeignKey(name = "sensitivityHvdcEntity_monitoredBranches_fK"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersHvdcEntityHvdcs",
            joinColumns = @JoinColumn(name = "HvdcId", foreignKey = @ForeignKey(name = "sensitivityHvdcEntity_hvdcs_fk"))
    )
    private List<FilterEquipmentsEmbeddable> hvdcs;

    @Column(name = "sensitivityParametersHvdcEntitySensitivityType")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.SensitivityType sensitivityType;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersHvdcEntityContingencies",
            joinColumns = @JoinColumn(name = "HvdcId", foreignKey = @ForeignKey(name = "sensitivityHvdcEntity_contingencies_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
