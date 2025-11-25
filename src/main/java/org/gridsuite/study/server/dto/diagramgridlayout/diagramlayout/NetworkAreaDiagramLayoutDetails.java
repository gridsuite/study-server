/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class NetworkAreaDiagramLayoutDetails extends AbstractDiagramLayout {
    UUID originalNadConfigUuid;
    UUID originalFilterUuid;
    UUID currentFilterUuid;
    String name;
    Set<String> voltageLevelIds;
    List<NadVoltageLevelPositionInfos> positions;
}
