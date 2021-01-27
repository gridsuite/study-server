/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.dto.LoadFlowStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

@Getter
@Setter
@AllArgsConstructor
@Table("study")
public class StudyEntity implements BasicStudyEntity, Serializable {

    @Id
    private String userId;
    @Id
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
    private LoadFlowStatus loadFlowStatus;

    //Foreign key (One to One relation)
    @Column("loadFlowResultUuid")
    private UUID loadFlowResultUuid;

    //Foreign key (One to One relation)
    @Column("loadFlowParametersUuid")
    private UUID loadFlowParametersUuid;

    @Column("securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;
}
