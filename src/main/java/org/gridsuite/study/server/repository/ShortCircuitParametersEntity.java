/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.shortcircuit.StudyType;
import lombok.*;

import javax.persistence.*;
import java.util.Set;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "shortCircuitParameters")
public class ShortCircuitParametersEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "withLimitViolations", columnDefinition = "boolean default true")
    private boolean withLimitViolations;

    @Column(name = "withVoltageMap", columnDefinition = "boolean default true")
    private boolean withVoltageMap;

    @Column(name = "withFeederResult", columnDefinition = "boolean default true")
    private boolean withFeederResult;

    @Column(name = "studyType")
    @Enumerated(EnumType.STRING)
    private StudyType studyType;

    @Column(name = "minVoltageDropProportionalThreshold")
    private double minVoltageDropProportionalThreshold;

}
