/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

/**
 * Copied from network-map-server.
 *
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public enum ElementType {
        SUBSTATION,
        VOLTAGE_LEVEL,
        BRANCH,
        LINE,
        TIE_LINE,
        HVDC_LINE,
        HVDC_LINE_LCC,
        HVDC_LINE_VSC,
        LOAD,
        TWO_WINDINGS_TRANSFORMER,
        THREE_WINDINGS_TRANSFORMER,
        BUSBAR_SECTION,
        BUS,
        GENERATOR,
        BATTERY,
        SHUNT_COMPENSATOR,
        DANGLING_LINE,
        STATIC_VAR_COMPENSATOR,
        LCC_CONVERTER_STATION,
        VSC_CONVERTER_STATION
}
