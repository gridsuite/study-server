/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.networkmodificationtree.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gridsuite.study.server.repository.AbstractManuallyAssignedIdentifierEntity;

import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToOne;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractNodeInfoEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> {

    @Id
    @Column(name = "idNode", insertable = false, updatable = false)
    private UUID idNode; // don't bother with getter/setter since the `idNode` reference handles everything

    @Override
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
    Boolean readOnly;
}
