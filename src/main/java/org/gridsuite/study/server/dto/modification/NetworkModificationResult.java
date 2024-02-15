/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
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

import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact;
import org.gridsuite.study.server.dto.impacts.CollectionElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact.ImpactType;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact.SimpleImpactType;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Sylvain Bouzols <sylvain.bouzols at rte-france.com>
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
    private List<AbstractBaseImpact> networkImpacts = List.of();

    public Set<String> getImpactedSubstationsIds() {
        Set<String> ids = new TreeSet<>();
        networkImpacts.stream()
            .filter(impact -> impact.getImpactType() == ImpactType.SIMPLE && ((SimpleElementImpact) impact).getSimpleImpactType() != SimpleImpactType.DELETION)
            .forEach(impact -> {
                if (impact instanceof SimpleElementImpact simpleImpact) {
                    ids.addAll(simpleImpact.getSubstationIds());
                } else if (impact instanceof CollectionElementImpact) {
                    // TODO: Do nothing for now
                }
            });
        return ids;
    }

}
