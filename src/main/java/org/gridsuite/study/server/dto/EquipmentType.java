/*
  Copyright (c) 2021, All partners of the iTesla project (http://www.itesla-project.eu/consortium)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.powsybl.iidm.network.*;
import lombok.NonNull;
import org.gridsuite.study.server.StudyException;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public enum EquipmentType {
    // Same as com.powsybl.iidm.network.SwitchKind
    BREAKER,
    DISCONNECTOR,
    LOAD_BREAK_SWITCH,

    // Same as com.powsybl.iidm.network.ConnectableType
    BUSBAR_SECTION,
    LINE,
    TWO_WINDINGS_TRANSFORMER,
    THREE_WINDINGS_TRANSFORMER,
    GENERATOR,
    BATTERY,
    LOAD,
    SHUNT_COMPENSATOR,
    DANGLING_LINE,
    STATIC_VAR_COMPENSATOR,
    HVDC_CONVERTER_STATION,

    // Other
    CONFIGURED_BUS,
    HVDC,
    SUBSTATION,
    VOLTAGE_LEVEL;

    public static EquipmentType getType(@NonNull Identifiable<?> identifiable) {
        try {
            if (identifiable instanceof Switch) {
                return EquipmentType.valueOf(((Switch) identifiable).getKind().name());
            } else if (identifiable instanceof Connectable) {
                return EquipmentType.valueOf(((Connectable<?>) identifiable).getType().name());
            } else if (identifiable instanceof Bus) {
                return EquipmentType.CONFIGURED_BUS;
            } else if (identifiable instanceof HvdcLine) {
                return EquipmentType.HVDC;
            } else if (identifiable instanceof Substation) {
                return EquipmentType.SUBSTATION;
            } else if (identifiable instanceof VoltageLevel) {
                return EquipmentType.VOLTAGE_LEVEL;
            }
        } catch (IllegalArgumentException e) {
            throw StudyException.createEquipmentTypeUnknown(identifiable.getClass().getSimpleName());
        }

        throw StudyException.createEquipmentTypeUnknown(identifiable.getClass().getSimpleName());
    }
}
