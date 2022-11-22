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

import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler(StudyException.class)
    protected ResponseEntity<Object> handleStudyException(StudyException exception) {
        switch (exception.getType()) {
            case ELEMENT_NOT_FOUND:
            case STUDY_NOT_FOUND:
            case NODE_NOT_FOUND:
            case SECURITY_ANALYSIS_NOT_FOUND:
            case SENSITIVITY_ANALYSIS_NOT_FOUND:
            case EQUIPMENT_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getType());
            case CASE_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(exception.getMessage());
            case STUDY_ALREADY_EXISTS:
                return ResponseEntity.status(HttpStatus.CONFLICT).body(STUDY_ALREADY_EXISTS);
            case LOADFLOW_NOT_RUNNABLE:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(LOADFLOW_NOT_RUNNABLE);
            case LOADFLOW_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(LOADFLOW_RUNNING);
            case SECURITY_ANALYSIS_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SECURITY_ANALYSIS_RUNNING);
            case SENSITIVITY_ANALYSIS_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SENSITIVITY_ANALYSIS_RUNNING);
            case NOT_ALLOWED:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
            case CANT_DELETE_ROOT_NODE:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_ALLOWED);
            case LINE_MODIFICATION_FAILED:
            case LOAD_CREATION_FAILED:
            case GENERATOR_CREATION_FAILED:
            case SHUNT_COMPENSATOR_CREATION_FAILED:
            case LINE_CREATION_FAILED:
            case TWO_WINDINGS_TRANSFORMER_CREATION_FAILED:
            case SUBSTATION_CREATION_FAILED:
            case VOLTAGE_LEVEL_CREATION_FAILED:
            case LINE_SPLIT_FAILED:
            case NETWORK_NOT_FOUND:
            case NETWORK_INDEXATION_FAILED:
            case NODE_NOT_BUILT:
            case DELETE_EQUIPMENT_FAILED:
            case DELETE_NODE_FAILED:
            case DELETE_STUDY_FAILED:
            case DELETE_MODIFICATIONS_FAILED:
            case GET_MODIFICATIONS_FAILED:
            case NODE_BUILD_ERROR:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
            case BAD_NODE_TYPE:
            case NODE_NAME_ALREADY_EXIST:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(exception.getMessage());
            case SVG_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
            case UNKNOWN_NOTIFICATION_TYPE:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(UNKNOWN_NOTIFICATION_TYPE);
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ExceptionHandler(ServerWebInputException.class)
    protected ResponseEntity<Object> handleStudyException(ServerWebInputException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof TypeMismatchException && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return ResponseEntity.status(exception.getStatus()).body(cause.getMessage());
    }

    @ExceptionHandler(TypeMismatchException.class)
    protected ResponseEntity<Object> handleStudyException(TypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getCause().getMessage());
    }
}
