/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com
 */
@Getter
@Setter
@PrimaryKeyClass
public class StudyKey implements Serializable {

    @PrimaryKeyColumn(name = "studyName", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String name;

    @PrimaryKeyColumn(name = "creationDate", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private LocalDateTime date;

    public StudyKey(String name, LocalDateTime date) {
        this.name = name;
        this.date = date;
    }
}
