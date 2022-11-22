/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server;

/**
 * @author Abdelsalem Hedhili <abdelsalem.hedhili at rte-france.com>
 */

public final class StudyConstants {

    private StudyConstants() {
    }

    public static final String CASE_API_VERSION = "v1";
    public static final String SINGLE_LINE_DIAGRAM_API_VERSION = "v1";
    public static final String NETWORK_CONVERSION_API_VERSION = "v1";
    public static final String GEO_DATA_API_VERSION = "v1";
    public static final String NETWORK_STORE_API_VERSION = "v1";
    public static final String NETWORK_MODIFICATION_API_VERSION = "v1";
    public static final String LOADFLOW_API_VERSION = "v1";
    public static final String SECURITY_ANALYSIS_API_VERSION = "v1";
    public static final String SENSITIVITY_ANALYSIS_API_VERSION = "v1";
    public static final String ACTIONS_API_VERSION = "v1";
    public static final String NETWORK_MAP_API_VERSION = "v1";
    public static final String REPORT_API_VERSION = "v1";
    public static final String SHORT_CIRCUIT_API_VERSION = "v1";

    public static final String NETWORK_UUID = "networkUuid";
    public static final String CASE_UUID = "caseUuid";

    public static final String DELIMITER = "/";
    public static final String QUERY_PARAM_VARIANT_ID = "variantId";
    public static final String GROUP_UUID = "groupUuid";
    public static final String REPORT_UUID = "reportUuid";
    public static final String REPORTER_ID = "reporterId";
    public static final String QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND = "errorOnGroupNotFound";
    public static final String QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND = "errorOnReportNotFound";
    public static final String QUERY_PARAM_REPORT_DEFAULT_NAME = "defaultName";
}
