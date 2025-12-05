/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import com.powsybl.ws.commons.error.AbstractBusinessExceptionHandler;
import com.powsybl.ws.commons.error.PowsyblWsProblemDetail;
import com.powsybl.ws.commons.error.ServerNameProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class StudyExceptionHandler extends AbstractBusinessExceptionHandler<StudyException, StudyBusinessErrorCode> {

    protected StudyExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull StudyBusinessErrorCode getBusinessCode(StudyException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(StudyBusinessErrorCode studyBusinessErrorCode) {
        return switch (studyBusinessErrorCode) {
            case NOT_FOUND,
                 NO_VOLTAGE_INIT_RESULTS_FOR_NODE
                -> HttpStatus.NOT_FOUND;
            case COMPUTATION_RUNNING,
                 NOT_ALLOWED,
                 BAD_NODE_TYPE,
                 NODE_NAME_ALREADY_EXIST,
                 ROOT_NETWORK_DELETE_FORBIDDEN,
                 MAXIMUM_ROOT_NETWORK_BY_STUDY_REACHED,
                 MAXIMUM_TAG_LENGTH_EXCEEDED,
                 TOO_MANY_NAD_CONFIGS,
                 TOO_MANY_MAP_CARDS,
                 MOVE_NETWORK_MODIFICATION_FORBIDDEN,
                 CANT_DELETE_ROOT_NODE,
                 MAX_NODE_BUILDS_EXCEEDED
                -> HttpStatus.FORBIDDEN;
            case TIME_SERIES_BAD_TYPE -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(StudyException.class)
    protected ResponseEntity<PowsyblWsProblemDetail> handleStudyException(
        StudyException exception, HttpServletRequest request) {
        return super.handleDomainException(exception, request);
    }
}
