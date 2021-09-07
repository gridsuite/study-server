/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.hypothesisTree.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
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
@Table(name = "RootNodeInfo", indexes = {@Index(name = "rootNodeInfo_studyId_idx", columnList = "studyId")})
public class RootNodeInfoEntity extends AbstractNodeInfoEntity {
    public RootNodeInfoEntity(UUID idNode, NodeEntity node, String name, String description, UUID studyId) {
        super(idNode, node, name, description);
        this.studyId = studyId;
    }

    @Column(name = "studyId")
    UUID studyId;
}
