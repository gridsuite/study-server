/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis;

import lombok.*;
import org.gridsuite.study.server.dto.sensianalysis.SensitivityAnalysisInputData;

import jakarta.persistence.*;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "sensitivityFactorWithDistribTypeEntity")
public class SensitivityFactorWithDistribTypeEntity extends SensitivityFactorEntity {

    @Column(name = "distributionType")
    @Enumerated(EnumType.STRING)
    private SensitivityAnalysisInputData.DistributionType distributionType;
}
