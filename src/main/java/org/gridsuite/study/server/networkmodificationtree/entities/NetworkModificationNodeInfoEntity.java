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

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.ForeignKey;
import javax.persistence.Index;
import javax.persistence.Table;
import java.util.Set;
import java.util.UUID;

/**
 * @author Jacques Borsenberger <jacques.borsenberger at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Entity
@Table(name = "NetworkModificationNodeInfo  ")
public class NetworkModificationNodeInfoEntity extends AbstractNodeInfoEntity {

    @Column
    UUID networkModificationId;

    @Column
    String variantId;

    @Column(name = "modificationsToExclude")
    @ElementCollection
    @CollectionTable(foreignKey = @ForeignKey(name = "networkModificationNodeInfoEntity_modificationsToExclude_fk"), indexes = {@Index(name = "networkModificationNodeInfoEntity_modificationsToExclude_idx", columnList = "network_modification_node_info_entity_id_node")})
    private Set<UUID> modificationsToExclude;
}
