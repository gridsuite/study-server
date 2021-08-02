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

import javax.persistence.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "studycreationrequest", indexes = {@Index(name = "studyCreationRequest_isPrivate_index", columnList = "isPrivate"), @Index(name = "studyCreationRequest_userId_index", columnList = "userId")})
public class StudyCreationRequestEntity implements BasicStudyEntity {

    public StudyCreationRequestEntity(String userId, String studyName, LocalDateTime date, boolean isPrivate) {
        this(null, userId, studyName, date, isPrivate);
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "studyName", nullable = false)
    private String studyName;

    @Column(name = "creationDate", nullable = false)
    private LocalDateTime date;

    @Column(name = "isPrivate", nullable = false)
    private Boolean isPrivate;
}
