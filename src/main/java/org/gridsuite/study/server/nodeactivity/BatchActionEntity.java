/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gridsuite.study.server.repository.AbstractManuallyAssignedIdentifierEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "batch_node_action",
    indexes = @Index(name = "batch_action_study_id_idx", columnList = "study_id"))
public class BatchActionEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {
    @Id
    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    public UUID getId() {
        return nodeId;
    }

    @Column(name = "study_id", nullable = false)
    private UUID studyId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private BatchActionType type;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;
}
