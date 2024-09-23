/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.networkmodificationtree.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.networkmodificationtree.dto.BuildStatus;
import org.gridsuite.study.server.networkmodificationtree.dto.NodeBuildStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * @author Hugo Marcellin <hugo.marcelin at rte-france.com>
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Embeddable
public class NodeBuildStatusEmbeddable {

    @Column(name = "buildStatusLocal", nullable = false)
    @Enumerated(EnumType.STRING)
    private BuildStatus localBuildStatus;

    @Column(name = "buildStatusGlobal", nullable = false)
    @Enumerated(EnumType.STRING)
    private BuildStatus globalBuildStatus;

    public NodeBuildStatus toDto() {
        return NodeBuildStatus.from(localBuildStatus, globalBuildStatus);
    }

    public static NodeBuildStatusEmbeddable from(BuildStatus localBuildStatus, BuildStatus globalBuildStatus) {
        return NodeBuildStatusEmbeddable.builder()
            .localBuildStatus(localBuildStatus)
            .globalBuildStatus(globalBuildStatus)
            .build();
    }
}
