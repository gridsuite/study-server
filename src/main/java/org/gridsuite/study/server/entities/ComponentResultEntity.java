/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.entities;

import com.powsybl.loadflow.LoadFlowResult;
import lombok.*;
import org.springframework.data.annotation.Id;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "componentResult")
public class ComponentResultEntity implements Serializable {
    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name  =  "id")
    private long id;

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

    @Transient
    @ManyToOne
    @JoinColumn(name = "id", nullable = false)
    private LoadFlowResultEntity loadFlowResult;

}

