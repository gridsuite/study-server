/**
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

/**
 * @author Franck Lecuyer <franck.lecuyer at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Build infos")
public class BuildInfos {
    private String originVariantId;

    private String destinationVariantId;

    private List<UUID> modificationGroupUuids = new ArrayList<>();

    // map with modification groups as key, modification to excludes as value
    private Map<UUID, Set<UUID>> modificationUuidsToExclude = new HashMap<>();

    /**
     * Reports generated during this build operation.
     * These are new reports created when applying modifications.
     */
    private List<ReportInfos> reportsInfos = new ArrayList<>();

    /**
     * Reports inherited from the nearest built parent node.
     * These ensure child nodes maintain references to ancestor reports,
     * preventing premature deletion when intermediate nodes are unbuilt.
     */
    private List<ReportInfos> inheritedReportsInfos = new ArrayList<>();

    public void insertModificationInfos(UUID modificationGroupUuid, Set<UUID> modificationUuidsToExclude, ReportInfos reportInfos) {
        if (modificationUuidsToExclude != null && !modificationUuidsToExclude.isEmpty()) {
            this.modificationUuidsToExclude.put(modificationGroupUuid, modificationUuidsToExclude);
        }
        modificationGroupUuids.add(0, modificationGroupUuid);
        reportsInfos.add(0, reportInfos);
    }

    /**
     * Adds a report inherited from a built parent node.
     * Inherited reports are added at the end to maintain proper ordering:
     * parent reports come before child reports.
     *
     * @param nodeUuid The UUID of the node that owns this report
     * @param reportUuid The UUID of the report
     */
    public void addInheritedReport(UUID nodeUuid, UUID reportUuid) {
        inheritedReportsInfos.add(new ReportInfos(reportUuid, nodeUuid));
    }

    /**
     * Gets all reports (both new and inherited) as a single map.
     * Useful for storing the complete report set in the database.
     *
     * @return Map of node UUID to report UUID
     */
    public Map<UUID, UUID> getAllReportsAsMap() {
        Map<UUID, UUID> allReports = new LinkedHashMap<>();

        // Add inherited reports first (parent reports come first)
        inheritedReportsInfos.forEach(r ->
                allReports.put(r.nodeUuid(), r.reportUuid()));

        // Add new reports (may override inherited if same node)
        reportsInfos.forEach(r ->
                allReports.put(r.nodeUuid(), r.reportUuid()));

        return allReports;
    }
}
