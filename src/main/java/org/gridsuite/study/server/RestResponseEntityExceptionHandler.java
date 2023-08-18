/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

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

    @ExceptionHandler(StudyException.class)
    protected ResponseEntity<Object> handleStudyException(StudyException exception) {
        StudyException.Type type = exception.getType();
        switch (type) {
            case ELEMENT_NOT_FOUND:
            case STUDY_NOT_FOUND:
            case NODE_NOT_FOUND:
            case SECURITY_ANALYSIS_NOT_FOUND:
            case SENSITIVITY_ANALYSIS_NOT_FOUND:
            case DYNAMIC_SIMULATION_NOT_FOUND:
            case DYNAMIC_MAPPING_NOT_FOUND:
            case EQUIPMENT_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getType());
            case CASE_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(exception.getMessage());
            case STUDY_ALREADY_EXISTS:
                return ResponseEntity.status(HttpStatus.CONFLICT).body(type);
            case LOADFLOW_NOT_RUNNABLE:
            case LOADFLOW_RUNNING:
            case SECURITY_ANALYSIS_RUNNING:
            case SENSITIVITY_ANALYSIS_RUNNING:
            case DYNAMIC_SIMULATION_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(type);
            case NOT_ALLOWED:
            case BAD_NODE_TYPE:
            case NODE_NAME_ALREADY_EXIST:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
            case CANT_DELETE_ROOT_NODE:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_ALLOWED);
            case CREATE_NETWORK_MODIFICATION_FAILED:
            case UPDATE_NETWORK_MODIFICATION_FAILED:
            case DELETE_NETWORK_MODIFICATION_FAILED:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
            case NETWORK_NOT_FOUND:
            case NETWORK_INDEXATION_FAILED:
            case NODE_NOT_BUILT:
            case DELETE_EQUIPMENT_FAILED:
            case DELETE_NODE_FAILED:
            case DELETE_STUDY_FAILED:
            case GET_MODIFICATIONS_FAILED:
            case GET_NETWORK_ELEMENT_FAILED:
            case SENSITIVITY_ANALYSIS_ERROR:
            case NODE_BUILD_ERROR:
            case URI_SYNTAX:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
            case SVG_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
            case BAD_MODIFICATION_TYPE:
            case BAD_JSON_FORMAT:
            case TIME_SERIES_BAD_TYPE:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
            case UNKNOWN_NOTIFICATION_TYPE:
            case UNKNOWN_ACTION_TYPE:
            case MISSING_PARAMETER:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getType());
            case LOADFLOW_ERROR:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(ServerWebInputException.class)
    protected ResponseEntity<Object> handleServerWebInputException(ServerWebInputException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof TypeMismatchException && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return ResponseEntity.status(exception.getStatusCode()).body(cause.getMessage());
    }

    @ExceptionHandler(TypeMismatchException.class)
    protected ResponseEntity<Object> handleTypeMismatchException(TypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getCause().getMessage());
    }
}
