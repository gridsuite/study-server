package com.powsybl.study.server;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static com.powsybl.study.server.StudyConstants.STUDY_DOESNT_EXISTS;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(value = {StudyException.class})
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        String errorMessage = ex.getMessage();
        if (errorMessage.equals(STUDY_DOESNT_EXISTS)) {
            return handleExceptionInternal(ex, STUDY_DOESNT_EXISTS, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
        } else {
            return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
    }
}
