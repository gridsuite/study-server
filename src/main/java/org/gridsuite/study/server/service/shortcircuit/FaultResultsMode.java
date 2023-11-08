/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.shortcircuit;

/**
 * Specify if the faults are present in the result DTO and if so what they contain
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com
 */
public enum FaultResultsMode {
    /**
     * No fault present
     */
    NONE,
    /**
     * Present but without the limit violations and feeders
     */
    BASIC,
    /**
     * Present with all fields but filtered by limit violations presence
     */
    WITH_LIMIT_VIOLATIONS,
    /**
     * Present with all fields
     */
    FULL
}
