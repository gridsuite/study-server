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
@Table(name = "sensitivityParametersInjections")
public class SensitivityParametersInjectionsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsEntityMonitoredBranches",
            joinColumns = @JoinColumn(name = "injectionsId", foreignKey = @ForeignKey(name = "sensitivityInjectionsEntity_monitoredBranches_fK"))
    )
    private List<FilterEquipmentsEmbeddable> monitoredBranches;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsEntityInjections",
            joinColumns = @JoinColumn(name = "injectionsId", foreignKey = @ForeignKey(name = "sensitivityInjectionsEntity_injections_fk"))
    )
    private List<FilterEquipmentsEmbeddable> injections;

    @ElementCollection
    @CollectionTable(
            name = "sensitivityParametersInjectionsEntityContingencies",
            joinColumns = @JoinColumn(name = "injectionsId", foreignKey = @ForeignKey(name = "sensitivityInjectionsEntity_contingencies_fk"))
    )

    private List<FilterEquipmentsEmbeddable> contingencies;
}
