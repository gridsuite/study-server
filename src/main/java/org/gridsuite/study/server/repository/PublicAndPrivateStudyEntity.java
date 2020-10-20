/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import com.datastax.driver.core.DataType;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table("study")
public class PublicAndPrivateStudyEntity implements Serializable, StudyEntity {

    @PrimaryKeyColumn(name = "userId", type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "studyName", type = PrimaryKeyType.CLUSTERED)
    private String studyName;

    @Column("creationDate")
    private LocalDateTime date;

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

    @Column("isPrivate")
    private boolean isPrivate;

    @Column("loadFlowStatus")
    @CassandraType(type = DataType.Name.UDT, userTypeName = "loadFlowStatus")
    private LoadFlowStatusEntity loadFlowStatus;

    @Column("loadFlowResult")
    @CassandraType(type = DataType.Name.UDT, userTypeName = "loadFlowResult")
    private LoadFlowResultEntity loadFlowResult;

    @Column("loadFlowParameters")
    private LoadFlowParametersEntity loadFlowParameters;

    @Column("securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;
}
