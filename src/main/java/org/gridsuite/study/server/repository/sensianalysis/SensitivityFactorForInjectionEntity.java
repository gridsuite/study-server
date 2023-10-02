/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "sensitivityFactorForInjectionEntity")
public class SensitivityFactorForInjectionEntity extends AbstractSensitivityFactorEntity {
}
