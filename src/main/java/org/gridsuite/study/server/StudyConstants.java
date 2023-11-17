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
    public static final String DYNAMIC_SIMULATION_API_VERSION = "v1";
    public static final String SENSITIVITY_ANALYSIS_API_VERSION = "v1";
    public static final String ACTIONS_API_VERSION = "v1";
    public static final String NETWORK_MAP_API_VERSION = "v1";
    public static final String REPORT_API_VERSION = "v1";
    public static final String SHORT_CIRCUIT_API_VERSION = "v1";
    public static final String VOLTAGE_INIT_API_VERSION = "v1";
    public static final String TIME_SERIES_API_VERSION = "v1";
    public static final String DYNAMIC_MAPPING_API_VERSION = ""; // mapping server is now without version, must be v1 in the next time
    public static final String FILTER_API_VERSION = "v1";

    public static final String NETWORK_UUID = "networkUuid";
    public static final String CASE_UUID = "caseUuid";

    public static final String DELIMITER = "/";
    public static final String QUERY_PARAM_VARIANT_ID = "variantId";
    public static final String QUERY_PARAM_EQUIPMENT_TYPE = "equipmentType";
    public static final String QUERY_PARAM_ELEMENT_TYPE = "elementType";
    public static final String QUERY_PARAM_INFO_TYPE = "infoType";
    public static final String QUERY_PARAM_SUBSTATIONS_IDS = "substationsIds";
    public static final String QUERY_PARAM_SUBSTATION_ID = "substationId";
    public static final String QUERY_PARAM_DC_POWERFACTOR = "dcPowerFactor";

    public static final String GROUP_UUID = "groupUuid";
    public static final String REPORT_UUID = "reportUuid";
    public static final String UUIDS = "uuids";
    public static final String QUERY_PARAM_ERROR_ON_GROUP_NOT_FOUND = "errorOnGroupNotFound";
    public static final String QUERY_PARAM_ERROR_ON_REPORT_NOT_FOUND = "errorOnReportNotFound";
    public static final String QUERY_PARAM_REPORT_DEFAULT_NAME = "defaultName";
    public static final String QUERY_PARAM_REPORT_SEVERITY_LEVEL = "severityLevels";
    public static final String QUERY_PARAM_REPORT_WITH_ELEMENTS = "withElements";
    public static final String QUERY_PARAM_REPORT_TASKKEY_FILTER = "taskKeyFilter";
    public static final String QUERY_PARAM_RECEIVER = "receiver";
    public static final String HEADER_RECEIVER = "receiver";
    public static final String HEADER_BUS_ID = "busId";
    public static final String HEADER_IMPORT_PARAMETERS = "importParameters";
    public static final String HEADER_MESSAGE = "message";
    public static final String HEADER_USER_ID = "userId";
    public static final String QUERY_PARAM_ONLY_STASHED = "onlyStashed";
    public static final String QUERY_PARAM_STASHED = "stashed";

    public enum SldDisplayMode {
        FEEDER_POSITION,
        STATE_VARIABLE;
    }
}
