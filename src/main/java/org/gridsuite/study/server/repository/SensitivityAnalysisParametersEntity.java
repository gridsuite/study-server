/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;

import javax.persistence.*;
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
    public SensitivityAnalysisParametersEntity(double flowFlowSensitivityValueThreshold, double angleFlowSensitivityValueThreshold, double flowVoltageSensitivityValueThreshold) {
        this(null, flowFlowSensitivityValueThreshold, angleFlowSensitivityValueThreshold, flowVoltageSensitivityValueThreshold);
    }

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
}
