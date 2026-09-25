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

import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParametersReferences.nullSafe;
import static org.gridsuite.study.server.dto.studyexport.parameters.ExportedParametersReferences.toUuidSet;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityAnalysisExportedParameters(List<ContingencyListsInfos> contingencyListsInfos) implements ExportedParametersReferences {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContingencyListsInfos(List<UUID> contingencyLists) { }

    @Override
    public Set<UUID> getContingencyListUuids() {
        return toUuidSet(nullSafe(contingencyListsInfos).flatMap(infos -> nullSafe(infos.contingencyLists())));
    }
}
