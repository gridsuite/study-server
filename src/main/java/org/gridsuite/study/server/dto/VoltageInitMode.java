/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
public enum VoltageInitMode {
    UNIFORM_VALUES, // v=1pu, theta=0
    PREVIOUS_VALUES,
    DC_VALUES; // preprocessing to compute DC angles
}
