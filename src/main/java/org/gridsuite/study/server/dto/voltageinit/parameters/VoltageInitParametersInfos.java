/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit.parameters;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Voltage init parameters")
public class VoltageInitParametersInfos {

    List<VoltageLimitInfos> voltageLimitsModification;

    List<VoltageLimitInfos> voltageLimitsDefault;

    List<FilterEquipments> variableQGenerators;

    EquipmentsSelectionType generatorsSelectionType;

    List<FilterEquipments> variableTwoWindingsTransformers;

    EquipmentsSelectionType twoWindingsTransformersSelectionType;

    List<FilterEquipments> variableShuntCompensators;

    EquipmentsSelectionType shuntCompensatorsSelectionType;

    double reactiveSlacksThreshold;

    double shuntCompensatorActivationThreshold;

    boolean updateBusVoltage;
}
