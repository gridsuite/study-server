/**
 * Copyright (c) 2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.service.client;

import com.google.common.base.CaseFormat;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import org.gridsuite.study.server.RemoteServicesProperties;

/**
 * List of all backend services
 *
 * @see RemoteServicesProperties#getServices()
 */
public enum RemoteServiceName {
    ACTIONS_SERVER,
    BALANCES_ADJUSTMENT_SERVER,
    CASE_IMPORT_SERVER,
    CASE_SERVER,
    CASE_VALIDATION_SERVER,
    CGMES_BOUNDARY_SERVER,
    CGMES_GL_SERVER,
    CONFIG_NOTIFICATION_SERVER,
    CONFIG_SERVER,
    DIRECTORY_NOTIFICATION_SERVER,
    DIRECTORY_SERVER,
    DYNAMIC_MAPPING_SERVER,
    DYNAMIC_SECURITY_ANALYSIS_SERVER,
    DYNAMIC_SIMULATION_SERVER,
    EXPLORE_SERVER,
    FILTER_SERVER,
    GATEWAY,
    GEO_DATA_SERVER,
    LOADFLOW_SERVER,
    MERGE_NOTIFICATION_SERVER,
    MERGE_ORCHESTRATOR_SERVER,
    NETWORK_CONVERSION_SERVER,
    NETWORK_MAP_SERVER,
    NETWORK_MODIFICATION_SERVER,
    NETWORK_STORE_SERVER,
    ODRE_SERVER,
    REPORT_SERVER,
    SECURITY_ANALYSIS_SERVER,
    SENSITIVITY_ANALYSIS_SERVER,
    SHORTCIRCUIT_SERVER,
    SINGLE_LINE_DIAGRAM_SERVER,
    STATE_ESTIMATION_ORCHESTRATOR_SERVER,
    STATE_ESTIMATION_SERVER,
    STUDY_CONFIG_SERVER,
    STUDY_NOTIFICATION_SERVER,
    STUDY_SERVER,
    TIMESERIES_SERVER,
    USER_ADMIN_SERVER,
    USER_IDENTITY_SERVER,
    VOLTAGE_INIT_SERVER;

    /**
     * Service name in the format found in {@link RemoteServicesProperties.Service#getName() Service.name}
     */
    @Accessors(fluent = true)
    @Getter(lazy = true)
    private final String serviceName = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, this.name());

    public static RemoteServiceName valueOfServiceName(@NonNull final String serviceName) {
        return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, serviceName));
    }
}
