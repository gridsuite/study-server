/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import com.powsybl.commons.PowsyblException;
import org.gridsuite.study.server.dto.modification.ModificationType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
public class ModificationTypeTest {

    @Test
    public void test() {
        assertEquals("loads", ModificationType.getUriFromType(ModificationType.LOAD_CREATION));
        assertEquals("generators", ModificationType.getUriFromType(ModificationType.GENERATOR_CREATION));
        assertThrows(PowsyblException.class, () -> ModificationType.getUriFromType(ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION));

        assertEquals(StudyException.Type.LOAD_CREATION_FAILED, ModificationType.getExceptionFromType(ModificationType.LOAD_CREATION));
        assertEquals(StudyException.Type.GENERATOR_CREATION_FAILED, ModificationType.getExceptionFromType(ModificationType.GENERATOR_CREATION));
        assertThrows(PowsyblException.class, () -> ModificationType.getExceptionFromType(ModificationType.EQUIPMENT_ATTRIBUTE_MODIFICATION));
    }
}
