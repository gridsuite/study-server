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
public record SensitivityAnalysisExportedParameters(
        List<SensitivityFactor> sensitivityInjectionsSet,
        List<SensitivityFactor> sensitivityInjection,
        List<SensitivityFactor> sensitivityHVDC,
        List<SensitivityFactor> sensitivityPST,
        List<SensitivityFactor> sensitivityNodes
) implements ExportedParametersReferences {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SensitivityFactor(
            List<UUID> monitoredBranches,
            List<UUID> injections,
            List<UUID> hvdcs,
            List<UUID> psts,
            List<UUID> monitoredVoltageLevels,
            List<UUID> equipmentsInVoltageRegulation,
            List<UUID> contingencies
    ) {
        Stream<UUID> filterUuids() {
            return Stream.of(monitoredBranches, injections, hvdcs, psts, monitoredVoltageLevels, equipmentsInVoltageRegulation)
                    .flatMap(ExportedParametersReferences::nullSafe);
        }
    }

    private Stream<SensitivityFactor> allFactors() {
        return Stream.of(sensitivityInjectionsSet, sensitivityInjection, sensitivityHVDC, sensitivityPST, sensitivityNodes)
                .flatMap(ExportedParametersReferences::nullSafe);
    }

    @Override
    public Set<UUID> getFilterUuids() {
        return toUuidSet(allFactors().flatMap(SensitivityFactor::filterUuids));
    }

    @Override
    public Set<UUID> getContingencyListUuids() {
        return toUuidSet(allFactors().flatMap(factor -> nullSafe(factor.contingencies())));
    }
}
