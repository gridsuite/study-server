/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.*;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * @author AJELLAL Ali <ali.ajellal@rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "securityAnalysisParameters")
public class SecurityAnalysisParametersEntity {
    public SecurityAnalysisParametersEntity(double lowVoltageAbsoluteThreshold, double lowVoltageProportionalThreshold, double highVoltageAbsoluteThreshold, double highVoltageProportionalThreshold, double flowProportionalThreshold) {
        this(null, lowVoltageAbsoluteThreshold, lowVoltageProportionalThreshold, highVoltageAbsoluteThreshold, highVoltageProportionalThreshold, flowProportionalThreshold);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "lowVoltageAbsoluteThreshold")
    private double lowVoltageAbsoluteThreshold;

    @Column(name = "lowVoltageProportionalThreshold")
    private double lowVoltageProportionalThreshold;

    @Column(name = "highVoltageAbsoluteThreshold")
    private double highVoltageAbsoluteThreshold;

    @Column(name = "highVoltageProportionalThreshold")
    private double highVoltageProportionalThreshold;

    @Column(name = "flowProportionalThreshold")
    private double flowProportionalThreshold;

}
