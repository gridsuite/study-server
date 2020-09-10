/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.repository;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

/**
 * @author Abdelsalem HEDHILI <abdelsalem.hedhili at rte-france.com>
 */
@Getter
@AllArgsConstructor
@UserDefinedType("userId")
public class UserId implements Serializable {

    private String subject;

    private String issuer;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        UserId userId = (UserId) o;
        return this.subject.equals(userId.subject)
                && this.issuer.equals(userId.issuer);
    }

    @Override
    public int hashCode() {
        return this.subject.hashCode() + this.issuer.hashCode();
    }
}
