/**
 * Copyright (c) 2020, RTE (http://www.rte-france.com) This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import com.powsybl.ws.commons.error.AbstractBaseRestExceptionHandler;
import com.powsybl.ws.commons.error.ServerNameProvider;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */
@ControllerAdvice
public class RestResponseEntityExceptionHandler extends AbstractBaseRestExceptionHandler<StudyException, StudyBusinessErrorCode> {

    protected RestResponseEntityExceptionHandler(ServerNameProvider serverNameProvider) {
        super(serverNameProvider);
    }

    @Override
    protected @NonNull StudyBusinessErrorCode getBusinessCode(StudyException e) {
        return e.getBusinessErrorCode();
    }

    @Override
    protected HttpStatus mapStatus(StudyBusinessErrorCode studyBusinessErrorCode) {
        return switch (studyBusinessErrorCode) {
            case ELEMENT_NOT_FOUND,
                 STUDY_NOT_FOUND,
                 NODE_NOT_FOUND,
                 ROOT_NETWORK_NOT_FOUND,
                 LOADFLOW_NOT_FOUND,
                 SECURITY_ANALYSIS_NOT_FOUND,
                 SENSITIVITY_ANALYSIS_NOT_FOUND,
                 SHORT_CIRCUIT_ANALYSIS_NOT_FOUND,
                 DYNAMIC_SIMULATION_NOT_FOUND,
                 DYNAMIC_MAPPING_NOT_FOUND,
                 EQUIPMENT_NOT_FOUND,
                 VOLTAGE_INIT_PARAMETERS_NOT_FOUND,
                 SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND,
                 LOADFLOW_PARAMETERS_NOT_FOUND,
                 SENSITIVITY_ANALYSIS_PARAMETERS_NOT_FOUND,
                 STATE_ESTIMATION_NOT_FOUND,
                 PCC_MIN_NOT_FOUND,
                 STATE_ESTIMATION_PARAMETERS_NOT_FOUND,
                 DYNAMIC_SECURITY_ANALYSIS_DEFAULT_PROVIDER_NOT_FOUND,
                 DYNAMIC_SECURITY_ANALYSIS_PROVIDER_NOT_FOUND,
                 DYNAMIC_SECURITY_ANALYSIS_PARAMETERS_NOT_FOUND,
                 DYNAMIC_SECURITY_ANALYSIS_NOT_FOUND,
                 SVG_NOT_FOUND,
                 NO_VOLTAGE_INIT_RESULTS_FOR_NODE,
                 NO_VOLTAGE_INIT_MODIFICATIONS_GROUP_FOR_NODE
                -> HttpStatus.NOT_FOUND;
            case CASE_NOT_FOUND -> HttpStatus.FAILED_DEPENDENCY;
            case STUDY_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case LOADFLOW_NOT_RUNNABLE,
                 LOADFLOW_RUNNING,
                 SECURITY_ANALYSIS_RUNNING,
                 SENSITIVITY_ANALYSIS_RUNNING,
                 DYNAMIC_SIMULATION_RUNNING,
                 SHORT_CIRCUIT_ANALYSIS_RUNNING,
                 VOLTAGE_INIT_RUNNING,
                 STATE_ESTIMATION_RUNNING,
                 PCC_MIN_RUNNING,
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
            case CREATE_NETWORK_MODIFICATION_FAILED,
                 UPDATE_NETWORK_MODIFICATION_FAILED,
                 DELETE_NETWORK_MODIFICATION_FAILED,
                 BAD_MODIFICATION_TYPE,
                 BAD_JSON_FORMAT,
                 TIME_SERIES_BAD_TYPE,
                 TIMELINE_BAD_TYPE,
                 BAD_PARAMETER,
                 UNKNOWN_NOTIFICATION_TYPE,
                 UNKNOWN_ACTION_TYPE,
                 MISSING_PARAMETER
                -> HttpStatus.BAD_REQUEST;
            case NOT_IMPLEMENTED -> HttpStatus.NOT_IMPLEMENTED;
            case DIAGRAM_GRID_LAYOUT_NOT_FOUND -> HttpStatus.NO_CONTENT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
