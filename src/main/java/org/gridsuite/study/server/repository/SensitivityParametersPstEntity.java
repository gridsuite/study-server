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
@Table(name = "sensitivityParametersPst")
public class SensitivityParametersPstEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "sensitivityParametersPstEntitySensitivityType")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.SensitivityType sensitivityType;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersPstEntityMonitoredBranches",
            joinColumns = @JoinColumn(name = "PstId", foreignKey = @ForeignKey(name = "sensitivityPstEntity_monitoredBranches_fK"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersPstEntityPst",
            joinColumns = @JoinColumn(name = "PstId", foreignKey = @ForeignKey(name = "sensitivityPstEntity_psts_fK"))
    )
    private List<FilterEquipmentsEmbeddable> psts;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersPstEntityContingencies",
            joinColumns = @JoinColumn(name = "PstId", foreignKey = @ForeignKey(name = "sensitivityPstEntity_contingencies_fK"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
