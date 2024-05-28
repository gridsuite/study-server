/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit.results;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Ayoub LABIDI <ayoub.labidi at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VoltageInitResult {

    private UUID resultUuid;

    private OffsetDateTime writeTimeStamp;

    private Map<String, String> indicators;

    private List<ReactiveSlack> reactiveSlacks;

    private List<BusVoltage> busVoltages;

    private UUID modificationsGroupUuid;
}
