/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.time.LocalDateTime;
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
@Table(name = "study", indexes = {@Index(name = "studyEntity_isPrivate_index", columnList = "isPrivate"), @Index(name = "studyEntity_userId_index", columnList = "userId")})
public class StudyEntity extends AbstractManuallyAssignedIdentifierEntity<UUID> implements BasicStudyEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "studyName", nullable = false)
    private String studyName;

    @Column(name = "creationDate",  nullable = false)
    private LocalDateTime date;

    @Column(name = "networkUuid", nullable = false)
    private UUID networkUuid;

    @Column(name = "networkId", nullable = false)
    private String networkId;

    @Column(name = "description",  nullable = false)
    private String description;

    @Column(name = "caseFormat",  nullable = false)
    private String caseFormat;

    @Column(name = "caseUuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "casePrivate",  nullable = false)
    private boolean casePrivate;

    @Column(name = "isPrivate", nullable = false)
    private boolean isPrivate;

    @Column(name = "loadFlowStatus")
    @Enumerated(EnumType.STRING)
    private LoadFlowStatus loadFlowStatus;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "loadFlowResultEntity_id",
            referencedColumnName  =  "id",
            foreignKey = @ForeignKey(
                    name = "loadFlowResult_id_fk"
            ))
    private LoadFlowResultEntity loadFlowResult;

    @Column(name = "loadFlowProvider")
    private String loadFlowProvider;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name  =  "loadFlowParametersEntity_id",
            referencedColumnName  =  "id",
            foreignKey = @ForeignKey(
                    name = "loadFlowParameters_id_fk"
            ), nullable = false)
    private LoadFlowParametersEntity loadFlowParameters;

    @Column(name = "securityAnalysisResultUuid")
    private UUID securityAnalysisResultUuid;

    @Value
    public static class StudyNetworkUuid {
        UUID networkUuid;
    }
}

