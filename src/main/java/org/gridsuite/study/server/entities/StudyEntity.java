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

import lombok.*;
import org.gridsuite.study.server.dto.LoadFlowStatus;

import javax.persistence.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "study")
public class StudyEntity implements BasicStudyEntity, Serializable {
    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private long id;

    @Column(name = "userId")
    private String userId;

    @Column(name = "studyName")
    private String studyName;

    @Column(name = "creationDate")
    private LocalDateTime date;

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
