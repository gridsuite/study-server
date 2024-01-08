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
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.EquipmentsContainerEmbeddable;

import java.util.List;
import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "nonEvacuatedEnergyMonitoredBranches")
public class NonEvacuatedEnergyMonitoredBranchesEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @ElementCollection
    @CollectionTable(
            name = "nonEvacuatedEnergyMonitoredBranch",
            joinColumns = @JoinColumn(name = "NonEvacuatedEnergyMonitoredBranchesId", foreignKey = @ForeignKey(name = "nonEvacuatedEnergyMonitoredBranchesEntity_monitoredBranches_fk"))
    )
    private List<EquipmentsContainerEmbeddable> monitoredBranches;

    @Column(name = "ist_n")
    private boolean istN;

    @Column(name = "limit_name_n")
    private String limitNameN;

    @Column(name = "n_coefficient")
    private float nCoefficient;

    @Column(name = "ist_nm1")
    private boolean istNm1;

    @Column(name = "limit_name_nm1")
    private String limitNameNm1;

    @Column(name = "nm1_coefficient")
    private float nm1Coefficient;

    @Column(name = "activated", columnDefinition = "boolean default true")
    private boolean activated;
}
