/**
 * Copyright (c) 2026, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.nodeactivity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * @author Ayoub Labidi <ayoub.labidi_externe at rte-france.com>
 */
@Entity
@Getter
@NoArgsConstructor
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "node_activity",
    uniqueConstraints = @UniqueConstraint(name = "node_activity_node_rn_uq", columnNames = {"node_id", "root_network_id"}),
    indexes = @Index(name = "node_activity_study_id_idx", columnList = "study_id"))
public class NodeActivityEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "study_id", nullable = false)
    private UUID studyId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    // null when the type affects all root networks
    @Column(name = "root_network_id")
    private UUID rootNetworkId;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private NodeActivityType type;

    // So a row left behind by a process that died can be removed
    @Column(name = "started_at", nullable = false, columnDefinition = "timestamptz")
    private Instant startedAt;

    public static NodeActivityEntity from(NodeActivityType type, UUID studyUuid, UUID rootNetworkUuid, UUID nodeUuid) {
        Objects.requireNonNull(studyUuid);
        Objects.requireNonNull(nodeUuid);
        if (!type.affectsAllRootNetworks()) {
            Objects.requireNonNull(rootNetworkUuid, () -> type + " runs in one root network");
        }
        return builder()
            .studyId(studyUuid)
            .nodeId(nodeUuid)
            .rootNetworkId(rootNetworkUuid)
            .type(type)
            .startedAt(Instant.now())
            .build();
    }

    public boolean hasConflictWith(NodeActivityEntity other, Map<UUID, Set<UUID>> ancestorsByNode) {
        return hasSameRootNetwork(other) &&
            (hasSameNode(other) || invalidates(other, ancestorsByNode) || other.invalidates(this, ancestorsByNode));
    }

    private boolean hasSameNode(NodeActivityEntity other) {
        return getNodeId().equals(other.getNodeId());
    }

    private boolean hasSameRootNetwork(NodeActivityEntity other) {
        return getType().affectsAllRootNetworks() || other.getType().affectsAllRootNetworks()
            || getRootNetworkId().equals(other.getRootNetworkId());
    }

    private boolean invalidates(NodeActivityEntity other, Map<UUID, Set<UUID>> ancestorsByNode) {
        return getType().invalidatesChildren()
            && ancestorsByNode.getOrDefault(other.getNodeId(), Set.of()).contains(getNodeId());
    }
}
