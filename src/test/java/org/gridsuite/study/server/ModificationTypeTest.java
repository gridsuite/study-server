/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.PowsyblException;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
class ModificationTypeTest {
    @Test
    void test() {
        assertEquals("loads", ModificationType.getUriFromType(ModificationType.LOAD_CREATION));
        assertEquals("generators", ModificationType.getUriFromType(ModificationType.GENERATOR_CREATION));
        assertEquals("lines", ModificationType.getUriFromType(ModificationType.LINE_CREATION));
        assertEquals("shunt-compensators", ModificationType.getUriFromType(ModificationType.SHUNT_COMPENSATOR_CREATION));
        assertEquals("generators-modification", ModificationType.getUriFromType(ModificationType.GENERATOR_MODIFICATION));
        assertEquals("load-scaling", ModificationType.getUriFromType(ModificationType.LOAD_SCALING));
        assertEquals("delete-voltage-level-on-line", ModificationType.getUriFromType(ModificationType.DELETE_VOLTAGE_LEVEL_ON_LINE));
        assertEquals("delete-attaching-line", ModificationType.getUriFromType(ModificationType.DELETE_ATTACHING_LINE));
        assertEquals("generator-scaling", ModificationType.getUriFromType(ModificationType.GENERATOR_SCALING));
        assertThrows(PowsyblException.class, () -> ModificationType.getUriFromType(ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION));
    }
}
