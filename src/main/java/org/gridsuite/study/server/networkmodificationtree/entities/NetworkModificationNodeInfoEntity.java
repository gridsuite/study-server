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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
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
}
