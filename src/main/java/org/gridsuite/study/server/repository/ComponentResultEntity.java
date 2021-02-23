/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.powsybl.loadflow.LoadFlowResult;
import lombok.*;

import javax.persistence.*;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "componentResult")
public class ComponentResultEntity {

    public ComponentResultEntity(int componentNum, LoadFlowResult.ComponentResult.Status status,
                                 int iterationCount, String slackBusId, double slackBusActivePowerMismatch,
                                 LoadFlowResultEntity loadFlowResult) {
        this.componentNum = componentNum;
        this.status = status;
        this.iterationCount = iterationCount;
        this.slackBusId = slackBusId;
        this.slackBusActivePowerMismatch = slackBusActivePowerMismatch;
        this.loadFlowResult = loadFlowResult;
    }

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private Long id;

    @Column(name = "componentNum")
    private int componentNum;

    @Column(name = "status")
    private LoadFlowResult.ComponentResult.Status status;

    @Column(name = "iterationCount")
    private int iterationCount;

    @Column(name = "slackBusId")
    private String slackBusId;

    @Column(name = "slackBusActivePowerMismatch")
    private double slackBusActivePowerMismatch;

    @ManyToOne
    @JoinColumn(name = "loadFlowResult_id",
            foreignKey = @ForeignKey(
                    name = "componentResult_loadFlowResult_fk"
            ))
    @JsonIgnore
    private LoadFlowResultEntity loadFlowResult;
}

