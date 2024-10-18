/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.utils;

import com.powsybl.iidm.network.IdentifiableType;
import org.gridsuite.study.server.dto.impacts.AbstractBaseImpact;
import org.gridsuite.study.server.dto.impacts.CollectionElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact;
import org.gridsuite.study.server.dto.impacts.SimpleElementImpact.SimpleImpactType;
import org.gridsuite.study.server.dto.modification.NetworkModificationResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
public final class ImpactUtils {
    private ImpactUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Optional<NetworkModificationResult> createModificationResultWithElementImpact(SimpleImpactType type, IdentifiableType elementType, String elementId, Set<String> substationIds) {
        return Optional.of(NetworkModificationResult.builder()
            .networkImpacts(createSubstationImpacts(substationIds))
            .build());
    }

    public static List<AbstractBaseImpact> createSubstationImpacts(Set<String> substationIds) {
        return substationIds.stream().map(id -> createElementImpact(SimpleImpactType.MODIFICATION, IdentifiableType.SUBSTATION, id, Set.of(id)))
                                    .collect(Collectors.toList());
    }

    public static SimpleElementImpact createElementImpact(SimpleImpactType type, IdentifiableType elementType, String elementId, Set<String> substationIds) {
        return SimpleElementImpact.builder()
            .simpleImpactType(type)
            .elementType(elementType)
            .elementId(elementId)
            .substationIds(substationIds).build();
    }

    public static CollectionElementImpact createCollectionElementImpact(IdentifiableType elementType) {
        return CollectionElementImpact.builder()
            .elementType(elementType)
            .build();
    }
}
