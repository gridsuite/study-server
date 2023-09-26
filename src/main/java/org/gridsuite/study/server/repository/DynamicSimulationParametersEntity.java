/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import lombok.*;

import jakarta.persistence.*;
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

    @Column(name = "solverId", columnDefinition = "varchar(255)")
    private String solverId;

    @Column(name = "solvers", columnDefinition = "CLOB")
    private String solvers;

    @Column(name = "mapping", columnDefinition = "varchar(255)")
    private String mapping;

    @Column(name = "network", columnDefinition = "CLOB")
    private String network;

    @Column(name = "curves", columnDefinition = "CLOB")
    private String curves;
}
