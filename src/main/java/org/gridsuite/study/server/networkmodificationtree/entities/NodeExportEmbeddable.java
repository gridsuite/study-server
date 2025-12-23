/*
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;
import org.gridsuite.study.server.dto.networkexport.ExportNetworkStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeExportInfos;

import java.util.UUID;

/**
 * @author Rehili Ghazwa <ghazwa.rehili at rte-france.com>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class NodeExportEmbeddable {

    @Column(name = "export_uuid", nullable = false, unique = true)
    private UUID exportUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExportNetworkStatus status;

    @Column(name = "export_to_explorer")
    private boolean exportToExplorer;

    @Column(name = "directory_uuid")
    private UUID directoryUuid;

    @Column(name = "description")
    private String description;

    public static NodeExportEmbeddable toNodeExportEmbeddable(UUID exportUuid, ExportNetworkStatus status) {
        return NodeExportEmbeddable.builder()
                .exportUuid(exportUuid)
                .status(status)
                .exportToExplorer(false)
                .build();
    }

    public static NodeExportEmbeddable toNodeExportEmbeddable(UUID exportUuid, ExportNetworkStatus status, boolean exportToExplorer, UUID directoryUuid, String description) {
        return NodeExportEmbeddable.builder()
                .exportUuid(exportUuid)
                .status(status)
                .exportToExplorer(exportToExplorer)
                .directoryUuid(directoryUuid)
                .description(description)
                .build();
    }

    public NodeExportInfos toNodeExportInfos() {
        return new NodeExportInfos(exportUuid, status, exportToExplorer, directoryUuid, description);
    }
}
