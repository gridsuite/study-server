/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.StudyEntity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
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
    @GeneratedValue(strategy  =  GenerationType.AUTO)
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

}
