/*
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
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "dynamicSimulationParameters")
public class DynamicSimulationParametersEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "startTime")
    private double startTime;

    @Column(name = "stopTime")
    private double stopTime;

    @Column(name = "solverId", columnDefinition = "varchar(255) default null")
    private String solverId;

    @Column(name = "solvers", columnDefinition = "text default null")
    private String solvers;

    @Column(name = "mapping", columnDefinition = "varchar(255) default null")
    private String mapping;

    @Column(name = "curves", columnDefinition = "text default null")
    private String curves;
}
