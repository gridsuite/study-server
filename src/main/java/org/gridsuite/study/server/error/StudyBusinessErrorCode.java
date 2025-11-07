/**
 * Copyright (c) 2025, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.error;

import com.powsybl.ws.commons.error.BusinessErrorCode;

/**
 * @author Joris Mancini <joris.mancini_externe at rte-france.com>
 */
public enum StudyBusinessErrorCode implements BusinessErrorCode {
    ELEMENT_NOT_FOUND("study.elementNotFound"),
    STUDY_NOT_FOUND("study.studyNotFound"),
    CASE_NOT_FOUND("study.caseNotFound"),
    COMPUTATION_RUNNING("study.computationRunning"),
    LOADFLOW_ERROR("study.loadflowError"),
    SECURITY_ANALYSIS_NOT_FOUND("study.securityAnalysisNotFound"),
    SENSITIVITY_ANALYSIS_NOT_FOUND("study.sensitivityAnalysisNotFound"),
    SHORT_CIRCUIT_ANALYSIS_NOT_FOUND("study.shortCircuitAnalysisNotFound"),
    LOADFLOW_NOT_FOUND("study.loadflowNotFound"),
    NOT_ALLOWED("study.notAllowed"),
    CANT_DELETE_ROOT_NODE("study.cantDeleteRootNode"),
    MOVE_NETWORK_MODIFICATION_FORBIDDEN("study.moveNetworkModificationForbidden"),
    BAD_NODE_TYPE("study.badNodeType"),
    NODE_NOT_FOUND("study.nodeNotFound"),
    NODE_NOT_BUILT("study.nodeNotBuilt"),
    NODE_NAME_ALREADY_EXIST("study.nodeNameAlreadyExist"),
    TIME_SERIES_BAD_TYPE("study.timeSeriesBadType"),
    NO_VOLTAGE_INIT_RESULTS_FOR_NODE("study.noVoltageInitResultsForNode"),
    MAX_NODE_BUILDS_EXCEEDED("study.maxNodeBuildsExceeded"),
    ROOT_NETWORK_NOT_FOUND("study.rootNetworkNotFound"),
    ROOT_NETWORK_DELETE_FORBIDDEN("study.rootNetworkDeleteForbidden"),
    MAXIMUM_ROOT_NETWORK_BY_STUDY_REACHED("study.maximumRootNetworkByStudyReached"),
    MAXIMUM_TAG_LENGTH_EXCEEDED("study.maximumTagLengthExceeded"),
    NETWORK_EXPORT_FAILED("study.networkExportFailed"),
    TOO_MANY_NAD_CONFIGS("study.tooManyNadConfigs"),
    TOO_MANY_MAP_CARDS("study.tooManyMapCards");

    private final String value;

    StudyBusinessErrorCode(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
