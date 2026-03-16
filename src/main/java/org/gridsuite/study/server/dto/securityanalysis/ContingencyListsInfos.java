/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.securityanalysis;

import lombok.*;
import org.gridsuite.study.server.dto.EquipmentsContainer;

import java.util.List;

/**
 * @author Caroline Jeandat {@literal <caroline.jeandat at rte-france.com>}
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ContingencyListsInfos {
    private List<EquipmentsContainer> contingencyLists;
    private String description;
    private boolean activated;
}
