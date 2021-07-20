/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import static org.gridsuite.study.server.StudyException.Type.*;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler {

    @ExceptionHandler(value = {StudyException.class})
    protected ResponseEntity<Object> handleException(RuntimeException exception) {
        StudyException studyException = (StudyException) exception;
        switch (studyException.getType()) {
            case ELEMENT_NOT_FOUND:
            case STUDY_NOT_FOUND:
            case SECURITY_ANALYSIS_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(studyException.getType());
            case CASE_NOT_FOUND:
                return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY).body(CASE_NOT_FOUND);
            case STUDY_ALREADY_EXISTS:
                return ResponseEntity.status(HttpStatus.CONFLICT).body(STUDY_ALREADY_EXISTS);
            case LOADFLOW_NOT_RUNNABLE:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(LOADFLOW_NOT_RUNNABLE);
            case LOADFLOW_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(LOADFLOW_RUNNING);
            case SECURITY_ANALYSIS_RUNNING:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(SECURITY_ANALYSIS_RUNNING);
            case NOT_ALLOWED:
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(NOT_ALLOWED);
            case LINE_MODIFICATION_FAILED:
            case DIRECTORY_REQUEST_FAILED:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(studyException.getMessage());
            default:
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
