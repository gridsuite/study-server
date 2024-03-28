/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.voltageinit.parameters;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class StudyVoltageInitParameters {

    private VoltageInitParametersInfos computationParameters;

    private boolean applyModifications;
}
