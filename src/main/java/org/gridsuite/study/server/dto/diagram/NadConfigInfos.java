/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.diagram;

import lombok.*;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;

/**
 * @author Charly Boutier <charly.boutier at rte-france.com>
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class NadConfigInfos {
    private UUID id;
    private Boolean withGeoData;
    @Builder.Default
    private List<String> voltageLevelIds = new ArrayList<>();
    private Integer depth;
    private Integer scalingFactor;
    private Double radiusFactor;
    @Builder.Default
    private List<NadVoltageLevelPositionInfos> positions = new ArrayList<>();
}
