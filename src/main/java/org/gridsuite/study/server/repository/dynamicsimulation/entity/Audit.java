/*
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.gridsuite.study.server.repository.dynamicsimulation.entity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.gridsuite.study.server.StudyConstants;

import jakarta.persistence.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.Optional;

import static jakarta.persistence.TemporalType.TIMESTAMP;

/**
 * @author Thang PHAM <quyet-thang.pham at rte-france.com>
 */
@Data
@Setter
@Getter
@Embeddable
public class Audit {

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Temporal(TIMESTAMP)
    @Column(name = "created_date", updatable = false)
    private Date createdDate;

    @Column(name = "updated_by")
    private String updatedBy;

    @Temporal(TIMESTAMP)
    @Column(name = "updated_date")
    private Date updatedDate;

    @PrePersist
    public void onCreate() {
        createdDate = new Date();
        getCurrentAuditor().ifPresent(userId -> createdBy = userId);
    }

    @PreUpdate
    public void onUpdate() {
        updatedDate = new Date();
        getCurrentAuditor().ifPresent(userId -> updatedBy = userId);
    }

    private Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(getRequest())
                .map(request -> request.getHeader(StudyConstants.HEADER_USER_ID));
    }

    private HttpServletRequest getRequest() {
        RequestAttributes attribs = RequestContextHolder.getRequestAttributes();

        if (attribs instanceof ServletRequestAttributes servletAttribs) {
            return servletAttribs.getRequest();
        }

        return null;
    }
}
