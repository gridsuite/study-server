/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import com.powsybl.shortcircuit.StudyType;
import lombok.*;

import javax.persistence.*;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "shortCircuitParameters")
public class ShortCircuitParametersEntity {

    public ShortCircuitParametersEntity(boolean withLimitViolations, boolean withVoltageResult, boolean withFortescueResult, boolean withFeederResult, StudyType studyType, double minVoltageDropProportionalThreshold) {
        this(null, withLimitViolations, withVoltageResult, withFortescueResult, withFeederResult, studyType, minVoltageDropProportionalThreshold);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "withLimitViolations", columnDefinition = "boolean default true")
    private boolean withLimitViolations;

    @Column(name = "withVoltageResult", columnDefinition = "boolean default true")
    private boolean withVoltageResult;

    @Column(name = "withFortescueResult", columnDefinition = "boolean default true")
    private boolean withFortescueResult;

    @Column(name = "withFeederResult", columnDefinition = "boolean default true")
    private boolean withFeederResult;

    @Column(name = "studyType")
    @Enumerated(EnumType.STRING)
    private StudyType studyType;

    @Column(name = "minVoltageDropProportionalThreshold")
    private double minVoltageDropProportionalThreshold;
}
