/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.entities;

import java.time.ZonedDateTime;

import lombok.*;

import javax.persistence.*;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 * @author Chamseddine Benhamed <chamseddine.benhamed at rte-france.com>
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "studycreationrequest")
public class StudyCreationRequestEntity {

    public StudyCreationRequestEntity(String userId, String studyName, ZonedDateTime date) {
        this.userId = userId;
        this.studyName = studyName;
        this.date = date;
    }

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "userId", nullable = false)
    private String userId;

    @Column(name = "studyName", nullable = false)
    private String studyName;

    @Column(name = "creationDate")
    private ZonedDateTime date;
}
