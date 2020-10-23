/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository;

import com.datastax.driver.core.DataType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 * @author Etienne Homer <etienne.homer at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@UserDefinedType("loadFlowResult")
public class LoadFlowResultEntity implements Serializable {

    private boolean ok;

    @CassandraType(type = DataType.Name.MAP, typeArguments = { DataType.Name.TEXT, DataType.Name.TEXT })
    private Map<String, String> metrics;

    private String logs;

    @CassandraType(type = DataType.Name.LIST, typeArguments = { DataType.Name.UDT }, userTypeName = "componentResult")
    private List<ComponentResultEntity> componentResults;
}
