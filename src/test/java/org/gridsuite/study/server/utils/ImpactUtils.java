/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.powsybl.iidm.network.IdentifiableType;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact;
import org.gridsuite.study.server.dto.modification.SimpleElementImpact.SimpleImpactType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public final class ImpactUtils {

    private ImpactUtils() {
    }

    public static Optional<NetworkModificationResult> createModificationResultWithElementImpact(SimpleImpactType impactType, IdentifiableType elementType, String elementId, Set<String> substationIds) {
        return Optional.of(NetworkModificationResult.builder()
            .networkImpacts(List.of(createElementImpact(impactType, elementType, elementId, substationIds)))
            .build());
    }

    public static Optional<NetworkModificationResult> createModificationResultWithElementImpact(SimpleElementImpact impact) {
        return Optional.of(NetworkModificationResult.builder()
            .networkImpacts(List.of(impact))
            .build());
    }

    public static SimpleElementImpact createCreationImpactType(IdentifiableType elementType, String elementId, Set<String> substationIds) {
        return createElementImpact(SimpleImpactType.CREATION, elementType, elementId, substationIds);
    }

    private static SimpleElementImpact createElementImpact(SimpleImpactType impactType, IdentifiableType elementType, String elementId, Set<String> substationIds) {
        return SimpleElementImpact.builder()
            .impactType(impactType)
            .elementType(elementType)
            .elementId(elementId)
            .substationIds(substationIds).build();
    }
}
