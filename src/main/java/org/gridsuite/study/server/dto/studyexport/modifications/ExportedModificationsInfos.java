/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.modifications;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.gridsuite.study.server.dto.studyexport.ExportedParameters;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.gridsuite.study.server.dto.studyexport.ExportedParameters.nullSafe;
import static org.gridsuite.study.server.dto.studyexport.ExportedParameters.toUuidSet;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExportedModificationsInfos(
        List<FilterInfos> filters,
        List<FiltersHolder> variations,
        List<FiltersHolder> assignmentInfosList,
        List<FiltersHolder> formulaInfosList,
        List<FilterInfos> generatorsWithoutOutage,
        List<FilterInfos> generatorsWithFixedSupply,
        List<GeneratorsFrequencyReserve> generatorsFrequencyReserve,
        UUID loadFlowParametersId,
        List<ExportedModificationsInfos> modificationsInfos
) implements ExportedParameters {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FilterInfos(UUID id) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FiltersHolder(List<FilterInfos> filters) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeneratorsFrequencyReserve(List<FilterInfos> generatorsFilters) { }

    private Stream<ExportedModificationsInfos> subModifications() {
        return nullSafe(modificationsInfos);
    }

    @Override
    public Set<UUID> getFilterUuids() {
        Stream<FilterInfos> ownFilters = Stream.of(
                nullSafe(filters),
                Stream.of(variations, assignmentInfosList, formulaInfosList).flatMap(ExportedParameters::nullSafe).flatMap(holder -> nullSafe(holder.filters())),
                nullSafe(generatorsWithoutOutage),
                nullSafe(generatorsWithFixedSupply),
                nullSafe(generatorsFrequencyReserve).flatMap(reserve -> nullSafe(reserve.generatorsFilters()))
        ).flatMap(s -> s);
        return toUuidSet(Stream.concat(ownFilters.map(FilterInfos::id), subModifications().flatMap(m -> m.getFilterUuids().stream())));
    }

    public Set<UUID> getLoadFlowParametersUuids() {
        return toUuidSet(Stream.concat(Stream.ofNullable(loadFlowParametersId), subModifications().flatMap(m -> m.getLoadFlowParametersUuids().stream())));
    }
}
