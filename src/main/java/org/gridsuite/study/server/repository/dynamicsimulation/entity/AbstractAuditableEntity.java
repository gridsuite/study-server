/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.dynamicsimulation.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.repository.AbstractManuallyAssignedIdentifierEntity;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import java.util.Date;

import static javax.persistence.TemporalType.TIMESTAMP;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Data
@Setter
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractAuditableEntity<ID> extends AbstractManuallyAssignedIdentifierEntity<ID> {
    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @CreatedDate
    @Temporal(TIMESTAMP)
    @Column(name = "created_date", updatable = false)
    private Date createdDate;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @LastModifiedDate
    @Temporal(TIMESTAMP)
    @Column(name = "updated_date")
    private Date updatedDate;
}
