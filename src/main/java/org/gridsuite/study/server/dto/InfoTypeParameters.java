/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class InfoTypeParameters {
    public static final String QUERY_PARAM_DC_POWERFACTOR = "dcPowerFactor";
    public static final String QUERY_PARAM_LOAD_OPERATIONAL_LIMIT_GROUPS = "loadOperationalLimitGroups";
    public static final String QUERY_PARAM_LOAD_REGULATING_TERMINALS = "loadRegulatingTerminals";
    public static final String QUERY_PARAM_LOAD_NETWORK_COMPONENTS = "loadNetworkComponents";

    private String infoType;
    private Map<String, String> optionalParameters;

    public InfoTypeParameters(String infoType, Map<String, String> optionalParameters) {
        this.infoType = infoType;
        this.optionalParameters = optionalParameters == null ? new HashMap<>() : optionalParameters;
    }
}
