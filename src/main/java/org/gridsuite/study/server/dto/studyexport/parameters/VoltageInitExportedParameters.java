/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.parameters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParametersReferences.nullSafe;
import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParametersReferences.toUuidSet;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VoltageInitExportedParameters(
        List<VoltageLimit> voltageLimitsModification,
        List<VoltageLimit> voltageLimitsDefault,
        List<FilterEquipments> variableQGenerators,
        List<FilterEquipments> variableTwoWindingsTransformers,
        List<FilterEquipments> variableShuntCompensators
) implements ExportedParametersReferences {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VoltageLimit(List<FilterEquipments> filters) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterEquipments(UUID filterId) { }

    @Override
    public Set<UUID> getFilterUuids() {
        Stream<FilterEquipments> limitFilters = Stream.concat(nullSafe(voltageLimitsModification), nullSafe(voltageLimitsDefault))
                .flatMap(voltageLimit -> nullSafe(voltageLimit.filters()));
        Stream<FilterEquipments> variableFilters = Stream.of(variableQGenerators, variableTwoWindingsTransformers, variableShuntCompensators)
                .flatMap(ExportedParametersReferences::nullSafe);
        return toUuidSet(Stream.concat(limitFilters, variableFilters).map(FilterEquipments::filterId));
    }
}
