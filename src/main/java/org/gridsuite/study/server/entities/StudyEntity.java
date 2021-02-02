/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.entities;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.UUID;

import lombok.*;
import org.gridsuite.study.server.dto.LoadFlowStatus;

import javax.persistence.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Builder
@Table(name = "study")
public class StudyEntity implements BasicStudyEntity, Serializable {

    public StudyEntity(String userId, String studyName, ZonedDateTime date, UUID networkUuid, String networkId,
                       String description, String caseFormat, UUID caseUuid, boolean casePrivate, boolean isPrivate,
                       LoadFlowStatus loadFlowStatus, LoadFlowResultEntity loadFlowResult, LoadFlowParametersEntity
                               loadFlowParameters, UUID securityAnalysisResultUuid) {
        this.userId = userId;
        this.studyName = studyName;
        this.date = date;
        this.networkUuid = networkUuid;
        this.networkId = networkId;
        this.description = description;
        this.caseFormat = caseFormat;
        this.caseUuid = caseUuid;
        this.casePrivate = casePrivate;
        this.isPrivate = isPrivate;
        this.loadFlowStatus = loadFlowStatus;
        this.loadFlowResult = loadFlowResult;
        this.loadFlowParameters = loadFlowParameters;
        this.securityAnalysisResultUuid = securityAnalysisResultUuid;
    }

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private long id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "studyName", nullable = false)
    private String studyName;

    @Column(name = "creationDate")
    private ZonedDateTime date;

    @Column(name = "networkUuid")
    private UUID networkUuid;

    @Column(name = "networkId")
    private String networkId;

    @Column(name = "description")
    private String description;

    @Column(name = "caseFormat")
    private String caseFormat;

    @Column(name = "caseUuid")
    private UUID caseUuid;

    @Column(name = "casePrivate")
    private boolean casePrivate;

    @Column(name = "isPrivate")
    private boolean isPrivate;

    @Column(name = "loadFlowStatus")
    @Enumerated(EnumType.STRING)
    private LoadFlowStatus loadFlowStatus;

    @OneToOne(cascade  =  CascadeType.ALL)
    @JoinColumn(name  =  "loadFlowResultEntity_id", referencedColumnName  =  "id")
    private LoadFlowResultEntity loadFlowResult;

    @OneToOne(cascade  =  CascadeType.ALL)
    @JoinColumn(name  =  "loadFlowParameters_id", referencedColumnName  =  "id")
    private LoadFlowParametersEntity loadFlowParameters;

    @Column(name = "securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;
}
