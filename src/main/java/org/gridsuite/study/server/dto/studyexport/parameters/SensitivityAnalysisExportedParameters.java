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

import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParameters.nullSafe;
import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParameters.toUuidSet;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SensitivityAnalysisExportedParameters(
        List<SensitivityFactor> sensitivityInjectionsSet,
        List<SensitivityFactor> sensitivityInjection,
        List<SensitivityFactor> sensitivityHVDC,
        List<SensitivityFactor> sensitivityPST,
        List<SensitivityNodes> sensitivityNodes
) implements ExportedParameters {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensitivityFactor(
            List<UUID> monitoredBranches,
            List<UUID> injections,
            List<UUID> hvdcs,
            List<UUID> psts,
            List<UUID> contingencies
    ) {
        Stream<UUID> filterUuids() {
            return Stream.of(monitoredBranches, injections, hvdcs, psts)
                    .flatMap(ExportedParameters::nullSafe);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensitivityNodes(
            List<UUID> monitoredVoltageLevels,
            List<UUID> equipmentsInVoltageRegulation,
            List<UUID> contingencies
    ) {
        Stream<UUID> filterUuids() {
            return Stream.of(monitoredVoltageLevels, equipmentsInVoltageRegulation)
                    .flatMap(ExportedParameters::nullSafe);
        }
    }

    private Stream<SensitivityFactor> allFactors() {
        return Stream.of(sensitivityInjectionsSet, sensitivityInjection, sensitivityHVDC, sensitivityPST)
                .flatMap(ExportedParameters::nullSafe);
    }

    @Override
    public Set<UUID> getFilterUuids() {
        return toUuidSet(Stream.concat(
                allFactors().flatMap(SensitivityFactor::filterUuids),
                nullSafe(sensitivityNodes).flatMap(SensitivityNodes::filterUuids)));
    }

    @Override
    public Set<UUID> getContingencyListUuids() {
        return toUuidSet(Stream.concat(
                allFactors().flatMap(factor -> nullSafe(factor.contingencies())),
                nullSafe(sensitivityNodes).flatMap(nodes -> nullSafe(nodes.contingencies()))));
    }
}
