/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis;

import lombok.*;

import jakarta.persistence.*;
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
@Table(name = "sensitivityAnalysisParameters")
public class SensitivityAnalysisParametersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "flowFlowSensitivityValueThreshold")
    private double flowFlowSensitivityValueThreshold;

    @Column(name = "angleFlowSensitivityValueThreshold")
    private double angleFlowSensitivityValueThreshold;

    @Column(name = "flowVoltageSensitivityValueThreshold")
    private double flowVoltageSensitivityValueThreshold;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_Analysis_Parameters_id")
    private List<SensitivityFactorWithDistribTypeEntity> sensitivityInjectionsSet;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_Analysis_Parameters_id")
    private List<SensitivityFactorForInjectionEntity> sensitivityInjections;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_Analysis_Parameters_id")
    private List<SensitivityFactorWithSensiTypeForHvdcEntity> sensitivityHvdc;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_Analysis_Parameters_id")
    private List<SensitivityFactorWithSensiTypeForPstEntity> sensitivityPST;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensitivity_Analysis_Parameters_id")
    private List<SensitivityFactorForNodeEntity> sensitivityNodes;
}
