/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.dto;

import com.datastax.driver.core.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

import java.io.Serializable;

@UserDefinedType("loadFlowResult")
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class LoadFlowResult implements Serializable {

    public enum LoadFlowStatus {
        NOT_DONE,
        RUNNING,
        CONVERGED,
        DIVERGED
    }

    @CassandraType(type = DataType.Name.TEXT)
    private LoadFlowStatus status = LoadFlowStatus.NOT_DONE;
}
