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
        LOADFLOW_ERROR,
        SECURITY_ANALYSIS_RUNNING,
        SECURITY_ANALYSIS_NOT_FOUND,
        SENSITIVITY_ANALYSIS_RUNNING,
        SENSITIVITY_ANALYSIS_NOT_FOUND,
        SENSITIVITY_ANALYSIS_ERROR,
        SHORT_CIRCUIT_ANALYSIS_NOT_FOUND,
        LOADFLOW_NOT_FOUND,
        SHORT_CIRCUIT_ANALYSIS_RUNNING,
        VOLTAGE_INIT_NOT_FOUND,
        VOLTAGE_INIT_RUNNING,
        DYNAMIC_SIMULATION_RUNNING,
        DYNAMIC_SIMULATION_NOT_FOUND,
        DYNAMIC_MAPPING_NOT_FOUND,
        NOT_ALLOWED,
        STUDY_CREATION_FAILED,
        CANT_DELETE_ROOT_NODE,
        DELETE_EQUIPMENT_FAILED,
        DELETE_NODE_FAILED,
        DELETE_STUDY_FAILED,
        CREATE_NETWORK_MODIFICATION_FAILED,
        UPDATE_NETWORK_MODIFICATION_FAILED,
        DELETE_NETWORK_MODIFICATION_FAILED,
        UNKNOWN_EQUIPMENT_TYPE,
        BAD_NODE_TYPE,
        NETWORK_NOT_FOUND,
        EQUIPMENT_NOT_FOUND,
        NETWORK_INDEXATION_FAILED,
        NODE_NOT_FOUND,
        NODE_NOT_BUILT,
        SVG_NOT_FOUND,
        NODE_NAME_ALREADY_EXIST,
        NODE_BUILD_ERROR,
        INVALIDATE_BUILD_FAILED,
        UNKNOWN_NOTIFICATION_TYPE,
        BAD_MODIFICATION_TYPE,
        GET_MODIFICATIONS_FAILED,
        GET_MODIFICATIONS_COUNT_FAILED,
        GET_NETWORK_ELEMENT_FAILED,
        GET_NETWORK_COUNTRY_FAILED,
        BAD_JSON_FORMAT,
        UNKNOWN_ACTION_TYPE,
        MISSING_PARAMETER,
        LOAD_SCALING_FAILED,
        DELETE_VOLTAGE_LEVEL_ON_LINE,
        DELETE_ATTACHING_LINE,
        GENERATOR_SCALING_FAILED,
        URI_SYNTAX,
        TIME_SERIES_BAD_TYPE,
        FILTERS_NOT_FOUND,
        NO_VOLTAGE_INIT_RESULTS_FOR_NODE,
        NO_VOLTAGE_INIT_MODIFICATIONS_GROUP_FOR_NODE,
        VOLTAGE_INIT_PARAMETERS_NOT_FOUND,
        CREATE_VOLTAGE_INIT_PARAMETERS_FAILED,
        UPDATE_VOLTAGE_INIT_PARAMETERS_FAILED,
        DELETE_COMPUTATION_RESULTS_FAILED,
        STUDY_INDEXATION_FAILED,
        STUDY_CHECK_INDEXATION_FAILED,
        NOT_IMPLEMENTED,
        EVALUATE_FILTER_FAILED,
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
