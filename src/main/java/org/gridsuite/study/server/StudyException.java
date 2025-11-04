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
        ELEMENT_NOT_FOUND,
        STUDY_NOT_FOUND,
        DIAGRAM_GRID_LAYOUT_NOT_FOUND,
        CASE_NOT_FOUND,
        LOADFLOW_RUNNING,
        LOADFLOW_ERROR,
        SECURITY_ANALYSIS_RUNNING,
        SECURITY_ANALYSIS_NOT_FOUND,
        SENSITIVITY_ANALYSIS_RUNNING,
        SENSITIVITY_ANALYSIS_NOT_FOUND,
        SHORT_CIRCUIT_ANALYSIS_NOT_FOUND,
        LOADFLOW_NOT_FOUND,
        SHORT_CIRCUIT_ANALYSIS_RUNNING,
        VOLTAGE_INIT_RUNNING,
        DYNAMIC_SIMULATION_RUNNING,
        DYNAMIC_SECURITY_ANALYSIS_RUNNING,
        NOT_ALLOWED,
        CANT_DELETE_ROOT_NODE,
        CREATE_NETWORK_MODIFICATION_FAILED,
        MOVE_NETWORK_MODIFICATION_FORBIDDEN,
        BAD_NODE_TYPE,
        NETWORK_NOT_FOUND,
        NODE_NOT_FOUND,
        NODE_NOT_BUILT,
        NODE_NAME_ALREADY_EXIST,
        UNKNOWN_NOTIFICATION_TYPE,
        GET_MODIFICATIONS_FAILED,
        UNKNOWN_ACTION_TYPE,
        MISSING_PARAMETER,
        URI_SYNTAX,
        TIME_SERIES_BAD_TYPE,
        TIMELINE_BAD_TYPE,
        NO_VOLTAGE_INIT_RESULTS_FOR_NODE,
        RUN_DYNAMIC_SIMULATION_FAILED,
        DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND,
        STATE_ESTIMATION_RUNNING,
        PCC_MIN_RUNNING,
        MAX_NODE_BUILDS_EXCEEDED,
        ROOT_NETWORK_NOT_FOUND,
        ROOT_NETWORK_DELETE_FORBIDDEN,
        MAXIMUM_ROOT_NETWORK_BY_STUDY_REACHED,
        MAXIMUM_TAG_LENGTH_EXCEEDED,
        NETWORK_EXPORT_FAILED,
        TOO_MANY_NAD_CONFIGS,
        TOO_MANY_MAP_CARDS
    }

    private final Type type;

    public StudyException(Type type) {
        super(Objects.requireNonNull(type.name()));
        this.type = type;
    }

    public StudyException(Type type, String message) {
        super(message);
        this.type = type;
    }

    Type getType() {
        return type;
    }
}
