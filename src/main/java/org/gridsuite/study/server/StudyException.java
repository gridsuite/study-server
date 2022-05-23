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
        LOAD_MODIFICATION_FAILED,
        CANT_DELETE_ROOT_NODE,
        DELETE_EQUIPMENT_FAILED,
        GENERATOR_CREATION_FAILED,
        SHUNT_COMPENSATOR_CREATION_FAILED,
        LINE_CREATION_FAILED,
        TWO_WINDINGS_TRANSFORMER_CREATION_FAILED,
        SUBSTATION_CREATION_FAILED,
        VOLTAGE_LEVEL_CREATION_FAILED,
        LINE_SPLIT_FAILED,
        UNKNOWN_EQUIPMENT_TYPE,
        BAD_NODE_TYPE,
        NETWORK_NOT_FOUND,
        EQUIPMENT_NOT_FOUND,
        NETWORK_INDEXATION_FAILED,
        NODE_NOT_BUILT,
        GENERATOR_MODIFICATION_FAILED,
    }

    private final Type type;

    public static StudyException createEquipmentTypeUnknown(String type) {
        Objects.requireNonNull(type);
        return new StudyException(Type.UNKNOWN_EQUIPMENT_TYPE, "The equipment type : " + type + " is unknown");
    }

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
