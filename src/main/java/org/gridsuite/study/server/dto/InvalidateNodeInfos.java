/**
 * Copyright (c) 2022 RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Nicolas Noir <nicolas.noir at rte-france.com>
 */

@NoArgsConstructor
@Getter
public class InvalidateNodeInfos {

    @Setter
    private UUID networkUuid;

    private List<UUID> impactedNodeUuids = new ArrayList<>();

    private List<UUID> reportUuids = new ArrayList<>();

    private List<UUID> reportUsageUuids = new ArrayList<>();

    private List<String> variantIds = new ArrayList<>();

    private List<UUID> securityAnalysisResultUuids = new ArrayList<>();

    public void addImpactedNodeUuid(UUID nodeUuid) {
        impactedNodeUuids.add(nodeUuid);
    }

    public void addReportUuid(UUID reportUuid) {
        reportUuids.add(reportUuid);
    }

    public void addReportUsageUuid(UUID reportUuid) {
        reportUsageUuids.add(reportUuid);
    }

    public void addVariantId(String variantId) {
        variantIds.add(variantId);
    }

    public void addSecurityAnalysisResultUuid(UUID securityAnalysisResultUuid) {
        securityAnalysisResultUuids.add(securityAnalysisResultUuid);
    }
}
