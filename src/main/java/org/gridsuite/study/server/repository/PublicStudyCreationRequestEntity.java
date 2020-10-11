/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Table("publicstudycreationrequest")
public class PublicStudyCreationRequestEntity implements Serializable, BasicStudyEntity {

    @PrimaryKeyColumn(name = "userId", type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "studyName", type = PrimaryKeyType.CLUSTERED)
    private String studyName;

    @Column("creationDate")
    private LocalDateTime date;
}
