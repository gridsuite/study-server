/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;

import jakarta.persistence.*;
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
@Table(name = "voltageInitParameters")
public class VoltageInitParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "voltage_init_parameters_id")
    private List<VoltageInitParametersVoltageLimitsEntity> voltageLimits;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityConstantQGenerators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_constantQGenerators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> constantQGenerators;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableTwoWt",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableTwoWt_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableTwoWindingsTransformers;

    @ElementCollection
    @CollectionTable(
            name = "voltageInitParametersEntityVariableShuntCompensators",
            joinColumns = @JoinColumn(name = "voltageInitParametersId", foreignKey = @ForeignKey(name = "voltageInitParametersEntity_variableShuntCompensators_fk"))
    )
    private List<FilterEquipmentsEmbeddable> variableShuntCompensators;
}




