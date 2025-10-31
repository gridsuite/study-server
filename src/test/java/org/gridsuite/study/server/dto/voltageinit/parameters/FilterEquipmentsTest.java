/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit.parameters;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mohamed BENREJEB <mohamed.ben-rejeb at rte-france.com>
 */
class FilterEquipmentsTest {

    @Test
    void builderShouldSetIsValidFlag() {
        UUID filterId = UUID.randomUUID();
        FilterEquipments filterEquipments = FilterEquipments.builder()
            .filterId(filterId)
            .filterName("filterName")
            .isValid(true)
            .build();

        assertEquals(filterId, filterEquipments.getFilterId());
        assertEquals("filterName", filterEquipments.getFilterName());
        assertTrue(filterEquipments.isValid());
    }
}
