/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit.parameters;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@EqualsAndHashCode
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoltageLimitInfos {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Integer priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double lowVoltageLimit;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Double highVoltageLimit;

    List<FilterEquipments> filters;
}
