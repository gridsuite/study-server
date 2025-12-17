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

    @Column(name = "export_uuid", nullable = false)
    private UUID exportUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExportNetworkStatus status;

    public static NodeExportEmbeddable toNodeExportEmbeddable(UUID exportUuid, ExportNetworkStatus status) {
        return NodeExportEmbeddable.builder()
                .exportUuid(exportUuid)
                .status(status)
                .build();
    }
}
