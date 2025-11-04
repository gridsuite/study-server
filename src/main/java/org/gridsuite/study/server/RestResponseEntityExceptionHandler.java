/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;

import static org.gridsuite.study.server.StudyException.Type.NOT_ALLOWED;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestResponseEntityExceptionHandler.class);
    private static final String MESSAGE = "Caught in handler";

//    @ExceptionHandler(StudyException.class)
//    protected ResponseEntity<Object> handleStudyException(StudyException exception) {
//        if (LOGGER.isErrorEnabled()) {
//            LOGGER.error(MESSAGE, exception);
//        }
//        StudyException.Type type = exception.getType();
//        return switch (type) {
//            case ELEMENT_NOT_FOUND,
//                    STUDY_NOT_FOUND,
//                    NODE_NOT_FOUND,
//                    ROOT_NETWORK_NOT_FOUND,
//                    LOADFLOW_NOT_FOUND,
//                    SECURITY_ANALYSIS_NOT_FOUND,
//                    SENSITIVITY_ANALYSIS_NOT_FOUND,
//                    SHORT_CIRCUIT_ANALYSIS_NOT_FOUND,
//                    DYNAMIC_SIMULATION_NOT_FOUND,
//                    DYNAMIC_MAPPING_NOT_FOUND,
//                    VOLTAGE_INIT_PARAMETERS_NOT_FOUND,
//                    STATE_ESTIMATION_NOT_FOUND,
//                    PCC_MIN_NOT_FOUND,
//                    DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_NOT_FOUND,
//                    DYNAMIC_SECURITY_ANALYSIS_PROVIDER_NOT_FOUND,
//                    DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND,
//                    DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND
//                    -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getType());
//            case CASE_NOT_FOUND -> ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(exception.getMessage());
//            case LOADFLOW_RUNNING,
//                    SECURITY_ANALYSIS_RUNNING,
//                    SENSITIVITY_ANALYSIS_RUNNING,
//                    DYNAMIC_SIMULATION_RUNNING,
//                    SHORT_CIRCUIT_ANALYSIS_RUNNING,
//                    VOLTAGE_INIT_RUNNING,
//                    STATE_ESTIMATION_RUNNING,
//                    PCC_MIN_RUNNING
//                    -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(type);
//            case NOT_ALLOWED,
//                    BAD_NODE_TYPE,
//                    NODE_NAME_ALREADY_EXIST,
//                    ROOT_NETWORK_DELETE_FORBIDDEN,
//                    MAXIMUM_ROOT_NETWORK_BY_STUDY_REACHED,
//                    MAXIMUM_TAG_LENGTH_EXCEEDED,
//                    TOO_MANY_NAD_CONFIGS,
//                    TOO_MANY_MAP_CARDS,
//                    MOVE_NETWORK_MODIFICATION_FORBIDDEN
//                    -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
//            case CANT_DELETE_ROOT_NODE -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_ALLOWED);
//            case CREATE_NETWORK_MODIFICATION_FAILED,
//                    TIME_SERIES_BAD_TYPE,
//                    TIMELINE_BAD_TYPE,
//                    -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
//            case NETWORK_NOT_FOUND,
//                    NODE_NOT_BUILT,
//                    GET_MODIFICATIONS_FAILED,
//                    NODE_BUILD_ERROR, URI_SYNTAX,
//                    LOADFLOW_ERROR,
//                    GET_DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_FAILED,
//                    GET_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED,
//                    UPDATE_DYNAMIC_SECURITY_ANALYSIS_PROVIDER_FAILED,
//                    GET_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED,
//                    CREATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED,
//                    CREATE_DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PARAMETERS_FAILED,
//                    RUN_DYNAMIC_SECURITY_ANALYSIS_FAILED,
//                    INVALIDATE_DYNAMIC_SECURITY_ANALYSIS_FAILED,
//                    UPDATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED,
//                    DUPLICATE_DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_FAILED,
//                    -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
//            case SVG_NOT_FOUND,
//                    NO_VOLTAGE_INIT_RESULTS_FOR_NODE,
//                    NO_VOLTAGE_INIT_MODIFICATIONS_GROUP_FOR_NODE
//                    -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
//            case UNKNOWN_NOTIFICATION_TYPE,
//                    UNKNOWN_ACTION_TYPE,
//                    MISSING_PARAMETER
//                    -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getType());
//            case MAX_NODE_BUILDS_EXCEEDED -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(StudyException.Type.MAX_NODE_BUILDS_EXCEEDED + " " + exception.getMessage());
//            case DIAGRAM_GRID_LAYOUT_NOT_FOUND -> ResponseEntity.noContent().build();
//            default -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
//        };
//    }

    @ExceptionHandler(ServerWebInputException.class)
    protected ResponseEntity<Object> handleServerWebInputException(ServerWebInputException exception) {
        if (LOGGER.isErrorEnabled()) {
            LOGGER.error(MESSAGE, exception);
        }
        Throwable cause = exception.getCause();
        if (cause instanceof TypeMismatchException && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return ResponseEntity.status(exception.getStatusCode()).body(cause.getMessage());
    }

    @ExceptionHandler(TypeMismatchException.class)
    protected ResponseEntity<Object> handleTypeMismatchException(TypeMismatchException exception) {
        if (LOGGER.isErrorEnabled()) {
            LOGGER.error(MESSAGE, exception);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getCause().getMessage());
    }
}
