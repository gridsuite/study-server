/**
  Copyright (c) 2023, RTE (http://www.rte-france.com)
  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.gridsuite.study.server.dto;

import com.powsybl.commons.parameters.ParameterType;
import lombok.*;

import java.util.List;

/**
 * @author David Braquart <david.braquart@rte-france.com>
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class ParameterInfos {

    private String provider;

    private String name;

    private String value;

    private ParameterType type;

    private String description;

    private String defaultValue;

    private List<String> possibleValues;
}
