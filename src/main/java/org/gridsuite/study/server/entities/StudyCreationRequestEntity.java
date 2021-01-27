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
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * @author Slimane Amar <slimane.amar at rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Table("studycreationrequest")
public class StudyCreationRequestEntity implements BasicStudyEntity, Serializable, Persistable<UUID> {
    @Id
    @Column("id")
    private UUID id;

    @Column("userId")
    private String userId;

    @Column("studyName")
    private String studyName;

    @Column("creationDate")
    private LocalDateTime date;

    @Transient
    private boolean newElement;

    @Override
    @Transient
    public boolean isNew() {
        if (newElement && id == null) {
            id = UUID.randomUUID();
        }
        return newElement;
    }
}
