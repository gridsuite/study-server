/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.sensianalysis;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.dto.EquipmentsContainer;

import java.util.List;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Getter
@Setter
@AllArgsConstructor
public class SensitivityPstInfos {
    List<EquipmentsContainer> monitoredBranches;
    SensitivityType sensitivityType;
    List<EquipmentsContainer> psts;
    List<EquipmentsContainer> contingencies;
    boolean activated;
}
