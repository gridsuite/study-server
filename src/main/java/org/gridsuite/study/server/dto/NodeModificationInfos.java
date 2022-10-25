/**
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@Getter
@Builder
public class NodeModificationInfos {
    private UUID id;

    private UUID modificationGroupUuid;

    private String variantId;

    private UUID reportUuid;

    private UUID securityAnalysisUuid;

    private UUID sensitivityAnalysisUuid;

    private UUID shortCircuitAnalysisUuid;
}
