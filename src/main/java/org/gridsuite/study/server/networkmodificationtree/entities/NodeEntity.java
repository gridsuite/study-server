/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.StudyEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "Node", indexes = {
    @Index(name = "nodeEntity_parentNode_idx", columnList = "parentNode"),
    @Index(name = "nodeEntity_studyId_idx", columnList = "study_id")
    }
)
public class NodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    UUID idNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parentNode", foreignKey = @ForeignKey(name = "parent_node_id_fk_constraint"))
    NodeEntity parentNode;

    @Column
    @Enumerated(EnumType.STRING)
    NodeType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", foreignKey = @ForeignKey(name = "study_id_fk_constraint"))
    StudyEntity study;

    @Column(name = "stashed")
    boolean stashed;

    @Column(name = "stash_date", columnDefinition = "timestamptz")
    private Instant stashDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "node_export",
            joinColumns = @JoinColumn(name = "node_id", foreignKey = @ForeignKey(name = "node_export_node_fk")),
            indexes = @Index(name = "node_export_node_id_idx", columnList = "node_id")
    )
    @OrderColumn(name = "export_order")
    private List<NodeExportEmbeddable> nodeExportNetwork = new ArrayList<>();
}
