/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto.studyexport.spreadsheets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.gridsuite.study.server.dto.studyexport.ExportedParameters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author Ghazwa Rehili <ghazwa.rehili at rte-france.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExportedSpreadsheetConfigCollection(List<SpreadsheetConfig> spreadsheetConfigs) implements ExportedParameters {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpreadsheetConfig(List<GlobalFilter> globalFilters) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GlobalFilter(UUID uuid, boolean deleted) { }

    @Override
    public Set<UUID> getFilterUuids() {
        Set<UUID> filterUuids = new HashSet<>();
        if (spreadsheetConfigs == null) {
            return filterUuids;
        }
        for (SpreadsheetConfig spreadsheetConfig : spreadsheetConfigs) {
            if (spreadsheetConfig.globalFilters() == null) {
                continue;
            }
            for (GlobalFilter globalFilter : spreadsheetConfig.globalFilters()) {
                if (globalFilter.uuid() != null && !globalFilter.deleted()) {
                    filterUuids.add(globalFilter.uuid());
                }
            }
        }
        return filterUuids;
    }
}
