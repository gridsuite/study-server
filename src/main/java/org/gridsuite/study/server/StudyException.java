/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import java.util.Objects;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
public class StudyException extends RuntimeException {

    private final StudyBusinessErrorCode type;

    public StudyException(StudyBusinessErrorCode type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public StudyException(StudyBusinessErrorCode type, String message) {
        super(message);
        this.type = type;
    }

    StudyBusinessErrorCode getType() {
        return type;
    }
}
