/**
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.powsybl.commons.parameters.ParameterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@Getter
@AllArgsConstructor
@Builder
public class ParameterInfos {

    private final String provider;

    private final String name;

    private final String value;

    private final ParameterType type;

    private final String description;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<String> possibleValues;
}




