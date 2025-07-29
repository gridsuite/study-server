/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.diagramgridlayout.nad;

import lombok.*;

import java.util.*;

import org.gridsuite.study.server.dto.diagramgridlayout.diagramlayout.NadVoltageLevelPositionInfos;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class NadConfigInfos {
    private UUID id;
    @Builder.Default
    private Set<String> voltageLevelIds = new HashSet<>();
    @Builder.Default
    private List<NadVoltageLevelPositionInfos> positions = new ArrayList<>();
}
