/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.datastax.driver.core.DataType;
import com.powsybl.loadflow.LoadFlowResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@UserDefinedType("componentResult")
public class ComponentResultEntity implements Serializable {
    @Column("componentNum")
    @CassandraType(type = DataType.Name.INT)
    private int componentNum;

    @Column("status")
    @CassandraType(type = DataType.Name.TEXT)
    private LoadFlowResult.ComponentResult.Status status;

    @Column("iterationCount")
    @CassandraType(type = DataType.Name.INT)
    private int iterationCount;

    @Column("slackBusId")
    @CassandraType(type = DataType.Name.TEXT)
    private String slackBusId;

    @Column("slackBusActivePowerMismatch")
    @CassandraType(type = DataType.Name.DOUBLE)
    private double slackBusActivePowerMismatch;

    public static List<ComponentResultEntity> toEntityList(List<LoadFlowResult.ComponentResult> componentResults) {
        List<ComponentResultEntity> entities = new ArrayList<>();
        componentResults.forEach(componentResult -> {
            entities.add(new ComponentResultEntity(componentResult.getComponentNum(), componentResult.getStatus(), componentResult.getIterationCount(), componentResult.getSlackBusId(), componentResult.getSlackBusActivePowerMismatch()));
        });
        return entities;
    }
}

