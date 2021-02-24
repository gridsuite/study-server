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
@Table(name = "studycreationrequest")
public class StudyCreationRequestEntity implements BasicStudyEntity {

    @Id
    @GeneratedValue(strategy  =  GenerationType.AUTO)
    @Column(name = "id")
    private UUID id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "studyName", nullable = false)
    private String studyName;

    @Column(name = "creationDate")
    private LocalDateTime date;

    @Column(name = "isPrivate")
    private Boolean isPrivate;
}
