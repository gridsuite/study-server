/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository.sensianalysis;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "sensitivityFactorEntity2")
public class SensitivityFactorEntity2 extends SensitivityFactorEntity {

}
