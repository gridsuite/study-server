/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.entities;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Chamseddoine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Table("componentResult")
public class ComponentResultEntity implements Serializable {
    @Id
    @Column("id")
    private UUID id;

    @Column("componentNum")
    private int componentNum;

    @Column("status")
    private LoadFlowResult.ComponentResult.Status status;

    @Column("iterationCount")
    private int iterationCount;

    @Column("slackBusId")
    private String slackBusId;

    @Column("slackBusActivePowerMismatch")
    private double slackBusActivePowerMismatch;

    //Foreign key (one To Many relation)
    private UUID loadFlowResultUuid;

}

