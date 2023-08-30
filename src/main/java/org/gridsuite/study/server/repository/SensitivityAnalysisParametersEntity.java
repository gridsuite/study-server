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
 * @author SAHNOUN Walid <walid.sahnoun@rte-france.com>
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
    @JoinColumn(name = "sensibility_analysis_parameters_id")
    private List<SensitivityAnalysisParametersInjectionsSetEntity> sensitivityInjectionsSet;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensibility_analysis_parameters_id")
    private List<SensitivityAnalysisParametersInjectionsEntity> sensitivityInjections;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensibility_analysis_parameters_id")
    private List<SensitivityAnalysisParametersHvdcEntity> sensitivityHVDC;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensibility_analysis_parameters_id")
    private List<SensitivityAnalysisParametersPstEntity> sensitivityPST;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sensibility_analysis_parameters_id")
    private List<SensitivityAnalysisParametersNodesEntity> sensitivityNodes;
}