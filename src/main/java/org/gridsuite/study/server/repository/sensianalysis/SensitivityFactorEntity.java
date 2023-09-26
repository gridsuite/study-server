/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis;

import lombok.*;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;

import javax.persistence.*;
import java.util.List;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@Table(name = "sensitivityFactorEntity")
public class SensitivityFactorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "monitoredBranch",
            joinColumns = @JoinColumn(name = "MonitoredBranchId", foreignKey = @ForeignKey(name = "sensitivityFactorEntity_monitoredBranchId_fk"))
    )
    private List<EquipmentsContainerEmbeddable> monitoredBranch;

    @ElementCollection
    @CollectionTable(
            name = "injections",
            joinColumns = @JoinColumn(name = "InjectionsId", foreignKey = @ForeignKey(name = "sensitivityFactorEntity_injections_fk"))
    )
    private List<EquipmentsContainerEmbeddable> injections;

    @ElementCollection
    @CollectionTable(
            name = "contingencies",
            joinColumns = @JoinColumn(name = "ContingenciesId", foreignKey = @ForeignKey(name = "sensitivityFactorEntity_contingencies_fk"))
    )
    private List<EquipmentsContainerEmbeddable> contingencies;

    @Column(name = "activated", columnDefinition = "boolean default true")
    private boolean activated;
}
