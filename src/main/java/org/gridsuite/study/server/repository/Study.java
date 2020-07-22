/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.io.Serializable;
import java.util.UUID;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Table
public class Study implements Serializable {

    @PrimaryKey("studyName")
    private String name;

    @Column("networkUuid")
    private UUID networkUuid;

    @Column("networkId")
    private String networkId;

    @Column("description")
    private String description;

    @Column("caseFormat")
    private String caseFormat;

    @Column("caseUuid")
    private UUID caseUuid;

    @Column("casePrivate")
    private boolean casePrivate;

    @Column("ownerEmail")
    private String ownerEmail;

    @Column("ownerName")
    private String ownerName;
}
