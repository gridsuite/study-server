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
@Table(name = "sensitivityParametersNodes")
public class SensitivityParametersNodesEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersNodesEntityMonitVoltLevels",
            joinColumns = @JoinColumn(name = "nodesId", foreignKey = @ForeignKey(name = "sensitivityNodesEntity_monitVoltLevels_fK"))
    )
    private List<FilterEquipmentsEmbeddable> monitVoltLevels;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersNodesEntityEqInVoltRegul",
            joinColumns = @JoinColumn(name = "nodesId", foreignKey = @ForeignKey(name = "sensitivityNodesEntity_eqInVoltRegul_fk"))
    )
    private List<FilterEquipmentsEmbeddable> eqInVoltRegul;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersNodesEntityContingencies",
            joinColumns = @JoinColumn(name = "nodesId", foreignKey = @ForeignKey(name = "sensitivityNodesEntity_contingencies_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
