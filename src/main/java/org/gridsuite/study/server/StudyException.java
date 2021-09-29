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
public class StudyException  extends RuntimeException {

    public enum Type {
        STUDY_ALREADY_EXISTS,
        ELEMENT_NOT_FOUND,
        STUDY_NOT_FOUND,
        CASE_NOT_FOUND,
        LOADFLOW_NOT_RUNNABLE,
        LOADFLOW_RUNNING,
        SECURITY_ANALYSIS_RUNNING,
        SECURITY_ANALYSIS_NOT_FOUND,
        NOT_ALLOWED,
        STUDY_CREATION_FAILED,
        LINE_MODIFICATION_FAILED,
        LOAD_CREATION_FAILED,
        CANT_DELETE_ROOT_NODE
    }

    private final Type type;

    public StudyException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    StudyException(Type type, String message) {
        super(message);
        this.type = type;
    }

    Type getType() {
        return type;
    }
}
