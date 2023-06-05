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

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "voltageInitParametersVoltageLimits")
public class VoltageInitParametersVoltageLimitsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "lowVoltageLimit")
    private double lowVoltageLimit;

    @Column(name = "highVoltageLimit")
    private double highVoltageLimit;

    @Column(name = "priority")
    private int priority;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersVoltageLimitsEntityFilters",
            joinColumns = @JoinColumn(name = "voltageLimitId", foreignKey = @ForeignKey(name = "voltageInitVoltageLimitsEntity_filters_fk"))
    )
    private List<FilterEquipmentsEmbeddable> filters;
}
