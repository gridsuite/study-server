/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.modification;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Setter
@Getter
@NoArgsConstructor
@ToString
@Builder
@Schema(description = "Network modification result")
public class NetworkModificationResult {
    public enum ApplicationStatus {
        ALL_OK,
        WITH_WARNINGS,
        WITH_ERRORS
    }

    @Schema(description = "Global application status")
    @Builder.Default
    ApplicationStatus applicationStatus = ApplicationStatus.ALL_OK;

    @Schema(description = "Last group application status")
    @Builder.Default
    ApplicationStatus lastGroupApplicationStatus = ApplicationStatus.ALL_OK;

    @Schema(description = "Network modification impacts")
    @Builder.Default
    private List<SimpleElementImpact> networkImpacts = List.of();

    public Set<String> getImpactedSubstationsIds() {
        return networkImpacts.stream()
            .filter(impact -> impact.getImpactType() != SimpleElementImpact.SimpleImpactType.DELETION)
            .flatMap(impact -> impact.getSubstationIds().stream())
            .collect(Collectors.toCollection(TreeSet::new));
    }
}
