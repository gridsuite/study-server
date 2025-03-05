/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gridsuite.study.server.networkmodificationtree.dto.AbstractNode;
import org.gridsuite.study.server.repository.AbstractManuallyAssignedIdentifierEntity;

import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
public abstract class AbstractNodeInfoEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    @Column(name = "idNode", insertable = false, updatable = false)
    private UUID idNode; // don't bother with getter/setter since the `idNode` reference handles everything

    public UUID getId() {
        return idNode;
    }

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idNode", nullable = false)
    private NodeEntity node;

    @Column(columnDefinition = "CLOB")
    String name;

    @Column
    String description;

    @Column
    Integer columnPosition;

    @Column
    Boolean readOnly;

    public abstract AbstractNode toDto();
}
