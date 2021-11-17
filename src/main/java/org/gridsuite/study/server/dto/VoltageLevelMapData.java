/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.iidm.network.TopologyKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class VoltageLevelMapData {

    private String id;

    private String name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String substationId;

    private double nominalVoltage;

    private TopologyKind topologyKind;
}
